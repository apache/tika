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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.server.ServerProtocolIO;

public class PipesClientPayloadLimitTest {

    /**
     * A request whose serialized form exceeds maxIpcPayloadBytes must be refused before
     * sending -- a clean PAYLOAD_LIMIT_EXCEEDED, not a worker death misreported as a crash --
     * and must leave the connection usable.
     */
    @Test
    @Timeout(45)
    public void oversizedRequestFailsFastWithoutSending() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CountDownLatch connectionClosed = new CountDownLatch(1);
            Thread sentinel = new Thread(() -> runReadyOnlyServer(serverSocket, connectionClosed));
            sentinel.setDaemon(true);
            sentinel.start();

            PipesConfig pipesConfig = new PipesConfig();
            pipesConfig.setMaxIpcPayloadBytes(ServerProtocolIO.MIN_FALLBACK_PAYLOAD_BYTES);
            SentinelServerManager manager = new SentinelServerManager(serverSocket.getLocalPort());
            try (PipesClient client = new PipesClient(pipesConfig, manager)) {
                Metadata metadata = new Metadata();
                metadata.set("oversized", "x".repeat(10_000));
                PipesResult result = client.process(new FetchEmitTuple("payload-limit-test",
                        new FetchKey("fetcher", "key"), new EmitKey(), metadata,
                        new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

                assertEquals(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, result.status(),
                        "expected client-side refusal, got: " + result.status()
                                + " / " + result.message());
                assertTrue(result.message().contains("maxIpcPayloadBytes"),
                        "message should name the limit, got: " + result.message());
                assertFalse(manager.abandoned, "nothing was sent; no reason to abandon");
                assertNull(manager.marked,
                        "the request was refused before anything was written; the worker is "
                                + "healthy and must not be recycled");
                assertFalse(connectionClosed.await(300, TimeUnit.MILLISECONDS),
                        "nothing was sent; the connection must stay usable");
            }
        }
    }

    /** Accepts one connection, sends READY, then just holds the socket open. */
    private static void runReadyOnlyServer(ServerSocket serverSocket,
            CountDownLatch connectionClosed) {
        try (Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            PipesMessage.ready().write(out);
            PipesMessage.read(in);
        } catch (IOException e) {
            // EOF or reset: the connection is gone
        }
        connectionClosed.countDown();
    }
}
