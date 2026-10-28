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
package org.apache.tika.pipes.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import org.apache.tika.grpc.v2alpha1.Cardinality;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiResponse;
import org.apache.tika.grpc.v2alpha1.FetchAndParseRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseResponse;
import org.apache.tika.grpc.v2alpha1.ParsedDocument;
import org.apache.tika.grpc.v2alpha1.TikaServiceGrpc;
import org.apache.tika.grpc.v2alpha1.Values;
import org.apache.tika.pipes.ExternalTestBase;

/** The v2alpha1 service on a live server, next to v1 on the same port. */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "exec:exec classpath exceeds Windows CreateProcess command-line length limit")
class V2Alpha1Test extends ExternalTestBase {

    // declared in tika-config.json: a fetcher saved at runtime would not reach the forked
    // workers under the default in-memory config store
    private static final String FETCHER_ID = "fixtures";

    @Test
    void v2ParsesAndReportsFailuresInBand() throws Exception {
        ManagedChannel channel = getManagedChannel();
        try {
            FetchAndParseResponse csv = TikaServiceGrpc.newBlockingStub(channel)
                    .fetchAndParse(FetchAndParseRequest.newBuilder()
                            .setFetcherId(FETCHER_ID).setFetchKey("sample.csv").build());
            assertEquals("PARSE_SUCCESS", csv.getPipesStatus(), csv.getMessage());
            ParsedDocument document = csv.getDocuments(0);
            assertTrue(!document.getContent().isBlank(), "content");
            assertTrue(document.containsMetadata("tk:content-handler-type"));
            // a registry hint on the wire: every parser in the chain, as a BAG
            Values parsedBy = document.getMetadataOrThrow("tk:parsed-by");
            assertEquals(Cardinality.CARDINALITY_BAG, parsedBy.getCardinality());
            assertTrue(parsedBy.getValuesCount() > 1, parsedBy.toString());

            List<FetchAndParseBidiResponse> responses = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch done = new CountDownLatch(1);
            StreamObserver<FetchAndParseBidiRequest> requests = TikaServiceGrpc.newStub(channel)
                    .fetchAndParseBidi(new StreamObserver<>() {
                        @Override
                        public void onNext(FetchAndParseBidiResponse response) {
                            responses.add(response);
                        }

                        @Override
                        public void onError(Throwable t) {
                            errors.add(t);
                            done.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            done.countDown();
                        }
                    });
            requests.onNext(bidi("html", FETCHER_ID, "sample.html"));
            requests.onNext(bidi("missing-fetcher", "no-such-fetcher", "sample.html"));
            requests.onNext(bidi("xml", FETCHER_ID, "sample.xml"));
            requests.onCompleted();
            assertTrue(done.await(2, TimeUnit.MINUTES), "bidi stream did not finish");

            assertTrue(errors.isEmpty(), String.valueOf(errors));
            Map<String, FetchAndParseBidiResponse> byId = new HashMap<>();
            responses.forEach(r -> byId.put(r.getRequestId(), r));
            assertEquals(3, byId.size());
            assertEquals("PARSE_SUCCESS", byId.get("html").getResult().getPipesStatus());
            assertEquals(Status.Code.NOT_FOUND.value(), byId.get("missing-fetcher").getError().getCode());
            assertEquals("PARSE_SUCCESS", byId.get("xml").getResult().getPipesStatus());
        } finally {
            channel.shutdown();
        }
    }

    private static FetchAndParseBidiRequest bidi(String id, String fetcherId, String fetchKey) {
        return FetchAndParseBidiRequest.newBuilder()
                .setRequestId(id).setFetcherId(fetcherId).setFetchKey(fetchKey).build();
    }
}
