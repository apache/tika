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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external2.ExternalParser;

/**
 * A request must never be able to submit a whole new {@link Parser}, {@link Detector} or
 * {@link EncodingDetector} via a serialized {@code ParseContext} (e.g. a tika-server
 * {@code /pipes} or {@code /async} request). These are command-injection / SSRF surfaces
 * (e.g. {@code ExternalParser.setCommandLine}, {@code TesseractOCRParser.setTesseractPath}).
 * Configuration of benign, non-component objects must still work.
 */
public class TestParseContextComponentInjection {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLASS_KEY = TikaJsonSerializer.INSTANTIATED_CLASS_KEY;

    /** A ParseContext "contents" node of the form { "&lt;className&gt;": { "_class": "&lt;className&gt;" } }. */
    private static JsonNode entry(String className) throws IOException {
        return MAPPER.readTree("{\"" + className + "\":{\"" + CLASS_KEY + "\":\"" + className + "\"}}");
    }

    private static boolean hasSecurityCause(Throwable t) {
        while (t != null) {
            if (t instanceof SecurityException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    @Test
    public void assertNotComponentRejectsParserDetectorEncodingDetector() {
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.assertNotComponent(ExternalParser.class));
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.assertNotComponent(EvilDetector.class));
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.assertNotComponent(EvilEncodingDetector.class));
    }

    @Test
    public void assertNotComponentAllowsBenignConfig() {
        assertDoesNotThrow(() -> TikaJsonDeserializer.assertNotComponent(BenignConfig.class));
    }

    @Test
    public void deserializeChokepointRejectsComponents() throws IOException {
        //this is exactly what the nested/recursive deserialization path invokes
        JsonNode empty = MAPPER.readTree("{}");
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.deserialize(ExternalParser.class, empty));
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.deserialize(EvilDetector.class, empty));
        assertThrows(SecurityException.class, () -> TikaJsonDeserializer.deserialize(EvilEncodingDetector.class, empty));
    }

    @Test
    public void readParseContextRejectsTopLevelParser() throws IOException {
        JsonNode node = entry(ExternalParser.class.getName());
        assertThrows(SecurityException.class, () -> ParseContextDeserializer.readParseContext(node));
    }

    @Test
    public void readParseContextRejectsTopLevelDetector() throws IOException {
        JsonNode node = entry(EvilDetector.class.getName());
        assertThrows(SecurityException.class, () -> ParseContextDeserializer.readParseContext(node));
    }

    @Test
    public void readParseContextRejectsTopLevelEncodingDetector() throws IOException {
        JsonNode node = entry(EvilEncodingDetector.class.getName());
        assertThrows(SecurityException.class, () -> ParseContextDeserializer.readParseContext(node));
    }

    @Test
    public void readParseContextRejectsNestedParser() throws IOException {
        //a benign holder whose Parser-typed setter is fed a nested Parser _class must be blocked
        //by the recursive chokepoint, not just the top level.
        String holder = Holder.class.getName();
        String evil = ExternalParser.class.getName();
        JsonNode node = MAPPER.readTree("{\"" + holder + "\":{\"" + CLASS_KEY + "\":\"" + holder + "\"," +
                "\"outputParser\":{\"" + CLASS_KEY + "\":\"" + evil + "\"}}}");
        Exception ex = assertThrows(Exception.class, () -> ParseContextDeserializer.readParseContext(node));
        assertTrue(hasSecurityCause(ex), "expected a SecurityException in the cause chain, got: " + ex);
    }

    @Test
    public void readParseContextAllowsBenignConfig() throws IOException {
        ParseContext pc = ParseContextDeserializer.readParseContext(entry(BenignConfig.class.getName()));
        assertNotNull(pc.get(BenignConfig.class));
    }

    // ---- test helpers ----

    public static class BenignConfig {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /** Non-component object with a {@link Parser}-typed setter, to exercise the nested/recursive guard. */
    public static class Holder {
        private Parser outputParser;

        public Parser getOutputParser() {
            return outputParser;
        }

        public void setOutputParser(Parser outputParser) {
            this.outputParser = outputParser;
        }
    }

    public static class EvilDetector implements Detector {
        @Override
        public MediaType detect(InputStream input, Metadata metadata) throws IOException {
            return null;
        }
    }

    public static class EvilEncodingDetector implements EncodingDetector {
        @Override
        public Charset detect(InputStream input, Metadata metadata) throws IOException {
            return null;
        }
    }
}
