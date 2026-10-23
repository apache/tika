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

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

public class PipesClientClosedManagerTest {

    /**
     * A request that reaches initialization after its manager was closed (a parse racing
     * PipesParser.close()/AsyncProcessor.close()) must come back as FAILED_TO_INITIALIZE
     * rather than escaping as an unchecked IllegalStateException -- and must not mark a
     * worker for restart, since there is nothing left to restart.
     */
    @Test
    @Timeout(30)
    public void closedManagerDuringInitReturnsFailedToInitialize() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            SentinelServerManager manager = new SentinelServerManager(serverSocket.getLocalPort());
            manager.closed = true;
            try (PipesClient client = new PipesClient(new PipesConfig(), manager)) {
                PipesResult result = client.process(new FetchEmitTuple("closed-manager-test",
                        new FetchKey("fetcher", "key"), new EmitKey(), new Metadata(),
                        new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

                assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, result.status(),
                        "got: " + result.status() + " / " + result.message());
                assertTrue(result.message().contains("closed"),
                        "message should carry the manager's reason, got: " + result.message());
                assertNull(manager.marked, "nothing to restart on a closed manager");
                assertFalse(manager.abandoned, "no connection was established to abandon");
            }
        }
    }
}
