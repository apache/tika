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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NestingTestFilter;
import org.apache.tika.metadata.filter.OperatorOnlyClassTestFilter;
import org.apache.tika.metadata.filter.OperatorOnlyJsonConfigTestFilter;
import org.apache.tika.metadata.filter.OperatorOnlySetterTestFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

/**
 * Per-request JSON may not set an {@code @OperatorOnly} property or name an
 * {@code @OperatorOnly} class, however the component is reached: a {@code metadata-filters}
 * entry, a top-level entry, a nested property, or a bare-string reference.
 */
public class OperatorOnlyComponentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ParseContext resolve(String json, boolean trusted) throws Exception {
        ParseContext context = ParseContextDeserializer.readParseContext(MAPPER.readTree(json), !trusted);
        ParseContextUtils.resolveAll(context, OperatorOnlyComponentTest.class.getClassLoader());
        return context;
    }

    /** The reason must reach the top-level message: that is all a server client sees. */
    private static void assertRefused(String json) {
        Exception e = assertThrows(Exception.class, () -> resolve(json, false), json);
        String message = e.getMessage();
        assertTrue(message.contains("Cannot modify") || message.contains("may not be supplied via a request"),
                json + ": expected an operator-only refusal, got: " + messages(e));
    }

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    private static MetadataFilter onlyFilter(ParseContext context) {
        MetadataFilter filter = context.get(MetadataFilter.class);
        if (filter instanceof CompositeMetadataFilter composite) {
            assertEquals(1, composite.getFilters().size());
            return composite.getFilters().get(0);
        }
        return filter;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"metadata-filters\":[{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}]}",
            "{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}",
            "{\"metadata-filters\":[{\"operator-only-json-config-test-filter\":{\"endpoint\":\"http://x\"}}]}",
            "{\"metadata-filters\":[\"operator-only-class-test-filter\"]}",
            "{\"metadata-filters\":[{\"operator-only-class-test-filter\":{\"label\":\"a\"}}]}",
            "{\"operator-only-class-test-filter\":{\"label\":\"a\"}}",
            "{\"metadata-filters\":[{\"nesting-test-filter\":{\"delegate\":"
                    + "{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}}}]}",
            "{\"metadata-filters\":[{\"nesting-test-filter\":{\"delegate\":\"operator-only-class-test-filter\"}}]}",
            "{\"metadata-filters\":[{\"nesting-test-filter\":{\"parser\":\"external-parser\"}}]}",
            "{\"metadata-filters\":[{\"operator-only-json-config-test-filter\":"
                    + "{\"delegate\":\"operator-only-class-test-filter\"}}]}"
    })
    public void untrustedIsRefused(String json) {
        assertRefused(json);
    }

    /** A self-configuring component's runtime config with a component-typed property. */
    public static class ComponentHoldingConfig {
        private MetadataFilter delegate;

        public MetadataFilter getDelegate() {
            return delegate;
        }

        public void setDelegate(MetadataFilter delegate) {
            this.delegate = delegate;
        }
    }

    private static ComponentHoldingConfig runtimeConfig(String json, boolean trusted,
                                                        ComponentHoldingConfig defaults) throws IOException {
        ParseContext context = new ParseContext();
        context.setJsonConfig("holding-parser", json, trusted);
        return ConfigDeserializer.getConfig(context, "holding-parser", ComponentHoldingConfig.class, defaults);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"delegate\":\"operator-only-class-test-filter\"}",
            "{\"delegate\":{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}}"
    })
    public void runtimeConfigCarriesTrustIntoNestedComponents(String json) throws Exception {
        for (ComponentHoldingConfig defaults : new ComponentHoldingConfig[]{null, new ComponentHoldingConfig()}) {
            IOException e = assertThrows(IOException.class, () -> runtimeConfig(json, false, defaults), json);
            assertTrue(messages(e).contains("Cannot modify") || messages(e).contains("may not be supplied"),
                    messages(e));
            assertDoesNotThrow(() -> runtimeConfig(json, true, defaults), json);
        }
        assertTrue(runtimeConfig("{\"delegate\":\"operator-only-class-test-filter\"}", true, null)
                .getDelegate() instanceof OperatorOnlyClassTestFilter);
    }

    @Test
    public void untrustedMaySetOrdinaryProperties() throws Exception {
        ParseContext context = resolve(
                "{\"metadata-filters\":[{\"operator-only-setter-test-filter\":{\"label\":\"a\"}}]}", false);
        assertEquals("a", ((OperatorOnlySetterTestFilter) onlyFilter(context)).getLabel());
        assertDoesNotThrow(() -> resolve("{\"metadata-filters\":[{\"nesting-test-filter\":{\"delegate\":"
                + "{\"operator-only-setter-test-filter\":{\"label\":\"a\"}}}}]}", false));
    }

    @Test
    public void javaCodeDeclaresTrust() throws Exception {
        String json = "{\"endpoint\":\"http://x\"}";
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new OperatorOnlyJsonConfigTestFilter(() -> json));
        assertTrue(e.getMessage().contains("OperatorOnlyJsonConfigTestFilter$Config"), e.getMessage());
        assertTrue(e.getMessage().contains("JsonConfig.trusted(json)"), e.getMessage());
        assertEquals("http://x", new OperatorOnlyJsonConfigTestFilter(JsonConfig.trusted(json)).getEndpoint());
    }

    @Test
    public void trustedMaySetEverything() throws Exception {
        ParseContext context = resolve(
                "{\"metadata-filters\":[{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}]}", true);
        assertEquals("http://x", ((OperatorOnlySetterTestFilter) onlyFilter(context)).getEndpoint());

        context = resolve("{\"metadata-filters\":"
                + "[{\"operator-only-json-config-test-filter\":{\"endpoint\":\"http://x\"}}]}", true);
        assertEquals("http://x", ((OperatorOnlyJsonConfigTestFilter) onlyFilter(context)).getEndpoint());

        assertDoesNotThrow(() -> resolve("{\"metadata-filters\":[\"operator-only-class-test-filter\"]}", true));

        context = resolve("{\"metadata-filters\":[{\"nesting-test-filter\":{\"delegate\":"
                + "{\"operator-only-setter-test-filter\":{\"endpoint\":\"http://x\"}}}}]}", true);
        NestingTestFilter nesting = (NestingTestFilter) onlyFilter(context);
        assertEquals("http://x", ((OperatorOnlySetterTestFilter) nesting.getDelegate()).getEndpoint());
    }
}
