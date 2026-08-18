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
 * A request must not be able to name a {@code __} component -- that is what keeps /pipes and
 * /async away from the fetchers and emitters tika-server wires up for /tika and /unpack. The
 * parent-to-child IPC builds those same tuples itself and must still be able to name them.
 */
public class SystemComponentIdWireTest {

    private static String tuple(String fetcherId, String emitterId) {
        return "{\"id\":\"t\",\"fetcher\":\"" + fetcherId + "\",\"fetchKey\":\"k\"," +
                "\"emitter\":\"" + emitterId + "\",\"emitKey\":\"ek\"}";
    }

    /**
     * The surrounding-whitespace cases are the point of normalizing before validating: checking
     * the raw id and resolving the trimmed one would let these bind the component they name.
     */
    @Test
    public void requestCannotNameSystemFetcher() {
        for (String fetcherId : new String[]{"__tika-server", " __tika-server", "__tika-server ",
                "\\t__unpack"}) {
            Exception e = assertThrows(Exception.class,
                    () -> JsonFetchEmitTuple.fromJson(new StringReader(tuple(fetcherId, "e"))),
                    "should have rejected fetcher id '" + fetcherId + "'");
            assertTrue(root(e).contains("is reserved"),
                    "expected reserved rejection for '" + fetcherId + "', got: " + root(e));
        }
    }

    @Test
    public void requestCannotNameSystemEmitter() {
        for (String emitterId : new String[]{"__unpack", " __unpack"}) {
            Exception e = assertThrows(Exception.class,
                    () -> JsonFetchEmitTuple.fromJson(new StringReader(tuple("f", emitterId))),
                    "should have rejected emitter id '" + emitterId + "'");
            assertTrue(root(e).contains("is reserved"),
                    "expected reserved rejection for '" + emitterId + "', got: " + root(e));
        }
    }

    @Test
    public void asyncEndpointCannotNameSystemFetcher() {
        Exception e = assertThrows(Exception.class, () -> JsonFetchEmitTupleList.fromJson(
                new StringReader("{\"tuples\":[" + tuple("__tika-server", "e") + "]}")));
        assertTrue(root(e).contains("is reserved"), "expected reserved rejection, got: " + root(e));
    }

    /**
     * The NBSP case is why the rule is an allowlist rather than a trim: trim() and strip() both
     * leave U+00A0, so such an id would be neither reserved nor resolvable.
     */
    @Test
    public void requestCannotUseIllegalIdCharacters() {
        for (String fetcherId : new String[]{"my fetcher", "../etc", "a/b", "f\u00A0oo", "f:oo"}) {
            Exception e = assertThrows(Exception.class,
                    () -> JsonFetchEmitTuple.fromJson(new StringReader(tuple(fetcherId, "e"))),
                    "should have rejected fetcher id '" + fetcherId + "'");
            assertTrue(root(e).contains("Illegal"),
                    "expected charset rejection for '" + fetcherId + "', got: " + root(e));
        }
    }

    @Test
    public void legalIdsAreTrimmed() throws Exception {
        FetchEmitTuple t = JsonFetchEmitTuple.fromJson(new StringReader(tuple(" my-fetcher ", " e ")));
        assertEquals("my-fetcher", t.getFetchKey().getFetcherId());
        assertEquals("e", t.getEmitKey().getEmitterId());
    }

    @Test
    public void internalIpcMayNameSystemComponents() throws Exception {
        FetchEmitTuple t = new FetchEmitTuple("t", new FetchKey("__tika-server", "k"),
                new EmitKey("__unpack", "ek"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        FetchEmitTuple back = JsonPipesIpc.fromBytes(JsonPipesIpc.toBytes(t), FetchEmitTuple.class);
        assertEquals("__tika-server", back.getFetchKey().getFetcherId());
        assertEquals("__unpack", back.getEmitKey().getEmitterId());
    }

    @Test
    public void internalIpcStillRejectsIllegalIdCharacters() throws Exception {
        byte[] smile = new ObjectMapper(new SmileFactory())
                .writeValueAsBytes(new ObjectMapper().readTree(tuple("a/b", "e")));
        Exception e = assertThrows(Exception.class,
                () -> JsonPipesIpc.fromBytes(smile, FetchEmitTuple.class));
        assertTrue(root(e).contains("Illegal"), "expected charset rejection, got: " + root(e));
    }

    private static String root(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append(' ');
        }
        return sb.toString();
    }
}
