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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.slf4j.Logger;

/**
 * Helpers for child pipes-server JVM stdio.
 * <p>
 * The managers default to {@link ProcessBuilder#inheritIO()} for the child's
 * stdout/stderr -- the production case is container deployments
 * (Docker/Kubernetes) where stdio is the canonical log stream and gets
 * picked up by the container runtime / log aggregator. The pipes server's
 * default log4j2 config writes to {@code SYSTEM_ERR}; inheriting routes
 * those records to the operator's existing observability stack with no
 * extra wiring.
 * <p>
 * A previous version of this code defaulted to DISCARD to work around a
 * Windows-only CI hang where surefire's pipe reader waited on a child that
 * had inherited (and was still holding) the parent JVM's stderr handle.
 * That hang is now mitigated structurally by the parent-PID watch in
 * {@code PipesServer.watchParentProcess()} -- when the parent dies, the
 * child notices via {@code ProcessHandle.onExit()} and exits within
 * milliseconds, releasing the inherited handle and letting upstream
 * readers see EOF.
 * <p>
 * To suppress child stdio (e.g. embedded use cases that don't want
 * pipes-server log records interleaved with their own stream, or rare
 * test environments where the parent-watch isn't enough), set the system
 * property {@value #STDIO_MODE_PROPERTY} to {@value #STDIO_MODE_DISCARD}
 * on the parent JVM.
 * <p>
 * Native JVM crashes ({@code hs_err_pid<N>.log}) are always redirected into
 * the manager's per-server tmpDir via {@code -XX:ErrorFile=}, and
 * {@link #surfaceCrashDiagnostics(Logger, String, Path)} reads any found
 * crash logs into the parent SLF4J logger before cleanup.
 */
final class ServerProcessIO {

    /** System property selecting child stdio handling.
     *  See class javadoc. */
    static final String STDIO_MODE_PROPERTY = "tika.pipes.server.stdio";

    /** Property value to suppress child stdio. Any other value (or unset)
     *  inherits. */
    static final String STDIO_MODE_DISCARD = "discard";

    private ServerProcessIO() {
    }

    /**
     * Returns {@code true} (the default) unless the operator has set
     * {@value #STDIO_MODE_PROPERTY}={@value #STDIO_MODE_DISCARD} to silence
     * the child's stdio.
     */
    static boolean inheritStdio() {
        return !STDIO_MODE_DISCARD.equalsIgnoreCase(
                System.getProperty(STDIO_MODE_PROPERTY));
    }

    /**
     * Emits any {@code hs_err_pid<N>.log} JVM crash logs found in
     * {@code tmpDir} via {@code log.error} so they show up in the parent's
     * log output. Call this on every abnormal-exit path before tmpDir gets
     * deleted, otherwise the diagnostics disappear with the temp dir.
     */
    static void surfaceCrashDiagnostics(Logger log, String contextLabel, Path tmpDir) {
        if (tmpDir == null || !Files.isDirectory(tmpDir)) {
            return;
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
    }

    private static boolean isJvmCrashLog(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("hs_err_pid") && name.endsWith(".log");
    }
}
