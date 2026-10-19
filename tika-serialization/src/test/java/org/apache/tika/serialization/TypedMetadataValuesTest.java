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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

public class TypedMetadataValuesTest {

    private static final String CHUNKS = TikaCoreProperties.TIKA_CHUNKS.getName();
    private static final Property TEST_JSON = Property.internalJson("test-typed:json");
    // number text a double would rewrite
    private static final String CHUNKS_JSON =
            "[{\"text\":\"a \\\"b\\\"\",\"x\":1.50,\"y\":1e-5,\"z\":12345678901234567890123," +
                    "\"locators\":{\"text\":[{\"start\":0,\"end\":5}]}}]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    @AfterEach
    public void reset() {
        setTyped(false);
    }

    @Test
    public void testOffByDefault() throws Exception {
        Metadata m = chunks(CHUNKS_JSON);
        m.set(Audio.CHANNELS, "2");
        JsonNode node = MAPPER.readTree(toJsonList(m)).get(0);
        assertEquals(CHUNKS_JSON, node.get(CHUNKS).textValue());
        assertEquals("2", node.get(Audio.CHANNELS.getName()).textValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testChunksAreJson(boolean pretty) throws Exception {
        setTyped(true);
        Metadata m = chunks(CHUNKS_JSON);
        String json = toJsonList(m, pretty);
        JsonNode chunks = MAPPER.readTree(json).get(0).get(CHUNKS);
        assertTrue(chunks.isArray(), json);
        assertEquals("a \"b\"", chunks.get(0).get("text").textValue());
        assertTrue(json.contains("1.50") && json.contains("1e-5"), "number text kept: " + json);

        Metadata back = JsonMetadataList.fromJson(new StringReader(json)).get(0);
        assertEquals(CHUNKS_JSON, back.get(CHUNKS));
    }

    @Test
    public void testSingleMetadataChunksAreJson() throws Exception {
        setTyped(true);
        StringWriter writer = new StringWriter();
        JsonMetadata.toJson(chunks(CHUNKS_JSON), writer);
        assertTrue(MAPPER.readTree(writer.toString()).get(CHUNKS).isArray());
        assertEquals(CHUNKS_JSON,
                JsonMetadata.fromJson(new StringReader(writer.toString())).get(CHUNKS));
    }

    @Test
    public void testNumbersAndBooleans() throws Exception {
        setTyped(true);
        Metadata m = new Metadata();
        m.set(Audio.CHANNELS, "2");
        m.set(Geographic.LATITUDE, "-12.50");
        m.set(Audio.HAS_DRM, "true");
        JsonNode node = MAPPER.readTree(toJsonList(m)).get(0);
        assertTrue(node.get(Audio.CHANNELS.getName()).isIntegralNumber());
        assertTrue(node.get(Geographic.LATITUDE.getName()).isNumber());
        assertTrue(node.get(Audio.HAS_DRM.getName()).isBoolean());
        assertRoundTrips(m);
    }

    /** A value malformed for its type stays a string rather than becoming invalid JSON. */
    @ParameterizedTest
    @ValueSource(strings = {"abc", "0042", "1.", "+1", "NaN", "Infinity", "1,5", " 1", ""})
    public void testMalformedNumbersStayStrings(String value) throws Exception {
        setTyped(true);
        Metadata m = new Metadata();
        m.set(Audio.CHANNELS, value);
        m.set(Geographic.LATITUDE, value);
        m.set(Audio.HAS_DRM, value);
        JsonNode node = MAPPER.readTree(toJsonList(m)).get(0);
        assertTrue(node.get(Audio.CHANNELS.getName()).isTextual());
        assertTrue(node.get(Geographic.LATITUDE.getName()).isTextual());
        assertTrue(node.get(Audio.HAS_DRM.getName()).isTextual());
        assertRoundTrips(m);
    }

    /**
     * Each would read back as something other than the one value written: a scalar or an
     * array of scalars looks like an ordinary value or values; the rest are not one document.
     */
    @ParameterizedTest
    @ValueSource(strings = {"[\"a\",\"b\"]", "[]", "[1,2]", "\"a\"", "5", "null", "{\"a\":",
            "{} {}", "not json", ""})
    public void testJsonThatWouldNotRoundTripStaysAString(String value) throws Exception {
        setTyped(true);
        JsonNode node = MAPPER.readTree(toJsonList(chunks(value))).get(0);
        assertTrue(node.get(CHUNKS).isTextual(), value);
        assertRoundTrips(chunks(value));
    }

    @Test
    public void testMultipleJsonValuesStayStrings() throws Exception {
        setTyped(true);
        Metadata m = new Metadata();
        m.add(TEST_JSON.getName(), CHUNKS_JSON);
        m.add(TEST_JSON.getName(), "{\"a\":1}");
        JsonNode node = MAPPER.readTree(toJsonList(m)).get(0).get(TEST_JSON.getName());
        assertTrue(node.isArray() && node.get(0).isTextual() && node.get(1).isTextual());
        assertRoundTrips(m);
    }

    /** Deeper than the reader's nesting limit allows stays a string, so fromJson still works. */
    @Test
    public void testTooDeepStaysAString() throws Exception {
        setTyped(true);
        int max = JsonMetadataList.getStreamReadConstraints().getMaxNestingDepth();
        // list + metadata object leave max - 2 for the value
        String fits = "[".repeat(max - 3) + "{}" + "]".repeat(max - 3);
        String tooDeep = "[" + fits + "]";
        assertTrue(MAPPER.readTree(toJsonList(chunks(fits))).get(0).get(CHUNKS).isArray());
        assertTrue(MAPPER.readTree(toJsonList(chunks(tooDeep))).get(0).get(CHUNKS).isTextual());
        assertRoundTrips(chunks(fits));
        assertRoundTrips(chunks(tooDeep));
    }

    /** Typed output from a writer reads back the same whatever the keys and values. */
    @Test
    public void testRandomRoundTrip() throws Exception {
        long seed = System.nanoTime();
        Random random = new Random(seed);
        String[] keys = {TEST_JSON.getName(), Audio.CHANNELS.getName(), Geographic.LATITUDE.getName(),
                Audio.HAS_DRM.getName(), "custom:key"};
        String[] values = {CHUNKS_JSON, "{\"a\":[1,{\"b\":null}]}", "[[1],2]", "[\"x\"]", "[]",
                "{}", "0", "-0.5e+3", "007", "true", "false", "TRUE", "", "a", "null", "\"q\"",
                "1.0E-5"};
        for (boolean typed : new boolean[]{false, true}) {
            setTyped(typed);
            for (int i = 0; i < 200; i++) {
                Metadata m = random.nextBoolean() ? chunks(values[random.nextInt(values.length)])
                        : new Metadata();
                for (String key : keys) {
                    int n = random.nextInt(3);
                    for (int j = 0; j < n; j++) {
                        m.add(key, values[random.nextInt(values.length)]);
                    }
                }
                assertRoundTrips(m, "seed=" + seed);
            }
        }
    }

    @Test
    public void testConfig() throws Exception {
        int depth = JsonMetadataList.getStreamReadConstraints().getMaxNestingDepth();
        TikaLoader.load(config("{\"metadata-list\": {\"typedValues\": true}}"));
        assertTrue(JsonMetadataList.isTypedValues());
        assertTrue(JsonMetadata.isTypedValues());
        // typedValues alone leaves the read limits as they were
        assertEquals(depth, JsonMetadataList.getStreamReadConstraints().getMaxNestingDepth());

        TikaLoader.load(config("{\"metadata-list\": {\"typedValues\": false}}"));
        assertFalse(JsonMetadataList.isTypedValues());
        assertFalse(JsonMetadata.isTypedValues());

        assertThrows(TikaConfigException.class,
                () -> TikaLoader.load(config("{\"metadata-list\": {\"typedValues\": \"yes\"}}")));
    }

    private Path config(String json) throws Exception {
        Path p = Files.createTempFile(tmp, "tika-config", ".json");
        Files.writeString(p, json, StandardCharsets.UTF_8);
        return p;
    }

    private static void setTyped(boolean typed) {
        JsonMetadata.setTypedValues(typed);
        JsonMetadataList.setTypedValues(typed);
    }

    private static Metadata chunks(String value) {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.TIKA_CHUNKS, value);
        return m;
    }

    private static String toJsonList(Metadata m) throws Exception {
        return toJsonList(m, false);
    }

    private static String toJsonList(Metadata m, boolean pretty) throws Exception {
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(List.of(m), writer, pretty);
        return writer.toString();
    }

    private static void assertRoundTrips(Metadata m) throws Exception {
        assertRoundTrips(m, "");
    }

    private static void assertRoundTrips(Metadata m, String message) throws Exception {
        for (boolean pretty : new boolean[]{false, true}) {
            String json = toJsonList(m, pretty);
            Metadata back = JsonMetadataList.fromJson(new StringReader(json)).get(0);
            assertArrayEquals(m.names().length == 0 ? new String[0] : sorted(m.names()),
                    sorted(back.names()), message + " " + json);
            for (String name : m.names()) {
                assertArrayEquals(m.getValues(name), back.getValues(name),
                        message + " " + name + " " + json);
            }

            StringWriter writer = new StringWriter();
            JsonMetadata.toJson(m, writer);
            Metadata single = JsonMetadata.fromJson(new StringReader(writer.toString()));
            for (String name : m.names()) {
                assertArrayEquals(m.getValues(name), single.getValues(name),
                        message + " " + name + " " + writer);
            }
        }
    }

    private static String[] sorted(String[] names) {
        String[] copy = names.clone();
        Arrays.sort(copy);
        return copy;
    }
}
