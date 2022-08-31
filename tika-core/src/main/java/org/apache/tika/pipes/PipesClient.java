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
package org.apache.tika.pipes;

import static org.apache.tika.pipes.PipesServer.STATUS.CALL;
import static org.apache.tika.pipes.PipesServer.STATUS.PING;
import static org.apache.tika.pipes.PipesServer.STATUS.READY;
import static org.apache.tika.pipes.PipesServer.STATUS.lookup;
import static org.apache.tika.pipes.PipesServer.TIMEOUT_EXIT_CODE;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

/**
 * The PipesClient is designed to be single-threaded. It only allots
 * a single thread for {@link #process(FetchEmitTuple)} processing.
 * See {@link org.apache.tika.pipes.async.AsyncProcessor} for handling
 * multiple PipesClients.
 */
public class PipesClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesClient.class);
    private static final int MAX_BYTES_BEFORE_READY = 20000;
    private static AtomicInteger CLIENT_COUNTER = new AtomicInteger(0);
    private static final long WAIT_ON_DESTROY_MS = 10000;
    //this synchronizes the creation and/or closing of the executorService
    //there are a number of assumptions throughout that PipesClient is run
    //single threaded
    private final Object[] executorServiceLock = new Object[0];
    private final PipesConfigBase pipesConfig;
    private final int pipesClientId;
    private volatile boolean closed = false;
    private ExecutorService executorService = Executors.newFixedThreadPool(1);
    private Process process;
    private DataOutputStream output;
    private DataInputStream input;
    private int filesProcessed = 0;

    public PipesClient(PipesConfigBase pipesConfig) {
        this.pipesConfig = pipesConfig;
        this.pipesClientId = CLIENT_COUNTER.getAndIncrement();
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    private boolean ping() {
        if (process == null || !process.isAlive()) {
            return false;
        }
        try {
            output.write(PING.getByte());
            output.flush();
            int ping = input.read();
            if (ping == PING.getByte()) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        if (process != null) {
            try {
                destroyForcibly();
            } catch (InterruptedException e) {
                //swallow
            }
        }
        synchronized (executorServiceLock) {
            if (executorService != null) {
                executorService.shutdownNow();
            }
            closed = true;
        }
    }

    public PipesResult process(FetchEmitTuple t) throws IOException, InterruptedException {
        boolean restart = false;
        if (!ping()) {
            restart = true;
        } else if (pipesConfig.getMaxFilesProcessedPerProcess() > 0 &&
                filesProcessed >= pipesConfig.getMaxFilesProcessedPerProcess()) {
            LOG.info("pipesClientId={}: restarting server after hitting max files: {}",
                    pipesClientId, filesProcessed);
            restart = true;
        }
        if (restart) {
            boolean successfulRestart = false;
            while (!successfulRestart) {
                try {
                    restart();
                    successfulRestart = true;
                } catch (TimeoutException e) {
                    LOG.warn("pipesClientId={}: couldn't restart within {} ms (startupTimeoutMillis)",
                            pipesClientId, pipesConfig.getStartupTimeoutMillis());
                    Thread.sleep(pipesConfig.getSleepOnStartupTimeoutMillis());
                }
            }
        }
        return actuallyProcess(t);
    }

    private PipesResult actuallyProcess(FetchEmitTuple t) throws InterruptedException {
        long start = System.currentTimeMillis();
        FutureTask<PipesResult> futureTask = new FutureTask<>(() -> {

            UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(t);
            }

            byte[] bytes = bos.toByteArray();
            output.write(CALL.getByte());
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
            if (LOG.isTraceEnabled()) {
                LOG.trace("pipesClientId={}: timer -- write tuple: {} ms",
                        pipesClientId,
                        System.currentTimeMillis() - start);
            }
            long readStart = System.currentTimeMillis();
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("thread interrupt");
            }
            PipesResult result = readResults(t, start);
            if (LOG.isDebugEnabled()) {
                long elapsed = System.currentTimeMillis() - readStart;
                LOG.debug("finished reading result in {} ms", elapsed);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("pipesClientId={}: timer -- read result: {} ms",
                        pipesClientId,
                        System.currentTimeMillis() - readStart);
            }
            return result;
        });

        try {
            if (closed) {
                throw new IllegalArgumentException("pipesClientId=" + pipesClientId +
                        ": PipesClient closed");
            }
            executorService.execute(futureTask);
            return futureTask.get(pipesConfig.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            destroyForcibly();
            throw e;
        } catch (ExecutionException e) {
            LOG.error("pipesClientId=" + pipesClientId + ": execution exception", e);
            long elapsed = System.currentTimeMillis() - start;
            pauseThenDestroy();
            if (!process.isAlive() && TIMEOUT_EXIT_CODE == process.exitValue()) {
                LOG.warn("pipesClientId={} server timeout: {} in {} ms", pipesClientId, t.getId(),
                        elapsed);
                return PipesResult.TIMEOUT;
            }
            process.waitFor(500, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                LOG.warn("pipesClientId={} crash: {} in {} ms with no exit code available",
                        pipesClientId, t.getId(), elapsed);
            } else {
                LOG.warn("pipesClientId={} crash: {} in {} ms with exit code {}", pipesClientId,
                        t.getId(), elapsed, process.exitValue());
            }
            return PipesResult.UNSPECIFIED_CRASH;
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            destroyForcibly();
            LOG.warn("pipesClientId={} client timeout: {} in {} ms", pipesClientId, t.getId(),
                    elapsed);
            return PipesResult.TIMEOUT;
        } finally {
            futureTask.cancel(true);
        }
    }

    private void pauseThenDestroy() throws InterruptedException {
        //wait just a little bit to let process end to get exit value
        //if there's a timeout on the server side
        try {
            process.waitFor(200, TimeUnit.MILLISECONDS);
        } finally {
            destroyForcibly();
        }
    }

    private void destroyForcibly() throws InterruptedException {
        process.destroyForcibly();
        process.waitFor(WAIT_ON_DESTROY_MS, TimeUnit.MILLISECONDS);
        //important to close streams so that threads running in this
        //process receive notice that they really ought to stop.
        //TIKA-3588 showed that we can't trust that forcibly destroying
        //the process caused the actuallyProcess thread in this process to stop.
        try {
            input.close();
        } catch (IOException closeException) {
            //swallow
        }
        try {
            output.close();
        } catch (IOException closeException) {
            //swallow
        }
        if (process.isAlive()) {
            LOG.error("Process still alive after {}ms", WAIT_ON_DESTROY_MS);
        }
    }

    private PipesResult readResults(FetchEmitTuple t, long start) throws IOException {

        int statusByte = input.read();
        long millis = System.currentTimeMillis() - start;
        PipesServer.STATUS status = null;
        try {
            status = lookup(statusByte);
        } catch (IllegalArgumentException e) {
            throw new IOException("problem reading response from server " + status);
        }

        switch (status) {
            case OOM:
                LOG.warn("pipesClientId={} oom: {} in {} ms", pipesClientId, t.getId(), millis);
                return PipesResult.OOM;
            case TIMEOUT:
                LOG.warn("pipesClientId={} server response timeout: {} in {} ms", pipesClientId,
                        t.getId(), millis);
                return PipesResult.TIMEOUT;
            case EMIT_EXCEPTION:
                LOG.warn("pipesClientId={} emit exception: {} in {} ms", pipesClientId, t.getId(),
                        millis);
                return readMessage(PipesResult.STATUS.EMIT_EXCEPTION);
            case EMITTER_NOT_FOUND:
                LOG.warn("pipesClientId={} emitter not found: {} in {} ms", pipesClientId,
                        t.getId(), millis);
                return readMessage(PipesResult.STATUS.NO_EMITTER_FOUND);
            case FETCHER_NOT_FOUND:
                LOG.warn("pipesClientId={} fetcher not found: {} in {} ms", pipesClientId,
                        t.getId(), millis);
                return readMessage(PipesResult.STATUS.NO_FETCHER_FOUND);
            case FETCHER_INITIALIZATION_EXCEPTION:
                LOG.warn("pipesClientId={} fetcher initialization exception: {} in {} ms",
                        pipesClientId, t.getId(), millis);
                return readMessage(PipesResult.STATUS.FETCHER_INITIALIZATION_EXCEPTION);
            case FETCH_EXCEPTION:
                LOG.warn("pipesClientId={} fetch exception: {} in {} ms", pipesClientId, t.getId(),
                        millis);
                return readMessage(PipesResult.STATUS.FETCH_EXCEPTION);
            case PARSE_SUCCESS:
                //there may have been a parse exception, but the parse didn't crash
                LOG.info("pipesClientId={} parse success: {} in {} ms", pipesClientId, t.getId(),
                        millis);
                return deserializeEmitData();
            case PARSE_EXCEPTION_NO_EMIT:
                return readMessage(PipesResult.STATUS.PARSE_EXCEPTION_NO_EMIT);
            case EMIT_SUCCESS:
                LOG.info("pipesClientId={} emit success: {} in {} ms", pipesClientId, t.getId(),
                        millis);
                return PipesResult.EMIT_SUCCESS;
            case EMIT_SUCCESS_PARSE_EXCEPTION:
                return readMessage(PipesResult.STATUS.EMIT_SUCCESS_PARSE_EXCEPTION);
            case EMPTY_OUTPUT:
                return PipesResult.EMPTY_OUTPUT;
            //fall through
            case READY:
            case CALL:
            case PING:
            case FAILED_TO_START:
                throw new IOException("Not expecting this status: " + status);
            default:
                throw new IOException("Need to handle procesing for: " + status);
        }

    }

    private PipesResult readMessage(PipesResult.STATUS status) throws IOException {
        //readInt checks for EOF
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String msg = new String(bytes, StandardCharsets.UTF_8);
        return new PipesResult(status, msg);
    }

    private PipesResult deserializeEmitData() throws IOException {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new UnsynchronizedByteArrayInputStream(bytes))) {
            EmitData emitData = (EmitData) objectInputStream.readObject();

            String stack = getStack(emitData);
            if (StringUtils.isBlank(stack)) {
                return new PipesResult(emitData);
            } else {
                return new PipesResult(emitData, stack);
            }
        } catch (ClassNotFoundException e) {
            LOG.error("class not found exception deserializing data", e);
            //this should be catastrophic
            throw new RuntimeException(e);
        }
    }

    private String getStack(EmitData emitData) {
        if (emitData.getMetadataList() == null || emitData.getMetadataList().size() < 1) {
            return StringUtils.EMPTY;
        }
        return emitData.getMetadataList().get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
    }

    private void restart() throws IOException, InterruptedException, TimeoutException {
        if (process != null) {
            destroyForcibly();
            boolean processEnded = process.waitFor(30, TimeUnit.SECONDS);
            if (! processEnded) {
                LOG.warn("pipesClientId={}: process has not yet ended", pipesClientId);
            }
            executorService.shutdownNow();
            boolean shutdown = executorService.awaitTermination(30, TimeUnit.SECONDS);
            if (! shutdown) {
                LOG.warn("pipesClientId={}: executorService has not yet shutdown", pipesClientId);
            }
            synchronized (executorServiceLock) {
                if (closed) {
                    throw new IllegalArgumentException("pipesClientId=" + pipesClientId +
                            ": PipesClient closed");
                }
                executorService = Executors.newFixedThreadPool(1);
            }
            LOG.info("pipesClientId={}: restarting process", pipesClientId);
        } else {
            LOG.info("pipesClientId={}: starting process", pipesClientId);
        }
        ProcessBuilder pb = new ProcessBuilder(getCommandline());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = pb.start();

        input = new DataInputStream(process.getInputStream());
        output = new DataOutputStream(process.getOutputStream());

        //wait for ready signal
        final UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
        FutureTask<Integer> futureTask = new FutureTask<>(() -> {
            int b = input.read();
            int read = 1;
            while (read < MAX_BYTES_BEFORE_READY && b != READY.getByte()) {

                if (b == -1) {
                    throw new RuntimeException(getMsg("pipesClientId=" + pipesClientId + ": " +
                            "Couldn't start server -- read EOF before 'ready' byte.\n" +
                            " process isAlive=" + process.isAlive(), bos));
                }
                bos.write(b);
                b = input.read();
                read++;
            }
            if (read >= MAX_BYTES_BEFORE_READY) {
                throw new RuntimeException(getMsg("pipesClientId=" + pipesClientId + ": " +
                        "Couldn't start server: read too many bytes before 'ready' byte.\n" +
                        " Make absolutely certain that your logger is not writing to " +
                        "stdout.\n", bos));
            }
            if (bos.size() > 0) {
                LOG.warn("pipesClientId={}: From forked process before start byte: {}",
                        pipesClientId, bos.toString(StandardCharsets.UTF_8));
            }
            return 1;
        });
        long start = System.currentTimeMillis();
        executorService.submit(futureTask);
        try {
            futureTask.get(pipesConfig.getStartupTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            destroyForcibly();
            throw e;
        } catch (ExecutionException e) {
            LOG.error("pipesClientId=" + pipesClientId + ": couldn't start server", e);
            destroyForcibly();
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.error("pipesClientId={} didn't receive ready byte from server within " +
                            "StartupTimeoutMillis {}; ms elapsed {}; did read >{}<",
                    pipesClientId, pipesConfig.getStartupTimeoutMillis(),
                    elapsed, bos.toString(StandardCharsets.UTF_8));
            destroyForcibly();
            throw e;
        } finally {
            futureTask.cancel(true);
        }
    }

    private static String getMsg(String msg, UnsynchronizedByteArrayOutputStream bos) {
        String readSoFar = bos.toString(StandardCharsets.UTF_8);
        if (StringUtils.isBlank(readSoFar)) {
            return msg;
        } else {
            return msg + "So far, I've read: >" + readSoFar + "<";
        }
    }

    private String[] getCommandline() {
        List<String> configArgs = pipesConfig.getForkedJvmArgs();
        boolean hasClassPath = false;
        boolean hasHeadless = false;
        boolean hasExitOnOOM = false;
        boolean hasLog4j = false;
        String origGCString = null;
        String newGCLogString = null;
        for (String arg : configArgs) {
            if (arg.startsWith("-Djava.awt.headless")) {
                hasHeadless = true;
            }
            if (arg.equals("-cp") || arg.equals("--classpath")) {
                hasClassPath = true;
            }
            if (arg.equals("-XX:+ExitOnOutOfMemoryError") ||
                    arg.equals("-XX:+CrashOnOutOfMemoryError")) {
                hasExitOnOOM = true;
            }
            if (arg.startsWith("-Dlog4j.configuration")) {
                hasLog4j = true;
            }
            if (arg.startsWith("-Xloggc:")) {
                origGCString = arg;
                newGCLogString = arg.replace("${pipesClientId}", "id-" + pipesClientId);
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
            commandLine.add("-cp");
            commandLine.add(System.getProperty("java.class.path"));
        }
        if (!hasHeadless) {
            commandLine.add("-Djava.awt.headless=true");
        }
        if (hasExitOnOOM) {
            LOG.warn(
                    "I notice that you have an exit/crash on OOM. If you run heavy external processes " +
                            "like tesseract, this setting may result in orphaned processes which could be disastrous" +
                            " for performance.");
        }
        if (!hasLog4j) {
            commandLine.add(
                    "-Dlog4j.configurationFile=classpath:pipes-fork-server-default-log4j2.xml");
        }
        commandLine.add("-DpipesClientId=" + pipesClientId);
        commandLine.addAll(configArgs);
        commandLine.add("org.apache.tika.pipes.PipesServer");
        commandLine.add(ProcessUtils.escapeCommandLine(
                pipesConfig.getTikaConfig().toAbsolutePath().toString()));

        commandLine.add(Long.toString(pipesConfig.getMaxForEmitBatchBytes()));
        commandLine.add(Long.toString(pipesConfig.getTimeoutMillis()));
        commandLine.add(Long.toString(pipesConfig.getShutdownClientAfterMillis()));
        LOG.debug("pipesClientId={}: commandline: {}", pipesClientId, commandLine);
        return commandLine.toArray(new String[0]);
    }
}
