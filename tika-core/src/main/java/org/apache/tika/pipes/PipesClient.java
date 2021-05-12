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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.async.AsyncConfig;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.utils.ProcessUtils;

public class PipesClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesClient.class);

    private Process process;
    private LogGobbler logGobbler;
    private Thread logGobblerThread;
    private final PipesConfigBase pipesConfig;
    private DataOutputStream output;
    private DataInputStream input;

    public PipesClient(PipesConfigBase pipesConfig) {
        this.pipesConfig = pipesConfig;
    }

    private int filesProcessed = 0;

    public int getFilesProcessed() {
        return filesProcessed;
    }

    private boolean ping() {
        if (process == null || ! process.isAlive()) {
            return false;
        }
        try {
            output.write(PipesServer.PING);
            output.flush();
            int ping = input.read();
            if (ping == PipesServer.PING) {
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
            process.destroyForcibly();
        }
    }

    public PipesResult process(FetchEmitTuple t) throws IOException {
        if (! ping()) {
            restart();
        }

        if (pipesConfig.getMaxFilesProcessed() > 0 &&
                filesProcessed >= pipesConfig.getMaxFilesProcessed()) {
            LOG.info("restarting server after hitting max files: " + filesProcessed);
            restart();
        }
        //TODO consider adding a timer here too
        // this could block forever if the watchdog thread in the server fails
        // or is starved
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
            objectOutputStream.writeObject(t);
        }
        byte[] bytes = bos.toByteArray();
        output.write(PipesServer.CALL);
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();

        long start = System.currentTimeMillis();
        try {
            return readResults(t);
        } catch (IOException e) {

            try {
                process.waitFor(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruptedException) {
                //wait just a little bit to let process end to get exit value
            } finally {
                process.destroyForcibly();
            }
            if (! process.isAlive() && PipesServer.TIMEOUT_EXIT_CODE == process.exitValue()) {
                LOG.warn("{} timed out", t.getId());
                return PipesResult.TIMEOUT;
            }
            return PipesResult.UNSPECIFIED_CRASH;
        }
    }

    private PipesResult readResults(FetchEmitTuple t) throws IOException {
        int status = input.read();
        switch (status) {
            case PipesServer.OOM:
                LOG.warn("oom: " + t.getId());
                return PipesResult.OOM;
            case PipesServer.TIMEOUT:
                LOG.warn("timeout: " + t.getId());
                return PipesResult.TIMEOUT;
            case PipesServer.EMIT_EXCEPTION:
                LOG.warn("emit exception: " + t.getId());
                return readMessage(PipesResult.STATUS.EMIT_EXCEPTION);
            case PipesServer.NO_EMITTER_FOUND:
                LOG.warn("no emitter found: " + t.getId());
                return PipesResult.NO_EMITTER_FOUND;
            case PipesServer.PARSE_SUCCESS:
            case PipesServer.PARSE_EXCEPTION_EMIT:
                LOG.info("parse success: " + t.getId());
                return deserializeEmitData();
            case PipesServer.PARSE_EXCEPTION_NO_EMIT:
                return readMessage(PipesResult.STATUS.PARSE_EXCEPTION_NO_EMIT);
            case PipesServer.EMIT_SUCCESS:
                LOG.info("emit success: " + t.getId());
                return PipesResult.EMIT_SUCCESS;
            case PipesServer.EMIT_SUCCESS_PARSE_EXCEPTION:
                return readMessage(PipesResult.STATUS.EMIT_SUCCESS_PARSE_EXCEPTION);
            default :
                throw new IOException("problem reading response from server " + status);
        }

    }

    private PipesResult readMessage(PipesResult.STATUS status) throws IOException {
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
        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return new PipesResult((EmitData)objectInputStream.readObject());
        } catch (ClassNotFoundException e) {
            LOG.error("class not found exception deserializing data", e);
            //this should be catastrophic
            throw new RuntimeException(e);
        }

    }

    private void restart() throws IOException {
        if (process != null) {
            process.destroyForcibly();
            LOG.info("restarting process");
        } else {
            LOG.info("starting process");
        }
        if (logGobblerThread != null) {
            logGobblerThread.interrupt();
        }
        ProcessBuilder pb = new ProcessBuilder(getCommandline());
        process = pb.start();
        logGobbler = new LogGobbler(process.getErrorStream());
        logGobblerThread = new Thread(logGobbler);
        logGobblerThread.setDaemon(true);
        logGobblerThread.start();
        input = new DataInputStream(process.getInputStream());
        output = new DataOutputStream(process.getOutputStream());
    }

    private String[] getCommandline() {
        List<String> configArgs = pipesConfig.getForkedJvmArgs();
        boolean hasClassPath = false;
        boolean hasHeadless = false;
        boolean hasExitOnOOM = false;

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
        }

        List<String> commandLine = new ArrayList<>();
        String javaPath = pipesConfig.getJavaPath();
        commandLine.add(ProcessUtils.escapeCommandLine(javaPath));
        if (! hasClassPath) {
            commandLine.add("-cp");
            commandLine.add(System.getProperty("java.class.path"));
        }
        if (! hasHeadless) {
            commandLine.add("-Djava.awt.headless=true");
        }
        if (! hasExitOnOOM) {
            //warn
        }
        commandLine.addAll(configArgs);
        commandLine.add("org.apache.tika.pipes.PipesServer");
        commandLine.add(
                ProcessUtils.escapeCommandLine(pipesConfig.getTikaConfig().toAbsolutePath().toString()));

        //turn off emit batching
        String maxForEmitBatchBytes = "0";
        if (pipesConfig instanceof AsyncConfig) {
            maxForEmitBatchBytes =
                    Long.toString(((AsyncConfig)pipesConfig).getMaxForEmitBatchBytes());
        }
        commandLine.add(maxForEmitBatchBytes);
        commandLine.add(Long.toString(pipesConfig.getTimeoutMillis()));
        commandLine.add(Long.toString(pipesConfig.getShutdownClientAfterMillis()));
        LOG.debug("commandline: " + commandLine.toString());
        return commandLine.toArray(new String[0]);
    }

    public static class LogGobbler implements Runnable {
        private static final Logger SERVER_LOG = LoggerFactory.getLogger(LogGobbler.class);

        private final BufferedReader reader;
        public LogGobbler(InputStream is) {
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            String line = null;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                return;
            }
            while (line != null) {
                if (line.startsWith("debug ")) {
                    SERVER_LOG.debug(line.substring(6));
                } else if (line.startsWith("info ")) {
                    SERVER_LOG.info(line.substring(5));
                } else if (line.startsWith("warn ")) {
                    SERVER_LOG.warn(line.substring(5));
                } else if (line.startsWith("error ")) {
                    SERVER_LOG.error(line.substring(6));
                }
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    return;
                }
            }
        }
    }
}
