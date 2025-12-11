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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FrameworkConfig.
 */
public class FrameworkConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testExtractDecoration() throws Exception {
        String json = """
            {
              "_mime-include": ["application/pdf"],
              "_mime-exclude": ["application/pdf+fdf"],
              "name": "test"
            }
                """;
        JsonNode node = MAPPER.readTree(json);

        FrameworkConfig config = FrameworkConfig.extract(node, MAPPER);

        assertNotNull(config.getDecoration(), "Decoration should be present");

        FrameworkConfig.ParserDecoration decoration = config.getDecoration();
        assertTrue(decoration.hasFiltering(), "Should have filtering");

        assertEquals(1, decoration.getMimeInclude().size());
        assertEquals("application/pdf", decoration.getMimeInclude().get(0));

        assertEquals(1, decoration.getMimeExclude().size());
        assertEquals("application/pdf+fdf", decoration.getMimeExclude().get(0));

        assertFalse(config.getComponentConfigJson().json().contains("_mime-include"),
                "Component config should not contain _mime-include");
        assertFalse(config.getComponentConfigJson().json().contains("_mime-exclude"),
                "Component config should not contain _mime-exclude");
    }

    @Test
    public void testNoDecoration() throws Exception {
        String json = """
            {
              "name": "test"
            }
                """;
        JsonNode node = MAPPER.readTree(json);

        FrameworkConfig config = FrameworkConfig.extract(node, MAPPER);

        assertNull(config.getDecoration(), "Decoration should be null");
    }

    @Test
    public void testMimeIncludeOnly() throws Exception {
        String json = """
            {
              "_mime-include": ["text/plain"],
              "name": "test"
            }
                """;
        JsonNode node = MAPPER.readTree(json);

        FrameworkConfig config = FrameworkConfig.extract(node, MAPPER);

        assertNotNull(config.getDecoration(), "Decoration should be present");
        assertEquals(1, config.getDecoration().getMimeInclude().size());
        assertTrue(config.getDecoration().getMimeExclude().isEmpty());
    }

    @Test
    public void testMimeExcludeOnly() throws Exception {
        String json = """
            {
              "_mime-exclude": ["image/jpeg"],
              "name": "test"
            }
                """;
        JsonNode node = MAPPER.readTree(json);

        FrameworkConfig config = FrameworkConfig.extract(node, MAPPER);

        assertNotNull(config.getDecoration(), "Decoration should be present");
        assertTrue(config.getDecoration().getMimeInclude().isEmpty());
        assertEquals(1, config.getDecoration().getMimeExclude().size());
    }

    @Test
    public void testComponentConfigJsonClean() throws Exception {
        String json = """
            {
              "_mime-include": ["text/plain"],
              "bufferSize": 1024,
              "enabled": true
            }
                """;
        JsonNode node = MAPPER.readTree(json);

        FrameworkConfig config = FrameworkConfig.extract(node, MAPPER);

        String componentJson = config.getComponentConfigJson().json();

        // Verify framework fields are removed
        assertFalse(componentJson.contains("_mime-include"), "Should not contain _mime-include");

        // Verify component fields remain
        assertTrue(componentJson.contains("bufferSize"), "Should contain bufferSize");
        assertTrue(componentJson.contains("enabled"), "Should contain enabled");
    }
}
