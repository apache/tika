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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.UnpackConfig;

/**
 * The server configures {@code __tika-server} and {@code __unpack} against its own
 * spool directories. They exist on every running server, so unlike an unknown id these would
 * resolve if a /pipes or /async caller named them -- reading another request's pending upload,
 * or planting a file where the unpack download path serves from.
 * <p>
 * Tested directly rather than over HTTP: an endpoint test would have to run against a config
 * where these ids are actually wired up, and against any other config it passes for the wrong
 * reason (400 for "no such fetcher").
 */
public class ReservedComponentIdTest {

    /**
     * cxf-unpack-test-template.json has to spell the emitter id out -- JsonConfigHelper
     * substitutes textual values, never field names -- so renaming the constant without the
     * template surfaces only as "Archive is not a ZIP archive" from the unpack tests.
     */
    @Test
    public void testUnpackTemplateIdMatchesConstant() {
        assertEquals("__unpack", PipesParsingHelper.UNPACK_EMITTER_ID,
                "cxf-unpack-test-template.json hard-codes this id; update it too");
    }

    @Test
    public void testReservedFetcherRejected() {
        assertThrows(BadRequestException.class, () -> PipesParsingHelper.rejectReservedComponentIds(
                tuple(PipesParsingHelper.DEFAULT_FETCHER_ID, "my-emitter", null)));
    }

    @Test
    public void testReservedEmitterRejected() {
        assertThrows(BadRequestException.class, () -> PipesParsingHelper.rejectReservedComponentIds(
                tuple("my-fetcher", PipesParsingHelper.UNPACK_EMITTER_ID, null)));
    }

    /** The bytes emitter is a second, easily missed way to name an emitter. */
    @Test
    public void testReservedUnpackBytesEmitterRejected() {
        assertThrows(BadRequestException.class, () -> PipesParsingHelper.rejectReservedComponentIds(
                tuple("my-fetcher", "my-emitter", PipesParsingHelper.UNPACK_EMITTER_ID)));
    }

    @Test
    public void testCallerComponentsAllowed() {
        assertDoesNotThrow(() -> PipesParsingHelper.rejectReservedComponentIds(
                tuple("my-fetcher", "my-emitter", "my-bytes-emitter")));
    }

    private static FetchEmitTuple tuple(String fetcherId, String emitterId, String bytesEmitterId) {
        ParseContext parseContext = new ParseContext();
        if (bytesEmitterId != null) {
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(bytesEmitterId);
            parseContext.set(UnpackConfig.class, unpackConfig);
        }
        return new FetchEmitTuple("id", new FetchKey(fetcherId, "key"),
                new EmitKey(emitterId, "key"), new Metadata(), parseContext);
    }
}
