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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaExtras;
import org.apache.tika.pipes.core.server.PipesServer;
import org.apache.tika.utils.ProcessUtils;

/**
 * Manages a dedicated PipesServer process for a single PipesClient.
 * <p>
 * This is the default mode where each PipesClient has its own server process,
 * providing isolation at the cost of memory overhead.
 * <p>
 * Connection model: The client creates a ServerSocket and the server connects TO it.
 * This is the reverse of typical client-server patterns but allows the client to
 * control the port assignment.
 */
public class PerClientServerManager implements ServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(PerClientServerManager.class);
    private static final long WAIT_ON_DESTROY_MS = 10000;
    public static final int SOCKET_CONNECT_TIMEOUT_MS = 60000;
    /** Cores reserved for the parent JVM when auto-sizing forked JVMs'
     *  -XX:ActiveProcessorCount. The parent has client-side serialization,
     *  response deserialization, and heartbeat bookkeeping; if it's CPU-starved
     *  small operations like socket flush show pathological tail latency. */
    private static final int PARENT_RESERVED_CORES = 2;
    /** Don't auto-cap below this many CPUs per fork. At cap=1 the fork's only
     *  CPU is fully consumed by parsing, so its socket-reader thread can't run
     *  and the parent's writes block on receiver-side back-pressure -- worse
     *  than no cap at all. This guard matters for small k8s pods where the
     *  formula could otherwise produce slice=1. */
    private static final int MIN_AUTO_CAP_SLICE = 2;

    /** Share of host/container memory the forks may collectively claim; the remainder is
     *  left for the parent JVM, the OS, and page cache for spooled input. */
    private static final int FORK_HEAP_BUDGET_PERCENT = 75;


    private static boolean userSetHeap(List<String> args) {
        return args.stream().anyMatch(a -> a.startsWith("-Xmx")
                || a.startsWith("-XX:MaxRAMPercentage")
                || a.startsWith("-XX:MaxRAMFraction"));
    }

    /** A lone fork gets less than the whole budget: the parent claims the JVM default (~25% of
     *  the container) on top, and metaspace, thread stacks and page cache come out of what is
     *  left. Exceeding the container trades a per-document OOM for an OOM-kill of both JVMs. */
    private static final int SINGLE_FORK_HEAP_PERCENT = 60;

    private static int forkHeapPercentage(int numClients) {
        if (numClients == 1) {
            return SINGLE_FORK_HEAP_PERCENT;
        }
        return Math.max(1, FORK_HEAP_BUDGET_PERCENT / numClients);
    }

    /**
     * Explicit heap settings suppress auto-division, so nothing else checks that
     * numClients forks at the user's size can actually coexist -- the mistake only
     * surfaces later as fork OOMs or the host OOM-killer. Returns a warning when
     * the combined explicit ceiling exceeds the fork budget, else null.
     * Package-private for tests.
     */
    static String heapOvercommitWarning(List<String> forkedJvmArgs, int numClients,
                                        long totalMemBytes) {
        long xmxBytes = -1;
        double maxRamPct = -1;
        // last occurrence wins, same as the JVM
        for (String arg : forkedJvmArgs) {
            if (arg.startsWith("-Xmx")) {
                xmxBytes = parseJvmMemArg(arg.substring("-Xmx".length()));
            } else if (arg.startsWith("-XX:MaxRAMPercentage=")) {
                try {
                    maxRamPct = Double.parseDouble(
                            arg.substring("-XX:MaxRAMPercentage=".length()));
                } catch (NumberFormatException e) {
                    maxRamPct = -1;
                }
            }
        }
        // -Xmx beats MaxRAMPercentage in the JVM, so only judge the percentage alone
        if (xmxBytes <= 0 && maxRamPct > 0 && numClients * maxRamPct > FORK_HEAP_BUDGET_PERCENT) {
            return String.format(java.util.Locale.ROOT,
                    "numClients=%d x -XX:MaxRAMPercentage=%s commits %.0f%% of memory; " +
                    "the parent JVM and OS need the remainder (fork budget: %d%%). " +
                    "Lower numClients or the percentage.",
                    numClients, maxRamPct, numClients * maxRamPct, FORK_HEAP_BUDGET_PERCENT);
        }
        if (xmxBytes > 0 && totalMemBytes > 0
                && numClients * (double) xmxBytes > totalMemBytes * FORK_HEAP_BUDGET_PERCENT / 100.0) {
            return String.format(java.util.Locale.ROOT,
                    "numClients=%d x -Xmx (%,dMB each) commits %,dMB of %,dMB total memory, " +
                    "over the %d%% fork budget; the parent JVM and OS need the remainder. " +
                    "Lower numClients or -Xmx.",
                    numClients, xmxBytes / MB, numClients * xmxBytes / MB, totalMemBytes / MB,
                    FORK_HEAP_BUDGET_PERCENT);
        }
        return null;
    }

    private static final long MB = 1024 * 1024;

    // "-Xmx" style value: digits with optional k/m/g/t suffix; -1 if unparseable
    static long parseJvmMemArg(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        long multiplier = switch (Character.toLowerCase(value.charAt(value.length() - 1))) {
            case 'k' -> 1024L;
            case 'm' -> 1024L * 1024;
            case 'g' -> 1024L * 1024 * 1024;
            case 't' -> 1024L * 1024 * 1024 * 1024;
            default -> 1;
        };
        String digits = multiplier == 1 ? value : value.substring(0, value.length() - 1);
        try {
            long n = Long.parseLong(digits);
            return n <= 0 ? -1 : Math.multiplyExact(n, multiplier);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    // Container-aware on JDK 17 (honors cgroup memory limits); -1 if unavailable.
    // Read via JMX attribute name: the typed accessor lives on a com.sun
    // interface that forbiddenapis bans as non-portable.
    private static long totalMemorySize() {
        try {
            Object value = java.lang.management.ManagementFactory.getPlatformMBeanServer()
                    .getAttribute(new javax.management.ObjectName("java.lang:type=OperatingSystem"),
                            "TotalMemorySize");
            return value instanceof Number n ? n.longValue() : -1;
        } catch (Exception e) {
            return -1;
        }
    }


    private final PipesConfig pipesConfig;
    private final Path tikaConfigPath;
    private final int clientId;

    private volatile Process process;
    private volatile ServerSocket serverSocket;
    private volatile Path tmpDir;
    private volatile int port = -1;
    private long filesProcessed = 0;
    private volatile boolean pendingRestart = false;
    // Set once by shutdown()/close(); guards a request thread from starting a fresh
    // process after the manager has been torn down (which would leak the child).
    private volatile boolean closed = false;

    public PerClientServerManager(PipesConfig pipesConfig, Path tikaConfigPath, int clientId) {
        this.pipesConfig = pipesConfig;
        this.tikaConfigPath = tikaConfigPath;
        this.clientId = clientId;
        // Emit CPU-sizing diagnostics once per PipesParser (only on the first client).
        if (clientId == 0) {
            logCpuSizing();
        }
    }

    /**
     * Emits a one-shot summary of how the auto-cap will behave for this PipesParser,
     * plus warnings for clearly-pathological provisioning. Grep for "pipes-cpu-sizing"
     * in logs to see the decision the JVM made.
     */
    private void logCpuSizing() {
        int hostCores = Runtime.getRuntime().availableProcessors();
        int numClients = pipesConfig.getNumClients();
        boolean userSetCap = pipesConfig.getForkedJvmArgs().stream()
                .anyMatch(a -> a.startsWith("-XX:ActiveProcessorCount="));

        // Hostile environment: fewer than 2 cores means the parser thread, GC, JIT,
        // and protocol heartbeat all share one CPU. Pipes will run but tail latency
        // will be poor regardless of numClients.
        if (hostCores < 2) {
            LOG.warn("pipes-cpu-sizing: hostCores={} is below the practical minimum. " +
                    "Each fork JVM needs roughly 2 CPUs (1 for parsing, 1 for GC/JIT/" +
                    "protocol heartbeat); on a single-CPU host these contend with each " +
                    "other and performance will be poor.", hostCores);
        }

        // Over-provisioned: numClients packed too tightly given the host's cores.
        // Triggers earlier than the slice<MIN guard so the user is warned even
        // when they explicitly set -XX:ActiveProcessorCount themselves.
        if (numClients > 1 && numClients * MIN_AUTO_CAP_SLICE + PARENT_RESERVED_CORES > hostCores) {
            int recommendedMax = Math.max(1,
                    (hostCores - PARENT_RESERVED_CORES) / MIN_AUTO_CAP_SLICE);
            LOG.warn("pipes-cpu-sizing: numClients={} is over-provisioned for {}-core " +
                    "host. Recommended max for this host: numClients={}. Forks need at " +
                    "least {} CPUs each plus {} reserved for the parent JVM; otherwise " +
                    "GC/JIT/protocol threads contend with parser threads across forks.",
                    numClients, hostCores, recommendedMax,
                    MIN_AUTO_CAP_SLICE, PARENT_RESERVED_CORES);
        }

        // Always-on summary so ops can see what was decided. Grep for "pipes-cpu-sizing".
        String capDecision;
        if (userSetCap) {
            capDecision = "user-set in forkedJvmArgs";
        } else {
            int budget = Math.max(1, hostCores - PARENT_RESERVED_CORES);
            int slice = budget / numClients;
            capDecision = (slice >= MIN_AUTO_CAP_SLICE)
                    ? "slice=" + slice
                    : "skipped (slice<" + MIN_AUTO_CAP_SLICE + ")";
        }
        String heapDecision;
        if (userSetHeap(pipesConfig.getForkedJvmArgs())) {
            heapDecision = "user-set in forkedJvmArgs";
        } else {
            heapDecision = "MaxRAMPercentage=" + forkHeapPercentage(numClients);
        }
        LOG.info("pipes-cpu-sizing: hostCores={}, numClients={}, parentReserved={}, " +
                "autoCap={}, heap={}", hostCores, numClients, PARENT_RESERVED_CORES,
                capDecision, heapDecision);

        String overcommit = heapOvercommitWarning(pipesConfig.getForkedJvmArgs(),
                numClients, totalMemorySize());
        if (overcommit != null) {
            LOG.warn("pipes-cpu-sizing: {}", overcommit);
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Path getTempDirectory() {
        return tmpDir;
    }

    @Override
    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public void incrementFilesProcessed(long maxFilesPerProcess) {
        if (maxFilesPerProcess <= 0) {
            return;
        }
        filesProcessed++;
        if (filesProcessed >= maxFilesPerProcess) {
            LOG.info("clientId={}: reached max files limit ({}/{}), marking for restart",
                    clientId, filesProcessed, maxFilesPerProcess);
            pendingRestart = true;
        }
    }

    @Override
    public boolean needsRestart() {
        return pendingRestart;
    }

    @Override
    public void markServerForRestart() {
        LOG.info("clientId={}: marking server for restart", clientId);
        pendingRestart = true;
    }

    @Override
    public void connectionAbandoned() {
        LOG.info("clientId={}: connection abandoned, worker will be recycled on next use", clientId);
        pendingRestart = true;
    }

    @Override
    public int handleCrashAndGetExitCode() {
        pendingRestart = true;
        if (process != null) {
            try {
                process.waitFor(1, TimeUnit.SECONDS);
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    if (exitValue == 0) {
                        LOG.info("clientId={}: process exited cleanly", clientId);
                    } else {
                        LOG.warn("clientId={}: process exited with code {}", clientId, exitValue);
                    }
                    return exitValue;
                } else {
                    LOG.warn("clientId={}: process still running after crash", clientId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return -1;
    }

    @Override
    public synchronized void ensureRunning() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        if (closed) {
            throw new IllegalStateException("PerClientServerManager is closed");
        }
        // Check if server is running AND not marked for restart
        if (isRunning() && !pendingRestart) {
            return;
        }
        startServer();
    }

    @Override
    public Socket connect(int socketTimeoutMillis) throws IOException, ServerInitializationException {
        // Capture the socket up front: shutdown() may null the field concurrently, but this
        // request keeps using (and detects the close on) the instance it started with.
        ServerSocket ss = serverSocket;
        if (ss == null) {
            throw new IllegalStateException("Server not started. Call ensureRunning() first.");
        }

        // Accept incoming connection from the server process
        ss.setSoTimeout(1000); // 1 second timeout for each poll
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                Socket socket = ss.accept();
                socket.setSoTimeout(socketTimeoutMillis);
                socket.setTcpNoDelay(true);
                LOG.debug("clientId={}: accepted connection from server", clientId);
                return socket;
            } catch (SocketTimeoutException e) {
                // Check if the process died (or the manager was shut down) before connecting.
                Process p = process;
                if (p == null) {
                    throw new IOException("Server manager was shut down while connecting");
                }
                if (!p.isAlive()) {
                    int exitValue = p.exitValue();
                    LOG.error("clientId={}: Process exited with code {} before connecting to socket",
                            clientId, exitValue);
                    ServerProcessIO.surfaceCrashDiagnostics(LOG, "clientId=" + clientId, tmpDir);
                    // Always treat pre-connect death as retryable.
                    // The only non-retryable paths are:
                    // 1. pb.start() fails (can't launch process) - handled in startServer()
                    // 2. Server explicitly reports bad config via protocol - handled in waitForStartup()
                    // 3. Exhausted all retry attempts - handled in maybeInit()
                    pendingRestart = true;
                    throw new IOException(
                            "Server process died before connecting (exit code " + exitValue + ") - will retry");
                }
                // Check if we've exceeded the overall timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > SOCKET_CONNECT_TIMEOUT_MS) {
                    LOG.error("clientId={}: Timed out waiting for server to connect after {}ms", clientId, elapsed);
                    ServerProcessIO.surfaceCrashDiagnostics(LOG, "clientId=" + clientId, tmpDir);
                    throw new ServerInitializationException(
                            "Server did not connect within " + SOCKET_CONNECT_TIMEOUT_MS + "ms");
                }
                // Continue polling
            }
        }
    }

    private synchronized void startServer() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        if (closed) {
            throw new IllegalStateException("PerClientServerManager is closed");
        }
        // Clean up any previous server (restart) -- teardown, not shutdown, so we do not
        // mark the manager closed.
        if (process != null || serverSocket != null || tmpDir != null) {
            teardown();
        }

        // Create new server socket to get a free port
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
        port = serverSocket.getLocalPort();

        LOG.trace("clientId={}: starting server on port={}", clientId, port);

        tmpDir = pipesConfig.createTempDirectory("pipes-server-" + clientId + "-");
        ProcessBuilder pb = new ProcessBuilder(getCommandline(tmpDir));
        // Tell the child our PID so it can watch ProcessHandle.onExit() and
        // self-terminate promptly if we die. Without this, an orphan child
        // can only notice via socket-read timeout (default 60s) and can't
        // notice at all while it is mid-parse -- the leak that TIKA-4740
        // surfaced via @TempDir cleanup failures.
        pb.environment().put(PipesServer.PARENT_PID_ENV,
                Long.toString(ProcessHandle.current().pid()));
        // Default: inherit stdio so the pipes-server's log records show up
        // in the parent's stdio stream (the production case is Docker/K8s
        // where container stdio is picked up by log aggregators -- writing
        // to files in a container is anti-pattern). Set
        // -Dtika.pipes.server.stdio=discard on the parent to suppress.
        //
        // The Windows surefire hang that previously made inheritIO() risky
        // is mitigated by PipesServer.watchParentProcess(): when the parent
        // exits, the child detects it via ProcessHandle.onExit() within
        // milliseconds and System.exit()s, releasing its inherited stderr
        // handle so upstream pipe readers see EOF promptly.
        //
        // hs_err crash logs are pointed at tmpDir via -XX:ErrorFile in
        // getCommandline() and surfaced via SLF4J on abnormal exit.
        if (ServerProcessIO.inheritStdio()) {
            pb.inheritIO();
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        }

        try {
            process = pb.start();
        } catch (Exception e) {
            deleteDir(tmpDir);
            tmpDir = null;
            LOG.error("clientId={}: failed to start server", clientId, e);
            String msg = "Failed to start server process";
            if (e.getMessage() != null) {
                msg += ": " + e.getMessage();
            }
            throw new ServerInitializationException(msg, e);
        }

        // Server is started, but we don't wait for connection here.
        // The connection is established in connect() method.
        LOG.debug("clientId={}: server process started, waiting for connection in connect()", clientId);

        // Reset counters after successful start
        filesProcessed = 0;
        pendingRestart = false;
    }

    @Override
    public synchronized void shutdown() throws InterruptedException {
        closed = true;
        teardown();
    }

    /**
     * Tears down the current server process, socket, and temp dir without marking the manager
     * closed -- shared by the final {@link #shutdown()} and by {@link #startServer()} on restart.
     * Callers hold the monitor.
     */
    private void teardown() throws InterruptedException {
        LOG.debug("clientId={}: tearing down server", clientId);

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket", e);
            }
            serverSocket = null;
        }

        destroyProcess();

        if (tmpDir != null) {
            deleteDir(tmpDir);
            tmpDir = null;
        }

        port = -1;
    }

    private void destroyProcess() throws InterruptedException {
        if (process != null) {
            process.destroyForcibly();
            process.waitFor(WAIT_ON_DESTROY_MS, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                LOG.error("clientId={}: process still alive after {}ms", clientId, WAIT_ON_DESTROY_MS);
            }
            process = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during shutdown", e);
        }
    }

    /**
     * Returns the ServerSocket for accepting client connections.
     * Used by PipesClient to accept the server's incoming connection.
     *
     * @return the server socket
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Returns the server process.
     *
     * @return the process, or null if not started
     */
    public Process getProcess() {
        return process;
    }

    private void deleteDir(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(dir.toFile());
        } catch (IOException e) {
            LOG.warn("couldn't delete tmp dir {}", dir);
        }
    }

    // package-private and tmpDir passed in (rather than read from the field set by
    // startServer) so tests can exercise arg injection without forking a JVM
    String[] getCommandline(Path tmpDir) throws IOException {
        List<String> configArgs = new ArrayList<>(pipesConfig.getForkedJvmArgs());
        boolean hasClassPath = false;
        boolean hasHeadless = false;
        boolean hasExitOnOOM = false;
        boolean hasLog4j = false;
        boolean hasActiveProcessorCount = false;
        boolean hasErrorFile = false;
        String origGCString = null;
        String newGCLogString = null;

        for (String arg : configArgs) {
            if (arg.startsWith("-Djava.awt.headless")) {
                hasHeadless = true;
            }
            if (arg.equals("-cp") || arg.equals("--classpath")) {
                hasClassPath = true;
            }
            if (arg.equals("-XX:+ExitOnOutOfMemoryError") || arg.equals("-XX:+CrashOnOutOfMemoryError")) {
                hasExitOnOOM = true;
            }
            if (arg.startsWith("-Dlog4j.configuration") || arg.startsWith("-Dlog4j2.configuration")) {
                hasLog4j = true;
            }
            if (arg.startsWith("-XX:ActiveProcessorCount=")) {
                hasActiveProcessorCount = true;
            }
            if (arg.startsWith("-XX:ErrorFile=")) {
                hasErrorFile = true;
            }
            if (arg.startsWith("-Xloggc:")) {
                origGCString = arg;
                newGCLogString = arg.replace("${pipesClientId}", "id-" + clientId);
            }
        }

        // Direct native-crash dumps (hs_err_pid<N>.log) into tmpDir so
        // ServerProcessIO.surfaceCrashDiagnostics() can find and emit them on
        // abnormal exit. The child JVM inherits the parent's CWD (we do NOT
        // call pb.directory()), so without this the JVM would write hs_err
        // wherever the parent was launched -- typically lost.
        if (!hasErrorFile) {
            configArgs.add("-XX:ErrorFile=" + tmpDir.resolve("hs_err_pid%p.log")
                    .toAbsolutePath());
        }

        // Heap gets the same treatment as CPU. Left alone, every fork independently
        // takes the JVM's own default max heap (a fixed fraction of host/container
        // memory), so numClients forks have a combined ceiling well above what the
        // host has -- the same "each JVM thinks it owns the machine" problem the
        // ActiveProcessorCount cap solves. Give each fork a slice of a fixed budget
        // instead, leaving the remainder for the parent and the OS.
        if (!userSetHeap(configArgs)) {
            int pct = forkHeapPercentage(pipesConfig.getNumClients());
            configArgs.add("-XX:MaxRAMPercentage=" + pct);
            LOG.debug("clientId={}: auto-injected -XX:MaxRAMPercentage={} (numClients={})",
                    clientId, pct, pipesConfig.getNumClients());
        }

        // If the user hasn't explicitly set -XX:ActiveProcessorCount, size each
        // forked JVM's view of CPUs to a fair slice of the host. Otherwise each
        // JVM defaults its GC, JIT, and common ForkJoinPool to "all cores", which
        // means N forked JVMs collectively spawn N x cores GC threads etc. and
        // fight each other. We also reserve PARENT_RESERVED_CORES so the parent
        // JVM (which serializes requests, deserializes responses, runs heartbeat
        // bookkeeping) isn't starved for CPU.
        // Skip the auto-cap when the computed slice would drop below
        // MIN_AUTO_CAP_SLICE -- below that, the fork can't keep its socket
        // reader responsive and back-pressures the parent.
        if (!hasActiveProcessorCount) {
            int hostCores = Runtime.getRuntime().availableProcessors();
            int forkBudget = Math.max(1, hostCores - PARENT_RESERVED_CORES);
            int slice = forkBudget / pipesConfig.getNumClients();
            if (slice >= MIN_AUTO_CAP_SLICE) {
                configArgs.add("-XX:ActiveProcessorCount=" + slice);
                LOG.debug("clientId={}: auto-injected -XX:ActiveProcessorCount={} " +
                        "(hostCores={}, parentReserved={}, numClients={})",
                        clientId, slice, hostCores, PARENT_RESERVED_CORES,
                        pipesConfig.getNumClients());
            } else {
                LOG.info("clientId={}: skipping -XX:ActiveProcessorCount auto-cap " +
                        "(would yield slice={} < MIN_AUTO_CAP_SLICE={}; " +
                        "hostCores={}, parentReserved={}, numClients={}).{}",
                        clientId, slice, MIN_AUTO_CAP_SLICE, hostCores,
                        PARENT_RESERVED_CORES, pipesConfig.getNumClients(),
                        pipesConfig.getNumClients() > 1
                                ? " Consider lowering numClients on this host." : "");
            }
        }

        if (origGCString != null && newGCLogString != null) {
            configArgs.remove(origGCString);
            configArgs.add(newGCLogString);
        }

        List<String> commandLine = new ArrayList<>();
        String javaPath = pipesConfig.getJavaPath();
        commandLine.add(ProcessUtils.escapeCommandLine(javaPath));

        if (!hasClassPath) {
            Path argFile = writeArgFile(tmpDir);
            commandLine.add("@" + argFile.toAbsolutePath());
        }

        if (!hasHeadless) {
            commandLine.add("-Djava.awt.headless=true");
        }
        if (hasExitOnOOM) {
            LOG.info("I notice that you have a jdk setting to exit/crash on OOM. If you run heavy external processes " +
                    "like tesseract, this setting may result in orphaned processes which could be disastrous for performance.");
        }
        if (!hasLog4j) {
            commandLine.add("-Dlog4j.configurationFile=classpath:pipes-fork-server-default-log4j2.xml");
        }
        commandLine.add("-DpipesClientId=" + clientId);
        commandLine.addAll(configArgs);
        commandLine.add("-Djava.io.tmpdir=" + tmpDir.toAbsolutePath());
        commandLine.add("org.apache.tika.pipes.core.server.PipesServer");

        commandLine.add(Integer.toString(port));
        commandLine.add(tikaConfigPath.toAbsolutePath().toString());

        LOG.debug("clientId={}: commandline: {}", clientId, commandLine);
        return commandLine.toArray(new String[0]);
    }

    private Path writeArgFile(Path tmpDir) throws IOException {
        Path argFile = tmpDir.resolve("jvm-args.txt");
        // forward any tika.extras.dir jars to the forked PipesServer
        String classpath = TikaExtras.appendJarsToClasspath(System.getProperty("java.class.path"));
        String normalizedClasspath = classpath.replace("\\", "/");
        String content = "-cp\n\"" + normalizedClasspath + "\"\n";
        Files.writeString(argFile, content, StandardCharsets.UTF_8);
        LOG.debug("clientId={}: wrote argfile with classpath ({} chars) to {}, content starts with: {}",
                clientId, classpath.length(), argFile, content.substring(0, Math.min(100, content.length())));
        return argFile;
    }
}
