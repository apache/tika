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
package org.apache.tika.metadata.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The CI gate: regenerate the registry from the live {@link org.apache.tika.metadata.Property}
 * declarations and assert it matches the committed {@code metadata-keys.json}. If a Property is
 * added/changed/renamed without regenerating, this fails — the registry cannot silently drift.
 */
public class MetadataSchemaTest {

    private static final String RESOURCE = "/org/apache/tika/metadata/metadata-keys.json";

    @Test
    public void committedRegistryMatchesDeclarations() throws Exception {
        String committed;
        try (InputStream in = MetadataSchemaTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "committed " + RESOURCE + " is missing");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String regenerated = SchemaGenerator.generate();
        assertEquals(committed, regenerated,
                "metadata-keys.json is stale. Run `mvn -pl tika-metadata-schema exec:java` "
                        + "(or SchemaGenerator.main) and commit the regenerated file.");
    }
}
