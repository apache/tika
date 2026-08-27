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
import java.net.Socket;
import java.nio.file.Path;

/**
 * Points a {@link PipesClient} at a scripted in-test server; no forked process anywhere.
 */
final class SentinelServerManager implements ServerManager {
    private final int port;
    volatile boolean abandoned;
    volatile RestartReason marked;

    SentinelServerManager(int port) {
        this.port = port;
    }

    @Override
    public void connectionAbandoned() {
        abandoned = true;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void ensureRunning() {
        // the scripted server is already listening
    }

    @Override
    public Socket connect(int socketTimeoutMs) throws IOException {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(socketTimeoutMs);
        return socket;
    }

    @Override
    public void shutdown() {
        // nothing to shut down
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Path getTempDirectory() {
        return null;
    }

    @Override
    public long getGeneration() {
        return 0;
    }

    @Override
    public void markServerForRestart(RestartReason reason, long generation) {
        marked = reason;
    }

    @Override
    public int handleCrashAndGetExitCode(long generation) {
        return -1;
    }

    @Override
    public void close() {
        // nothing to close
    }
}
