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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.loader.MinimalTestParser;
import org.apache.tika.config.loader.RequestConfigTestParser;
import org.apache.tika.config.loader.RequestConfigTestSubParser;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

/** Which self-configuring components read a parse-context block of their own. */
public class RequestConfigurableTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAcceptsBlockNobodyReads(boolean restricted) throws Exception {
        // warned about, not refused, until 5.0
        ParseContext context = ParseContextDeserializer.readParseContext(
                json("{\"minimal-test-parser\":{}}"), restricted);
        assertTrue(context.hasJsonConfig("minimal-test-parser"));
    }

    @Test
    public void testOnlyAnnotatedSelfConfiguringComponentsRead() {
        assertFalse(readsBlock("minimal-test-parser"));
        assertTrue(readsBlock("request-config-test-parser"));
        assertTrue(readsBlock("request-config-test-sub-parser"));
        // not self-configuring: resolved into the context, so always read
        assertTrue(readsBlock("timeout-limits"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAppliesBlockForReader(boolean restricted) throws Exception {
        ParseContext context = ParseContextDeserializer.readParseContext(
                json("{\"request-config-test-parser\":{\"maxItems\":7}}"), restricted);
        assertEquals("7", parse(new RequestConfigTestParser(), context).get("maxItems"));
    }

    @Test
    public void testSubclassInheritsAnnotation() throws Exception {
        ParseContext context = ParseContextDeserializer.readParseContext(
                json("{\"request-config-test-sub-parser\":{\"maxItems\":3}}"), true);
        assertEquals("3", parse(new RequestConfigTestSubParser(), context).get("maxItems"));
    }

    @Test
    public void testConfigFileParseContextAcceptsBlockNobodyReads(@TempDir Path tmp) throws Exception {
        Path config = Files.writeString(tmp.resolve("tika-config.json"),
                "{\"parse-context\":{\"minimal-test-parser\":{}}}", StandardCharsets.UTF_8);
        assertTrue(TikaLoader.load(config).loadParseContext().hasJsonConfig("minimal-test-parser"));
    }

    @Test
    public void testReadingWithoutAnnotationFailsUnderAssertions() {
        assumeTrue(ConfigDeserializer.class.desiredAssertionStatus());
        ParseContext context = new ParseContext();
        context.setJsonConfig("minimal-test-parser", "{}");
        AssertionError e = assertThrows(AssertionError.class,
                () -> ConfigDeserializer.getConfig(context, "minimal-test-parser", MinimalTestParser.class));
        assertTrue(e.getMessage().contains("RequestConfigurable"), e.getMessage());
    }

    private static boolean readsBlock(String name) {
        return ComponentNameResolver.readsParseContextConfig(
                ComponentNameResolver.getComponentInfo(name).orElseThrow());
    }

    private static JsonNode json(String s) throws IOException {
        return MAPPER.readTree(s);
    }

    private static Metadata parse(Parser parser, ParseContext context) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            parser.parse(tis, new DefaultHandler(), metadata, context);
        }
        return metadata;
    }
}
