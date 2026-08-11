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
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.parser.ParseContext;

public class ProcessUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessUtils.class);

    // How often a bounded subprocess wait checkpoints the task's ParseTimeout -- must be
    // finer-grained than progressTimeoutMillis or a long wait looks like a false "hung" kill.
    public static final long HEARTBEAT_INTERVAL_MILLIS = 1000;

    // Timeout for checkCommand's binary-existence probe (e.g. "myapp --version") -- kept
    // short since an unresponsive binary isn't usable and a full minute would delay
    // every task that happens to probe first.
    public static final long DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS = 5000;

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
     * <p>
     * Equivalent to {@link #execute(ProcessBuilder, ParseContext, long, int, int)} with a
     * null context: {@code requestedTimeoutMillis} is granted unclipped, no checkpointing.
     *
     * @param pb
     * @param requestedTimeoutMillis
     * @param maxStdoutBuffer
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb,
                                            long requestedTimeoutMillis,
                                            int maxStdoutBuffer, int maxStdErrBuffer)
            throws IOException {
        return execute(pb, null, requestedTimeoutMillis, maxStdoutBuffer, maxStdErrBuffer);
    }

    /**
     * Same as {@link #execute(ProcessBuilder, long, int, int)}, but bounds the wait to
     * {@code min(requestedTimeoutMillis, ParseTimeout.remainingMillis())} (see
     * {@link ParseTimeout#budgetFor(long)}) so no single call can outlast the task's
     * total timeout regardless of its own configuration. The granted budget and original
     * request are both recorded on the result (see
     * {@link FileProcessResult#getRequestedTimeoutMillis()},
     * {@link FileProcessResult#isClippedByRemaining()}).
     * <p>
     * While waiting, checkpoints the {@link ParseTimeout} in {@code context} (if any)
     * every {@value #HEARTBEAT_INTERVAL_MILLIS} ms, so a bounded external call can run
     * longer than the progress (stall-detection) timeout without looking like a hang --
     * the wait itself is progress. A null {@code context} behaves like the
     * four-argument overload.
     *
     * @param pb
     * @param context                may be null
     * @param requestedTimeoutMillis the timeout the caller's own configuration asks for
     * @param maxStdoutBuffer
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb, ParseContext context,
                                            long requestedTimeoutMillis,
                                            int maxStdoutBuffer, int maxStdErrBuffer)
            throws IOException {
        long grantedTimeoutMillis = ParseTimeout.getOrCreate(context).budgetFor(requestedTimeoutMillis);
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
                complete = waitForWithHeartbeat(p, context, grantedTimeoutMillis);
                elapsed = System.currentTimeMillis() - start;
                if (complete) {
                    exitValue = p.exitValue();
                    outThread.join(1000);
                    errThread.join(1000);
                } else {
                    p.destroyForcibly();
                    outThread.join(1000);
                    errThread.join(1000);
                    boolean completed = p.waitFor(500, TimeUnit.MILLISECONDS);
                    if (completed) {
                        try {
                            exitValue = p.exitValue();
                        } catch (IllegalThreadStateException e) {
                            //not finished!
                        }
                    }
                }
            } catch (InterruptedException e) {
                exitValue = -1000;
            } finally {
                outThread.interrupt();
                errThread.interrupt();
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
            result.requestedTimeoutMillis = requestedTimeoutMillis;
            result.grantedTimeoutMillis = grantedTimeoutMillis;
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
     * This redirects stdout to stdoutRedirect path.
     * <p>
     * Equivalent to {@link #execute(ProcessBuilder, ParseContext, long, Path, int)} with
     * a null context, i.e. {@code requestedTimeoutMillis} is granted unclipped and the
     * wait does not checkpoint any task's progress timeout.
     *
     * @param pb
     * @param requestedTimeoutMillis
     * @param stdoutRedirect
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb,
                                            long requestedTimeoutMillis,
                                            Path stdoutRedirect, int maxStdErrBuffer) throws IOException {
        return execute(pb, null, requestedTimeoutMillis, stdoutRedirect, maxStdErrBuffer);
    }

    /**
     * Same as {@link #execute(ProcessBuilder, long, Path, int)}, but bounds the wait to
     * {@code min(requestedTimeoutMillis, ParseTimeout.remainingMillis())} and checkpoints
     * while waiting -- see {@link #execute(ProcessBuilder, ParseContext, long, int, int)}.
     *
     * @param pb
     * @param context                may be null
     * @param requestedTimeoutMillis the timeout the caller's own configuration asks for
     * @param stdoutRedirect
     * @param maxStdErrBuffer
     * @return
     * @throws IOException
     */
    public static FileProcessResult execute(ProcessBuilder pb, ParseContext context,
                                            long requestedTimeoutMillis,
                                            Path stdoutRedirect, int maxStdErrBuffer) throws IOException {

        if (!Files.isDirectory(stdoutRedirect.getParent())) {
            Files.createDirectories(stdoutRedirect.getParent());
        }

        long grantedTimeoutMillis = ParseTimeout.getOrCreate(context).budgetFor(requestedTimeoutMillis);
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
                complete = waitForWithHeartbeat(p, context, grantedTimeoutMillis);
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
            result.requestedTimeoutMillis = requestedTimeoutMillis;
            result.grantedTimeoutMillis = grantedTimeoutMillis;
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
     * Checks to see if the command can be run. Typically used with
     * something like "myapp --version" to check to see if "myapp"
     * is installed and on the path.
     * <p>
     * Equivalent to {@link #checkCommandWithTimeout(String[], long, int...)} with
     * {@link #DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS}.
     *
     * @param checkCmd   The check command to run
     * @param errorValue What is considered an error value? Default is 127 (command not found).
     * @return true if the command ran successfully (exit code not in errorValue list)
     */
    public static boolean checkCommand(String checkCmd, int... errorValue) {
        return checkCommandWithTimeout(new String[]{checkCmd}, DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS, errorValue);
    }

    /**
     * Checks to see if the command can be run. Typically used with
     * something like {@code new String[]{"myapp", "--version"}} to check to see if "myapp"
     * is installed and on the path.
     * <p>
     * Equivalent to {@link #checkCommandWithTimeout(String[], long, int...)} with
     * {@link #DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS}.
     *
     * @param checkCmd   The check command to run
     * @param errorValue What is considered an error value? Default is 127 (command not found).
     * @return true if the command ran successfully (exit code not in errorValue list)
     */
    public static boolean checkCommand(String[] checkCmd, int... errorValue) {
        return checkCommandWithTimeout(checkCmd, DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS, errorValue);
    }

    /**
     * Same as {@link #checkCommand(String[], int...)}, but with a caller-specified timeout
     * instead of the {@value #DEFAULT_CHECK_COMMAND_TIMEOUT_MILLIS}ms default.
     * <p>
     * Deliberately a distinct method name, not a same-named overload: {@code checkCommand(cmd, 500)}
     * would silently resolve to {@code checkCommand(String[], int...)} with
     * {@code errorValue={500}} at the default timeout -- Java prefers the varargs-only
     * overload over widening {@code int} to this method's {@code long} parameter, with no
     * compile error to catch the mistake.
     *
     * @param timeoutMillis how long to wait for the command to exit
     * @param errorValue    What is considered an error value? Default is 127 (command not found).
     * @return true if the command ran successfully (exit code not in errorValue list)
     */
    public static boolean checkCommandWithTimeout(String[] checkCmd, long timeoutMillis, int... errorValue) {
        if (errorValue.length == 0) {
            errorValue = new int[]{127};
        }

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(checkCmd);
            StreamGobbler outGobbler = new StreamGobbler(process.getInputStream(), 0);
            StreamGobbler errGobbler = new StreamGobbler(process.getErrorStream(), 0);
            Thread outThread = new Thread(outGobbler);
            Thread errThread = new Thread(errGobbler);
            outThread.start();
            errThread.start();
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new TimeoutException();
            }
            outThread.join(1000);
            errThread.join(1000);
            int result = process.exitValue();
            LOG.debug("exit value for {}: {}", checkCmd[0], result);
            for (int err : errorValue) {
                if (result == err) {
                    return false;
                }
            }
            return true;
        } catch (IOException | InterruptedException | TimeoutException e) {
            LOG.debug("exception trying to run " + checkCmd[0], e);
            return false;
        } catch (SecurityException se) {
            throw se;
        } catch (Error err) {
            if (err.getMessage() != null && (err.getMessage().contains("posix_spawn") ||
                    err.getMessage().contains("UNIXProcess"))) {
                LOG.debug("(TIKA-1526): exception trying to run: " + checkCmd[0], err);
                return false;
            }
            throw err;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Waits for the process to exit, like {@link Process#waitFor(long, TimeUnit)}, but polls
     * in {@value #HEARTBEAT_INTERVAL_MILLIS} ms increments and checkpoints {@code context}'s
     * {@link ParseTimeout} after each increment that doesn't complete -- this is what lets a
     * bounded external call run longer than the progress timeout without tripping the stall
     * detector: the wait itself is progress.
     * <p>
     * Public so callers managing their own {@link Process} (not going through
     * {@link #execute(ProcessBuilder, ParseContext, long, int, int)}) can still checkpoint
     * while waiting.
     *
     * @param context       may be null, in which case no checkpoint is recorded
     * @param timeoutMillis total wait time in ms; zero or negative checks once without waiting
     * @return true if the process exited before the timeout elapsed
     */
    public static boolean waitForWithHeartbeat(Process p, ParseContext context, long timeoutMillis)
            throws InterruptedException {
        long now = System.currentTimeMillis();
        long deadline = (timeoutMillis >= Long.MAX_VALUE - now) ? Long.MAX_VALUE : now + timeoutMillis;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            long pollMillis = remaining <= 0 ? 0 : Math.min(remaining, HEARTBEAT_INTERVAL_MILLIS);
            if (p.waitFor(pollMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
            if (remaining <= 0) {
                return false;
            }
            ParseTimeout.checkpoint(context);
        }
    }

}
