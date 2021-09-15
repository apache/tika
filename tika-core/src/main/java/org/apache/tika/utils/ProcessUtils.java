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
package org.apache.tika.utils;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProcessUtils {


    private static final ConcurrentHashMap<String, Process> PROCESS_MAP = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PROCESS_MAP.forEachValue(1, Process::destroyForcibly);
        }));
    }

    private static String register(Process p) {
        String id = UUID.randomUUID().toString();
        PROCESS_MAP.put(id, p);
        return id;
    }

    private static Process release(String id) {
        return PROCESS_MAP.remove(id);
    }

    /**
     * This should correctly put double-quotes around an argument if
     * ProcessBuilder doesn't seem to work (as it doesn't
     * on paths with spaces on Windows)
     *
     * @param arg
     * @return
     */
    public static String escapeCommandLine(String arg) {
        if (arg == null) {
            return arg;
        }
        //need to test for " " on windows, can't just add double quotes
        //across platforms.
        if (arg.contains(" ") && SystemUtils.IS_OS_WINDOWS &&
                (!arg.startsWith("\"") && !arg.endsWith("\""))) {
            arg = "\"" + arg + "\"";
        }
        return arg;
    }

    public static String unescapeCommandLine(String arg) {
        if (arg.contains(" ") && SystemUtils.IS_OS_WINDOWS &&
                (arg.startsWith("\"") && arg.endsWith("\""))) {
            arg = arg.substring(1, arg.length() - 1);
        }
        return arg;
    }

    /**
     * This writes stdout and stderr to the FileProcessResult.
     *
     * @param pb
     * @param timeoutMillis
     * @param maxStdoutBuffer
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb,
                                            long timeoutMillis,
                                            int maxStdoutBuffer, int maxStdErrBuffer)
            throws IOException {
        Process p = null;
        String id = null;
        try {
            p = pb.start();
            id = register(p);
            long elapsed = -1;
            long start = System.currentTimeMillis();
            StreamGobbler outGobbler = new StreamGobbler(p.getInputStream(), maxStdoutBuffer);
            StreamGobbler errGobbler = new StreamGobbler(p.getErrorStream(), maxStdErrBuffer);

            Thread outThread = new Thread(outGobbler);
            outThread.start();

            Thread errThread = new Thread(errGobbler);
            errThread.start();
            int exitValue = -1;
            boolean complete = false;
            try {
                complete = p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                elapsed = System.currentTimeMillis() - start;
                if (complete) {
                    exitValue = p.exitValue();
                    outThread.join(1000);
                    errThread.join(1000);
                } else {
                    p.destroyForcibly();
                    outThread.join(1000);
                    errThread.join(1000);
                }
            } catch (InterruptedException e) {
                exitValue = -1000;
            }
            FileProcessResult result = new FileProcessResult();
            result.processTimeMillis = elapsed;
            result.stderrLength = errGobbler.getStreamLength();
            result.stdoutLength = outGobbler.getStreamLength();
            result.isTimeout = ! complete;
            result.exitValue = exitValue;
            result.stdout = StringUtils.joinWith("\n", outGobbler.getLines());
            result.stderr = StringUtils.joinWith("\n", errGobbler.getLines());
            result.stdoutTruncated = outGobbler.getIsTruncated();
            result.stderrTruncated = errGobbler.getIsTruncated();
            return result;
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
            if (id != null) {
                release(id);
            }
        }
    }

    /**
     * This redirects stdout to stdoutRedirect.
     *
     * @param pb
     * @param timeoutMillis
     * @param stdoutRedirect
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb,
                                            long timeoutMillis,
                                            Path stdoutRedirect, int maxStdErrBuffer) throws IOException {

        if (!Files.isDirectory(stdoutRedirect.getParent())) {
            Files.createDirectories(stdoutRedirect.getParent());
        }

        pb.redirectOutput(stdoutRedirect.toFile());
        Process p = null;
        String id = null;
        try {
            p = pb.start();
            id = register(p);
            long elapsed = -1;
            long start = System.currentTimeMillis();
            StreamGobbler errGobbler = new StreamGobbler(p.getErrorStream(), maxStdErrBuffer);

            Thread errThread = new Thread(errGobbler);
            errThread.start();
            int exitValue = -1;
            boolean complete = false;
            try {
                complete = p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                elapsed = System.currentTimeMillis() - start;
                if (complete) {
                    exitValue = p.exitValue();
                    errThread.join(1000);
                } else {
                    p.destroyForcibly();
                    errThread.join(1000);
                }
            } catch (InterruptedException e) {
                exitValue = -1000;
            }
            FileProcessResult result = new FileProcessResult();
            result.processTimeMillis = elapsed;
            result.stderrLength = errGobbler.getStreamLength();
            result.stdoutLength = Files.size(stdoutRedirect);
            result.isTimeout = !complete;
            result.exitValue = exitValue;
            result.stdout = "";
            result.stderr = StringUtils.joinWith("\n", errGobbler.getLines());
            result.stdoutTruncated = false;
            result.stderrTruncated = errGobbler.getIsTruncated();
            return result;
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
            release(id);
        }

    }

}
