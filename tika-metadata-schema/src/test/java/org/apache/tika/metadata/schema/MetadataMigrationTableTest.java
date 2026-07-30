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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The CI gate for the 3.x-&gt;4.x migration table (TIKA-4797 / TIKA-4799): regenerates it from the
 * committed field tables + overlay and asserts tika-core's bundled {@code metadata-migration-3x-4x.json}
 * has not drifted. The migration table is committed in tika-core (where the filter lives, and which is
 * Jackson-free); the inputs and this gate live here, alongside the other schema registries.
 */
public class MetadataMigrationTableTest {

    private static final String THREE_X = "/org/apache/tika/metadata/metadata-keys-3x.json";
    private static final String FOUR_X = "/org/apache/tika/metadata/metadata-key-fields.json";
    private static final String OVERLAY = "/org/apache/tika/metadata/migration-overlay.tsv";
    private static final String COMMITTED = "/org/apache/tika/metadata/metadata-migration-3x-4x.json";

    @Test
    public void committedMigrationTableMatchesRegeneration() throws Exception {
        String regenerated = MigrationTableGenerator.generate(read(THREE_X), read(FOUR_X), read(OVERLAY));
        assertEquals(read(COMMITTED), regenerated,
                "metadata-migration-3x-4x.json (in tika-core) is stale. Regenerate with "
                        + "MigrationTableGenerator and commit the result.");
    }

    private static String read(String resource) throws Exception {
        try (InputStream in = MetadataMigrationTableTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource on the test classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
