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
package org.apache.tika.pipes.core.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * End-to-end checks at the actual wire entry points. A FetchEmitTuple is always untrusted request
 * input, so its parseContext must not introduce a wire-blocked component. All three entry points
 * (/pipes, /async, fork IPC) share {@code FetchEmitTupleDeserializer}, which enforces this.
 */
public class WireRestrictedFetchEmitTupleTest {

    private static final String WIRE_BLOCKED_PARSE_CONTEXT =
            "\"parse-context\":{\"typed\":{\"external-parser\":{\"config\":{" +
            "\"commandLine\":[\"/bin/sh\",\"-c\",\"echo x\"]," +
            "\"supportedTypes\":[\"text/plain\"]}}}}";

    private static String tuple(String parseContextField) {
        return "{\"id\":\"t\",\"fetcher\":\"f\",\"fetchKey\":\"k\"," +
                "\"emitter\":\"e\",\"emitKey\":\"ek\",\"onParseException\":\"skip\"" +
                (parseContextField.isEmpty() ? "" : "," + parseContextField) + "}";
    }

    @Test
    public void pipesEndpointRejectsParserInjection() {
        Exception e = assertThrows(Exception.class,
                () -> JsonFetchEmitTuple.fromJson(new StringReader(tuple(WIRE_BLOCKED_PARSE_CONTEXT))));
        assertTrue(root(e).contains("may not be supplied via a request parseContext"),
                "expected wire-blocked rejection, got: " + root(e));
    }

    @Test
    public void asyncEndpointRejectsParserInjection() {
        Exception e = assertThrows(Exception.class,
                () -> JsonFetchEmitTupleList.fromJson(new StringReader("[" + tuple(WIRE_BLOCKED_PARSE_CONTEXT) + "]")));
        assertTrue(root(e).contains("may not be supplied via a request parseContext"),
                "expected wire-blocked rejection, got: " + root(e));
    }

    @Test
    public void pipesEndpointAllowsSafeParseContext() throws Exception {
        String safe = "\"parse-context\":{" +
                "\"basic-content-handler-factory\":{\"type\":\"XML\",\"writeLimit\":1000}," +
                "\"timeout-limits\":{\"progressTimeoutMillis\":5000,\"totalTaskTimeoutMillis\":60000}}";
        FetchEmitTuple t = JsonFetchEmitTuple.fromJson(new StringReader(tuple(safe)));
        assertNotNull(t);
        assertTrue(t.getParseContext().hasJsonConfig("timeout-limits"));
        assertTrue(t.getParseContext().hasJsonConfig("basic-content-handler-factory"));
    }

    @Test
    public void ipcRoundTripsSafeTuple() throws Exception {
        FetchEmitTuple t = new FetchEmitTuple("t", new FetchKey("f", "k"),
                new EmitKey("e", "ek"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        byte[] bytes = JsonPipesIpc.toBytes(t);
        FetchEmitTuple back = JsonPipesIpc.fromBytes(bytes, FetchEmitTuple.class);
        assertEquals(t, back);
    }

    @Test
    public void forkIpcRejectsParserInjection() throws Exception {
        // The fork-IPC path uses Smile but shares the same restricted FetchEmitTupleDeserializer.
        byte[] smile = new ObjectMapper(new SmileFactory())
                .writeValueAsBytes(new ObjectMapper().readTree(tuple(WIRE_BLOCKED_PARSE_CONTEXT)));
        Exception e = assertThrows(Exception.class,
                () -> JsonPipesIpc.fromBytes(smile, FetchEmitTuple.class));
        assertTrue(root(e).contains("may not be supplied via a request parseContext"),
                "expected wire-blocked rejection at fork IPC, got: " + root(e));
    }

    private static String root(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return String.valueOf(r.getMessage());
    }
}
