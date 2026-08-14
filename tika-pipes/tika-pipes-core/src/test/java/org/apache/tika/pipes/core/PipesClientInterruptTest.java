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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;

/**
 * A scripted stand-in for the forked server proves what happens to the
 * connection when the thread inside {@link PipesClient#process} is
 * interrupted. The client rethrows InterruptedException; a pooled client
 * then goes back to the queue, so the connection must not stay open with a
 * request in flight -- the next borrower's ping would hang on it until
 * the socket timeout.
 */
public class PipesClientInterruptTest {

    /**
     * Interrupting an in-flight process() must close the connection: the
     * scripted server sees SHUT_DOWN or EOF instead of a socket that stays
     * open with an abandoned request on it.
     */
    @Test
    @Timeout(30)
    public void interruptClosesTheConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CountDownLatch heartbeatStarted = new CountDownLatch(1);
            CountDownLatch connectionClosed = new CountDownLatch(1);
            Thread sentinel = new Thread(() ->
                    runScriptedServer(serverSocket, heartbeatStarted, connectionClosed));
            sentinel.setDaemon(true);
            sentinel.start();

            PipesConfig pipesConfig = new PipesConfig();
            SentinelServerManager manager = new SentinelServerManager(serverSocket.getLocalPort());
            PipesClient client = new PipesClient(pipesConfig, manager);

            AtomicReference<Throwable> fromProcess = new AtomicReference<>();
            CountDownLatch processReturned = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                try {
                    client.process(new FetchEmitTuple("interrupt-test",
                            new FetchKey("fetcher", "key"), new EmitKey(), new Metadata(),
                            new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                } catch (Throwable t) {
                    fromProcess.set(t);
                } finally {
                    processReturned.countDown();
                }
            });
            worker.start();

            assertTrue(heartbeatStarted.await(15, TimeUnit.SECONDS),
                    "the scripted server never got the request; the test proves nothing");
            worker.interrupt();

            assertTrue(processReturned.await(15, TimeUnit.SECONDS),
                    "process() must return after the interrupt");
            assertTrue(fromProcess.get() instanceof InterruptedException,
                    "process() must rethrow the interrupt, got: " + fromProcess.get());
            assertTrue(connectionClosed.await(5, TimeUnit.SECONDS),
                    "the interrupted client left its connection open with a request in flight");
            assertTrue(manager.abandoned,
                    "the manager was not told; a per-client worker never dials back, so the "
                            + "next connect() would wait out the accept timeout for nothing");
            client.close();
        }
    }

    /**
     * Interrupting during startup retry must leave the same state as
     * interrupting mid-parse: connection closed, manager told. The scripted
     * server breaks the handshake (a valid frame of the wrong type instead of
     * READY), which lands the client in the retry backoff where the interrupt
     * is delivered.
     */
    @Test
    @Timeout(30)
    public void interruptDuringStartupBackoffAbandonsTheConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CountDownLatch badHandshakeSent = new CountDownLatch(1);
            CountDownLatch connectionClosed = new CountDownLatch(1);
            Thread sentinel = new Thread(() ->
                    runBadHandshakeServer(serverSocket, badHandshakeSent, connectionClosed));
            sentinel.setDaemon(true);
            sentinel.start();

            PipesConfig pipesConfig = new PipesConfig();
            SentinelServerManager manager = new SentinelServerManager(serverSocket.getLocalPort());
            PipesClient client = new PipesClient(pipesConfig, manager);

            AtomicReference<Throwable> fromProcess = new AtomicReference<>();
            CountDownLatch processReturned = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                try {
                    client.process(new FetchEmitTuple("interrupt-startup-test",
                            new FetchKey("fetcher", "key"), new EmitKey(), new Metadata(),
                            new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                } catch (Throwable t) {
                    fromProcess.set(t);
                } finally {
                    processReturned.countDown();
                }
            });
            worker.start();

            assertTrue(badHandshakeSent.await(15, TimeUnit.SECONDS),
                    "the scripted server never got a connection; the test proves nothing");
            worker.interrupt();

            assertTrue(processReturned.await(15, TimeUnit.SECONDS),
                    "process() must return after the interrupt");
            assertTrue(fromProcess.get() instanceof InterruptedException,
                    "process() must rethrow the interrupt, got: " + fromProcess.get());
            assertTrue(connectionClosed.await(5, TimeUnit.SECONDS),
                    "the interrupted client left its half-established connection open");
            assertTrue(manager.abandoned,
                    "the manager was not told; an abandoned per-client worker never "
                            + "dials back, mid-handshake or not");
            client.close();
        }
    }

    /**
     * Accepts one connection and answers the handshake with a valid frame of
     * the wrong type, sending the client into its reconnect backoff. Releases
     * connectionClosed when the client sends SHUT_DOWN or the socket reaches
     * EOF.
     */
    private static void runBadHandshakeServer(ServerSocket serverSocket,
            CountDownLatch badHandshakeSent, CountDownLatch connectionClosed) {
        try (Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            PipesMessage.ack().write(out);
            badHandshakeSent.countDown();
            while (true) {
                PipesMessage message = PipesMessage.read(in);
                if (message.type() == PipesMessageType.SHUT_DOWN) {
                    break;
                }
            }
            connectionClosed.countDown();
        } catch (IOException e) {
            // EOF or reset: the connection is gone either way
            connectionClosed.countDown();
        }
    }

    /**
     * Accepts one connection and speaks just enough protocol: READY, consume
     * the NEW_REQUEST, then WORKING heartbeats -- each one wakes the client's
     * read loop so the interrupt check runs. Releases connectionClosed when
     * the client sends SHUT_DOWN or the socket reaches EOF.
     */
    private static void runScriptedServer(ServerSocket serverSocket,
            CountDownLatch heartbeatStarted, CountDownLatch connectionClosed) {
        try (Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            PipesMessage.ready().write(out);
            PipesMessage.read(in); // NEW_REQUEST

            Thread heartbeat = new Thread(() -> {
                try {
                    while (true) {
                        PipesMessage.working().write(out);
                        heartbeatStarted.countDown();
                        Thread.sleep(100);
                    }
                } catch (IOException | InterruptedException e) {
                    // socket closed under us, or test over -- either way, done
                }
            });
            heartbeat.setDaemon(true);
            heartbeat.start();

            while (true) {
                PipesMessage message = PipesMessage.read(in);
                if (message.type() == PipesMessageType.SHUT_DOWN) {
                    break;
                }
            }
            connectionClosed.countDown();
        } catch (EOFException e) {
            // close without SHUT_DOWN still counts: the connection is gone
            connectionClosed.countDown();
        } catch (IOException e) {
            connectionClosed.countDown();
        }
    }

    /**
     * Points the client at the scripted server; no forked process anywhere.
     */
    private static final class SentinelServerManager implements ServerManager {
        private final int port;
        private volatile boolean abandoned;

        private SentinelServerManager(int port) {
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
        public java.nio.file.Path getTempDirectory() {
            return null;
        }

        @Override
        public void close() {
            // nothing to close
        }
    }
}
