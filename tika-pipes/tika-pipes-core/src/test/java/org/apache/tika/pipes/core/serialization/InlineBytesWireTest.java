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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.fetcher.BytesFetcher;
import org.apache.tika.pipes.core.fetcher.InlineBytes;
import org.apache.tika.serialization.ParseContextUtils;

/**
 * The inline payload travels as a dedicated raw-binary field of the tuple, not through the
 * parse-context config machinery whose text-JSON round trip would base64 it.
 */
public class InlineBytesWireTest {

    private static byte[] payload(int size) {
        byte[] b = new byte[size];
        new Random(17).nextBytes(b);
        return b;
    }

    private static FetchEmitTuple tuple(byte[] payload) {
        ParseContext ctx = new ParseContext();
        ctx.set(InlineBytes.class, new InlineBytes(payload));
        ctx.set(ParseMode.class, ParseMode.RMETA);
        return new FetchEmitTuple("t", new FetchKey(BytesFetcher.FETCHER_ID, "doc.bin"),
                EmitKey.NO_EMIT, new Metadata(), ctx);
    }

    @Test
    public void ipcRoundTripPreservesPayload() throws Exception {
        byte[] payload = payload(100_000);
        FetchEmitTuple t = tuple(payload);
        byte[] wire = JsonPipesIpc.toBytes(t);
        // serializing must not strip the payload from the caller's live context
        assertNotNull(t.getParseContext().get(InlineBytes.class));

        FetchEmitTuple back = JsonPipesIpc.fromBytes(wire, FetchEmitTuple.class);
        // the child's path: merge, resolve, fetch
        ParseContext merged = new ParseContext();
        merged.copyFrom(back.getParseContext());
        ParseContextUtils.resolveAll(merged, getClass().getClassLoader());
        assertEquals(ParseMode.RMETA, merged.get(ParseMode.class));

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = new BytesFetcher().fetch("doc.bin", metadata, merged)) {
            assertArrayEquals(payload, tis.readAllBytes());
        }
        assertEquals("doc.bin", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void wireCarriesRawBinary() throws Exception {
        byte[] payload = payload(1_000_000);
        byte[] wire = JsonPipesIpc.toBytes(tuple(payload));
        // raw binary: no base64 (+33%) and no Smile 7-bit encoding (+14%)
        assertTrue(wire.length >= payload.length, "wire shorter than payload?");
        assertTrue(wire.length < payload.length + 1024,
                "payload not raw on the wire: " + wire.length + " bytes for " + payload.length);
    }

    @Test
    public void payloadBypassesParseContextConfigs() throws Exception {
        byte[] wire = JsonPipesIpc.toBytes(tuple(payload(1000)));
        FetchEmitTuple back = JsonPipesIpc.fromBytes(wire, FetchEmitTuple.class);
        assertFalse(back.getParseContext().hasJsonConfig("inline-bytes"),
                "payload leaked into the lazy-config path");
        assertNotNull(back.getParseContext().get(InlineBytes.class));
    }

    @Test
    public void requestBodyRejectsInlineBytes() {
        String json = "{\"id\":\"t\",\"fetcher\":\"f\",\"fetchKey\":\"k\"," +
                "\"emitter\":\"e\",\"inlineBytes\":\"QUJD\"}";
        Exception e = assertThrows(Exception.class,
                () -> JsonFetchEmitTuple.fromJson(new StringReader(json)));
        assertTrue(root(e).contains("reserved for the host's IPC"),
                "expected inlineBytes rejection, got: " + root(e));
    }

    private static String root(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return String.valueOf(r.getMessage());
    }
}
