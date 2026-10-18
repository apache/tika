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

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * A tuple's parseContext is per-request configuration: with allowPerRequestConfig off it is
 * refused with the same 403 a multipart config part gets, whatever it carries, and an empty
 * one passes. {@link org.apache.tika.server.core.PipesPerRequestConfigDisabledTest} covers
 * the /pipes and /async routes.
 */
public class PerRequestConfigGateTest {

    @Test
    public void testParseContextRefusedWhenDisabled() {
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> PipesParsingHelper.rejectPerRequestConfig(tuple(perRequestConfig()), false));
        assertEquals(403, e.getResponse().getStatus());
    }

    @Test
    public void testEmptyParseContextAllowedWhenDisabled() {
        assertDoesNotThrow(() -> PipesParsingHelper.rejectPerRequestConfig(tuple(new ParseContext()), false));
    }

    @Test
    public void testParseContextAllowedWhenEnabled() {
        assertDoesNotThrow(() -> PipesParsingHelper.rejectPerRequestConfig(tuple(perRequestConfig()), true));
    }

    private static ParseContext perRequestConfig() {
        ParseContext parseContext = new ParseContext();
        parseContext.setJsonConfig("timeout-limits", "{\"progressTimeoutMillis\":100}");
        return parseContext;
    }

    private static FetchEmitTuple tuple(ParseContext parseContext) {
        return new FetchEmitTuple("id", new FetchKey("my-fetcher", "key"),
                new EmitKey("my-emitter", "key"), new Metadata(), parseContext);
    }
}
