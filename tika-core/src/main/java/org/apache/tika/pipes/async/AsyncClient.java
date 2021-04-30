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
package org.apache.tika.pipes.async;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.utils.ProcessUtils;

public class AsyncClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);

    //TODO: make these configurable
    private long parseTimeoutMillis = 30000;
    private long waitTimeoutMillis = 500000;

    private Process process;
    private final Path tikaConfigPath;
    private DataOutputStream output;
    private DataInputStream input;

    public AsyncClient(Path tikaConfigPath) {
        this.tikaConfigPath = tikaConfigPath;
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
            output.write(AsyncServer.PING);
            output.flush();
            int ping = input.read();
            if (ping == AsyncServer.PING) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public void close() {
        process.destroyForcibly();
    }

    public AsyncResult process(FetchEmitTuple t) throws IOException {
        if (! ping()) {
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
        output.write(AsyncServer.CALL);
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();

        long start = System.currentTimeMillis();
        try {
            return readResults(t);
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > parseTimeoutMillis) {
                LOG.warn("{} timed out", t.getId());
                return AsyncResult.TIMEOUT;
            }
            return AsyncResult.UNSPECIFIED_CRASH;
        }
    }

    private AsyncResult readResults(FetchEmitTuple t) throws IOException {
        int status = input.read();
        //TODO clean this up, never return null
        if (status == AsyncServer.OOM) {
            LOG.warn(t.getId() + " oom");
            return AsyncResult.OOM;
        } else if (status == AsyncServer.READY) {
        } else {
            throw new IOException("problem reading response from server " + status);
        }
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return new AsyncResult((EmitData)objectInputStream.readObject());
        } catch (ClassNotFoundException e) {
            //this should be catastrophic
            throw new RuntimeException(e);
        }

    }

    private void restart() throws IOException {
        if (process != null) {
            process.destroyForcibly();
        }
        LOG.debug("restarting process");
        ProcessBuilder pb = new ProcessBuilder(getCommandline());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = pb.start();
        input = new DataInputStream(process.getInputStream());
        output = new DataOutputStream(process.getOutputStream());
    }

    private String[] getCommandline() {
        //TODO: make this all configurable
        return new String[]{
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "org.apache.tika.pipes.async.AsyncServer",
                ProcessUtils.escapeCommandLine(tikaConfigPath.toAbsolutePath().toString()),
                Long.toString(parseTimeoutMillis),
                Long.toString(waitTimeoutMillis),
        };
    }
}
