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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaObjectMapperFactory;

/** The strict mapper refuses unknown properties, so the pre-4.1 flag names must keep loading. */
public class UnpackConfigDeprecatedFlagsTest {

    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.createMapper();

    @Test
    public void testIncludeMetadataInZipStillLoads() throws Exception {
        UnpackConfig c = MAPPER.readValue("{\"includeMetadataInZip\": true}", UnpackConfig.class);
        assertTrue(c.writesMetadata());
        assertTrue(c.isIncludeMetadataInZip());
    }

    @Test
    public void testIncludeFullMetadataStillLoads() throws Exception {
        UnpackConfig c = MAPPER.readValue("{\"includeFullMetadata\": true}", UnpackConfig.class);
        assertTrue(c.writesMetadata());
        assertTrue(c.isIncludeFullMetadata());
    }

    @Test
    public void testOnlyTheNewNameIsWritten() throws Exception {
        UnpackConfig c = new UnpackConfig();
        c.setIncludeMetadata(true);
        String json = MAPPER.writeValueAsString(c);
        assertTrue(json.contains("\"includeMetadata\":true"), json);
        assertFalse(json.contains("includeMetadataInZip"), json);
        assertFalse(json.contains("includeFullMetadata"), json);
        assertTrue(MAPPER.readValue(json, UnpackConfig.class).writesMetadata());
        assertFalse(MAPPER.writeValueAsString(new UnpackConfig()).contains("includeMetadata"),
                "unset must not be written, or it would pin the format default");
    }

    /** Unset: a Frictionless package carries metadata, REGULAR sidecars are opt-in. */
    @Test
    public void testUnsetFollowsTheFormat() throws Exception {
        UnpackConfig c = MAPPER.readValue("{\"outputFormat\": \"FRICTIONLESS\"}", UnpackConfig.class);
        assertTrue(c.writesMetadata());
        assertFalse(MAPPER.readValue("{\"outputFormat\": \"REGULAR\"}", UnpackConfig.class).writesMetadata());
        assertFalse(new UnpackConfig().writesMetadata());

        UnpackConfig optOut = MAPPER.readValue(
                "{\"outputFormat\": \"FRICTIONLESS\", \"includeMetadata\": false}", UnpackConfig.class);
        assertFalse(optOut.writesMetadata(), "explicit false must beat the format default");
    }

    @Test
    public void testUnknownPropertyStillFails() {
        assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"includeMetadatta\": true}", UnpackConfig.class));
    }

    @Test
    public void testCopyIsFieldForField() {
        UnpackConfig c = new UnpackConfig();
        c.setIncludeMetadata(true);
        c.setIncludeOriginal(true);
        c.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        c.setOutputMode(UnpackConfig.OUTPUT_MODE.DIRECTORY);
        c.setEmitter("e");
        UnpackConfig copy = c.copy();
        assertEquals(c, copy);
        copy.setIncludeMetadata(false);
        assertTrue(c.writesMetadata(), "copy must not alias the original");
    }
}
