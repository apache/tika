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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

public class MetadataValueTypesTest {

    // keys are never declared, so only learn() can type them; each test uses its own
    private static final String PREFIX = "test-value-types-";

    @AfterEach
    public void reset() {
        JsonMetadataList.setTypedValues(false);
    }

    @Test
    public void testLearnedTypeIsWrittenTyped() throws Exception {
        String key = PREFIX + "learned:count";
        Metadata m = new Metadata();
        m.set(key, "7");
        JsonMetadataList.setTypedValues(true);
        assertEquals("7", toJson(m).get(key).textValue());

        MetadataValueTypes.learn(key, "INTEGER");
        assertEquals(7, toJson(m).get(key).intValue());
    }

    @Test
    public void testLocalDeclarationWins() {
        String title = TikaCoreProperties.TITLE.getName();
        MetadataValueTypes.learn(title, "INTEGER");
        assertEquals(Property.ValueType.TEXT, MetadataValueTypes.get(title));
    }

    @Test
    public void testFirstLearnedWins() {
        String key = PREFIX + "first:flag";
        MetadataValueTypes.learn(key, "BOOLEAN");
        MetadataValueTypes.learn(key, "INTEGER");
        assertEquals(Property.ValueType.BOOLEAN, MetadataValueTypes.get(key));
    }

    @Test
    public void testUntypedAndUnknownTypesIgnored() {
        for (String type : new String[]{"TEXT", "DATE", "nope", ""}) {
            String key = PREFIX + "ignored:" + type;
            MetadataValueTypes.learn(key, type);
            assertNull(MetadataValueTypes.get(key), type);
        }
    }

    @Test
    public void testDescribeReportsOnlyTypedKeys() {
        Metadata m = new Metadata();
        m.set(Audio.CHANNELS, "2");
        m.set(TikaCoreProperties.TITLE, "t");
        m.set(PREFIX + "undeclared", "3");
        assertEquals(Map.of(Audio.CHANNELS.getName(), Property.ValueType.INTEGER),
                MetadataValueTypes.describe(List.of(m)));
    }

    private static JsonNode toJson(Metadata m) throws Exception {
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(List.of(m), writer);
        return new ObjectMapper().readTree(writer.toString()).get(0);
    }
}
