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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.TrackingFetcherManager;
import org.apache.tika.pipes.core.TrackingFetcherManager.TrackedFetcher;

/** A fetcher deleted mid-task stays open until the task that leased it ends. */
public class PipesWorkerLeaseTest {

    private static final PipesResult PARSE_FAILED =
            new PipesResult(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, "parse failed");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFetcherClosedOnlyAfterTask(boolean parseThrows) throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager();
        manager.saveComponent(TrackingFetcherManager.config("f"));
        TaskLeases leases = new TaskLeases(manager, null);
        int[] closesDuringParse = {-1};
        ParseHandler parseHandler = new ParseHandler(null, null, null, null, null, null,
                ParseMode.RMETA) {
            @Override
            PipesWorker.ParseDataOrPipesResult parseWithStream(FetchEmitTuple t,
                    TikaInputStream stream, Metadata metadata, ParseContext ctx) {
                try {
                    manager.deleteComponent("f");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                closesDuringParse[0] = ((TrackedFetcher) manager.getBuilt().get(0)).getCloses();
                if (parseThrows) {
                    throw new IllegalStateException("parser blew up");
                }
                return new PipesWorker.ParseDataOrPipesResult(null, PARSE_FAILED);
            }
        };
        FetchEmitTuple tuple = new FetchEmitTuple("id", new FetchKey("f", "k"),
                new EmitKey("e", "k"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        PipesWorker worker = new PipesWorker(tuple, new ParseContext(), null, leases,
                new FetchHandler(null, leases), parseHandler, null, null, ParseMode.RMETA);

        if (parseThrows) {
            assertThrows(IllegalStateException.class, worker::call);
        } else {
            assertEquals(PARSE_FAILED.status(), worker.call().status());
        }
        assertEquals(0, closesDuringParse[0], "closed while the task still held it");
        assertEquals(1, ((TrackedFetcher) manager.getBuilt().get(0)).getCloses());
    }
}
