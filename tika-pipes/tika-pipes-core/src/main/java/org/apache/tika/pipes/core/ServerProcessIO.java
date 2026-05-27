/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

/**
 * Helpers for routing child pipes-server JVM stdout/stderr to per-server log
 * files in the manager's temp dir, and for surfacing those files (and any
 * native JVM crash logs) via the parent's SLF4J logger when the child exits
 * abnormally.
 * <p>
 * Background: previously the managers used {@code pb.inheritIO()} /
 * {@code Redirect.INHERIT}, which duplicated the parent JVM's stdio handles
 * into the child. On Windows that leaks the parent JVM's stderr handle past
 * the parent JVM's own lifetime -- a surefire pipe reader thread on the
 * controller side then blocks forever waiting for EOF, hanging CI.
 */
final class ServerProcessIO {

    /** System property opt-in: when set, child log files and any hs_err
     *  crash logs are copied here before tmpDir cleanup. */
    static final String LOG_DIR_PROPERTY = "tika.pipes.server.logDir";

    static final String STDOUT_LOG = "server-stdout.log";
    static final String STDERR_LOG = "server-stderr.log";

    private static final int TAIL_BYTES = 64 * 1024;

    private ServerProcessIO() {
    }

    static File stdoutLog(Path tmpDir) {
        return tmpDir.resolve(STDOUT_LOG).toFile();
    }

    static File stderrLog(Path tmpDir) {
        return tmpDir.resolve(STDERR_LOG).toFile();
    }

    /**
     * Deletes the manager's tmpDir, retrying past Windows file-handle
     * lingering. After {@code process.destroyForcibly() / waitFor()} returns,
     * Windows can still hold open handles for the redirected stdout/stderr
     * files for up to a few hundred ms. A naive one-shot delete fails with
     * {@code FileSystemException("...used by another process")}; left
     * unhandled, that strands the files and breaks higher-level cleanup
     * (e.g. parent JUnit {@code @TempDir} teardown).
     */
    static void deleteDirWithRetry(Logger log, String contextLabel, Path dir) {
        if (dir == null) {
            return;
        }
        IOException last = null;
        int attempts = 6;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                FileUtils.deleteDirectory(dir.toFile());
                return;
            } catch (IOException e) {
                last = e;
                if (attempt == attempts) {
                    break;
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("{}: couldn't delete tmp dir {} after {} attempts: {}",
                contextLabel, dir, attempts,
                last == null ? "(no error)" : last.toString());
    }

    /**
     * Emits the child's stderr tail and any {@code hs_err_pid<N>.log} JVM
     * crash logs via {@code log.error} so they show up in the parent's log
     * output. Call this on every abnormal-exit path before {@code tmpDir}
     * gets deleted, otherwise the diagnostics disappear with the temp dir.
     * <p>
     * If {@code tika.pipes.server.logDir} is set, the same files are also
     * copied to that directory for post-mortem inspection.
     */
    static void surfaceCrashDiagnostics(Logger log, String contextLabel, Path tmpDir) {
        if (tmpDir == null || !Files.isDirectory(tmpDir)) {
            return;
        }
        Path stderr = tmpDir.resolve(STDERR_LOG);
        if (Files.isRegularFile(stderr)) {
            String tail = readTail(stderr);
            if (!tail.isEmpty()) {
                log.error("{}: child stderr tail:\n{}", contextLabel, tail);
            }
        }
        try (Stream<Path> entries = Files.list(tmpDir)) {
            entries.filter(ServerProcessIO::isJvmCrashLog).forEach(p -> {
                try {
                    log.error("{}: JVM crash log {}:\n{}", contextLabel,
                            p.getFileName(),
                            Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.warn("{}: failed to read JVM crash log {}: {}",
                            contextLabel, p.getFileName(), e.toString());
                }
            });
        } catch (IOException e) {
            log.warn("{}: failed to list tmpDir for hs_err logs: {}",
                    contextLabel, e.toString());
        }

        String persistDir = System.getProperty(LOG_DIR_PROPERTY);
        if (persistDir != null && !persistDir.isBlank()) {
            persistCrashFiles(log, contextLabel, tmpDir, Paths.get(persistDir));
        }
    }

    private static boolean isJvmCrashLog(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("hs_err_pid") && name.endsWith(".log");
    }

    private static void persistCrashFiles(Logger log, String contextLabel,
                                          Path tmpDir, Path dest) {
        try {
            Files.createDirectories(dest);
        } catch (IOException e) {
            log.warn("{}: failed to create persist dir {}: {}",
                    contextLabel, dest, e.toString());
            return;
        }
        String stamp = Long.toString(System.currentTimeMillis());
        try (Stream<Path> entries = Files.list(tmpDir)) {
            entries.filter(p -> {
                String name = p.getFileName().toString();
                return name.equals(STDOUT_LOG) || name.equals(STDERR_LOG)
                        || isJvmCrashLog(p);
            }).forEach(p -> {
                Path target = dest.resolve(stamp + "-" + p.getFileName());
                try {
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("{}: persisted {} to {}", contextLabel,
                            p.getFileName(), target);
                } catch (IOException e) {
                    log.warn("{}: failed to copy {} to {}: {}", contextLabel,
                            p.getFileName(), target, e.toString());
                }
            });
        } catch (IOException e) {
            log.warn("{}: failed to enumerate tmpDir for persistence: {}",
                    contextLabel, e.toString());
        }
    }

    private static String readTail(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long len = raf.length();
            long start = Math.max(0, len - TAIL_BYTES);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            if (start > 0) {
                s = "...[truncated, showing last " + TAIL_BYTES + " bytes]...\n" + s;
            }
            return s;
        } catch (IOException e) {
            return "";
        }
    }
}
