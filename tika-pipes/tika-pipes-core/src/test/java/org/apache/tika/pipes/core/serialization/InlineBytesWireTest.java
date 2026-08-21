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

import static org.apache.tika.pipes.core.serialization.WireTestUtil.root;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * The inline payload travels beside the tuple in the {@link PipesRequest} envelope -- never
 * inside the tuple or its serialized ParseContext, in any format.
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
        PipesRequest request = PipesRequest.of(t);
        // lifting the payload into the envelope must not mutate the caller's live context
        assertNotNull(t.getParseContext().get(InlineBytes.class));

        byte[] wire = JsonPipesIpc.toBytes(request);
        PipesRequest back = JsonPipesIpc.fromBytes(wire, PipesRequest.class);
        // the child's path: merge, resolve, plant, fetch
        ParseContext merged = new ParseContext();
        merged.copyFrom(back.getTuple().getParseContext());
        ParseContextUtils.resolveAll(merged, getClass().getClassLoader());
        back.applyTo(merged);
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
        byte[] wire = JsonPipesIpc.toBytes(PipesRequest.of(tuple(payload)));
        // raw binary: no base64 (+33%) and no Smile 7-bit encoding (+14%)
        assertTrue(wire.length >= payload.length, "wire shorter than payload?");
        assertTrue(wire.length < payload.length + 1024,
                "payload not raw on the wire: " + wire.length + " bytes for " + payload.length);
    }

    @Test
    public void payloadStaysOutOfTheTuple() throws Exception {
        byte[] wire = JsonPipesIpc.toBytes(PipesRequest.of(tuple(payload(1000))));
        PipesRequest back = JsonPipesIpc.fromBytes(wire, PipesRequest.class);
        assertFalse(back.getTuple().getParseContext().hasJsonConfig("inline-bytes"),
                "payload leaked into the lazy-config path");
        assertNull(back.getTuple().getParseContext().get(InlineBytes.class),
                "payload leaked into the tuple's context");
        assertNotNull(back.getInlineBytes());
    }

    @Test
    public void serializingATupleStillCarryingPayloadFailsLoudly() {
        // InlineBytes is deliberately unregistered: a tuple whose context still holds one has
        // no serialized form. PipesRequest.of is the only way onto the wire.
        Exception e = assertThrows(Exception.class, () -> JsonPipesIpc.toBytes(tuple(payload(10))));
        assertTrue(root(e).contains("Cannot serialize ParseContext entry"),
                "expected loud refusal, got: " + root(e));
        Exception text = assertThrows(Exception.class,
                () -> JsonFetchEmitTuple.toJson(tuple(payload(10))));
        assertTrue(root(text).contains("Cannot serialize ParseContext entry"),
                "expected loud refusal, got: " + root(text));
    }

    @Test
    public void requestBodyRejectsInlineBytes() {
        String json = "{\"id\":\"t\",\"fetcher\":\"f\",\"fetchKey\":\"k\"," +
                "\"emitter\":\"e\",\"inlineBytes\":\"QUJD\"}";
        Exception e = assertThrows(IOException.class,
                () -> JsonFetchEmitTuple.fromJson(new StringReader(json)));
        assertTrue(root(e).contains("not a FetchEmitTuple field"),
                "expected inlineBytes rejection, got: " + root(e));
    }

    @Test
    public void requestParseContextFormCannotBind() throws Exception {
        // Unregistered means the parse-context form is admitted as an inert config at most,
        // and resolution fails closed before anything binds.
        String json = "{\"id\":\"t\",\"fetcher\":\"f\",\"fetchKey\":\"k\",\"emitter\":\"e\"," +
                "\"parse-context\":{\"inline-bytes\":{\"bytes\":\"QUJD\"}}}";
        FetchEmitTuple t = JsonFetchEmitTuple.fromJson(new StringReader(json));
        ParseContext merged = new ParseContext();
        merged.copyFrom(t.getParseContext());
        Exception e = assertThrows(Exception.class,
                () -> ParseContextUtils.resolveAll(merged, getClass().getClassLoader()));
        assertTrue(root(e).contains("Unrecognized parse-context entry"),
                "expected fail-closed resolution, got: " + root(e));
        assertNull(merged.get(InlineBytes.class));
    }

    @Test
    public void unknownFieldErrorDoesNotAdvertiseInlineBytes() {
        String json = "{\"id\":\"t\",\"fetcher\":\"f\",\"fetchKey\":\"k\",\"emitter\":\"e\"," +
                "\"fetchKye\":\"typo\"}";
        Exception e = assertThrows(IOException.class,
                () -> JsonFetchEmitTuple.fromJson(new StringReader(json)));
        assertTrue(root(e).contains("Unrecognized"), "expected unknown-field error, got: " + root(e));
        assertFalse(root(e).contains("inlineBytes"),
                "IPC-only field advertised to requests: " + root(e));
    }
}
