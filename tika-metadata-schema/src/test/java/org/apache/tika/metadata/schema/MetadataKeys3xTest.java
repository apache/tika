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
 * TIKA-4797 gate: regenerate the field-attributed table from the live {@link
 * org.apache.tika.metadata.Property} declarations and assert it matches the committed
 * {@code metadata-keys-3x.json}. Keeps the 3.x migration baseline from drifting.
 */
public class MetadataKeys3xTest {

    private static final String RESOURCE = "/org/apache/tika/metadata/metadata-keys-3x.json";

    @Test
    public void committedTableMatchesDeclarations() throws Exception {
        String committed;
        try (InputStream in = MetadataKeys3xTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "committed " + RESOURCE + " is missing");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(committed, SchemaGenerator.generate(),
                "metadata-keys-3x.json is stale. Run SchemaGenerator.main and commit the result.");
    }
}
