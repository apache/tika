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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

/**
 * A request-supplied (wire) ParseContext may <em>configure</em> a parse but never
 * <em>introduce</em> an executable/IO component (parser, detector, renderer, ...). These tests pin
 * that boundary at the deserializer.
 */
public class WireRestrictedParseContextTest {

    /** Fresh mapper with the restricted ParseContext deserializer (untrusted/wire mode). */
    private ObjectMapper restrictedMapper() {
        ObjectMapper mapper = TikaObjectMapperFactory.createMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer(true));
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    public void everyContextKeyInterfaceIsClassifiedExactlyOnce() {
        Set<Class<?>> all = ComponentNameResolver.getContextKeyInterfaces();
        Set<Class<?>> allowed = ComponentNameResolver.getWireInstantiableContextKeys();
        Set<Class<?>> blocked = ComponentNameResolver.getWireBlockedContextKeys();
        for (Class<?> c : all) {
            assertTrue(allowed.contains(c) ^ blocked.contains(c),
                    c.getName() + " must be classified as exactly one of wire-allowed or wire-blocked");
        }
        Set<Class<?>> union = new HashSet<>(allowed);
        union.addAll(blocked);
        assertEquals(all, union, "wire-allowed union wire-blocked must equal CONTEXT_KEY_INTERFACES");
    }

    @Test
    public void restrictedRejectsTypedParserInjection() {
        // The historical RCE payload (external-parser under a "typed" wrapper); the nested scan
        // refuses it regardless of the wrapper key.
        String json = "{\"typed\":{\"external-parser\":{\"config\":{" +
                "\"commandLine\":[\"/bin/sh\",\"-c\",\"echo pwned\"]," +
                "\"supportedTypes\":[\"text/plain\"]}}}}";
        Exception e = assertThrows(Exception.class,
                () -> restrictedMapper().readValue(json, ParseContext.class));
        assertTrue(rootMessage(e).contains("may not be supplied via a request parseContext"),
                "expected wire-blocked rejection, got: " + rootMessage(e));
    }

    @Test
    public void restrictedAllowsFlatSelfConfiguringParserConfig() throws Exception {
        // Per-request tuning of an already-loaded self-configuring parser is config, not
        // instantiation: stored as an inert jsonConfig, never bound, so it must be allowed
        // (mirrors the real pdf-parser sortByPosition / OCR-strategy use case).
        String json = "{\"configurable-test-parser\":{\"maxItems\":5}}";
        ParseContext ctx = restrictedMapper().readValue(json, ParseContext.class);
        assertTrue(ctx.hasJsonConfig("configurable-test-parser"));
    }

    @Test
    public void restrictedRejectsNestedParserObject() {
        // A blocked parser nested inside an allowed component's config (compact object form).
        String json = "{\"basic-content-handler-factory\":{\"nested\":{\"external-parser\":{" +
                "\"config\":{\"commandLine\":[\"/bin/sh\",\"-c\",\"echo pwned\"]}}}}}";
        Exception e = assertThrows(Exception.class,
                () -> restrictedMapper().readValue(json, ParseContext.class));
        assertTrue(rootMessage(e).contains("may not be supplied via a request parseContext"),
                "expected nested wire-blocked rejection, got: " + rootMessage(e));
    }

    @Test
    public void keysOnlyScanLetsInertBareStringReferencesThrough() {
        // A bare-string reference (compact "defaults" form, no config) is deliberately NOT scanned:
        // it cannot carry a commandLine and is never bound-and-invoked. Pins that decision so we
        // don't regress to scanning arbitrary string values (false positives).
        String json = "{\"metadata-filters\":[\"external-parser\"]}";
        assertDoesNotThrow(() -> restrictedMapper().readValue(json, ParseContext.class));
    }

    @Test
    public void restrictedRejectsParserSmuggledInArrayConfig() {
        // Nesting a parser inside an array config (e.g. metadata-filters) must also be refused.
        String json = "{\"metadata-filters\":[{\"external-parser\":{}}]}";
        Exception e = assertThrows(Exception.class,
                () -> restrictedMapper().readValue(json, ParseContext.class));
        assertTrue(rootMessage(e).contains("may not be supplied via a request parseContext"),
                "expected array-nested wire-blocked rejection, got: " + rootMessage(e));
    }

    @Test
    public void restrictedAllowsSafeConfig() throws Exception {
        // Allowed component (metadata filter) + bounded config DTOs (handler, timeout) must pass.
        String json = "{\"metadata-filters\":[\"mock-upper-case-filter\"]," +
                "\"basic-content-handler-factory\":{\"type\":\"XML\",\"writeLimit\":1000}," +
                "\"timeout-limits\":{\"progressTimeoutMillis\":5000,\"totalTaskTimeoutMillis\":60000}}";
        ParseContext ctx = restrictedMapper().readValue(json, ParseContext.class);
        assertNotNull(ctx);
        assertTrue(ctx.hasJsonConfig("metadata-filters"));
        assertTrue(ctx.hasJsonConfig("timeout-limits"));
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return String.valueOf(r.getMessage());
    }
}
