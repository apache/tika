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
package org.apache.tika.pipes.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.reporter.fs.FileSystemJsonlReporterConfig;
import org.apache.tika.pipes.reporter.fs.FileSystemJsonlReporterFactory;

/**
 * Validates file system plugin configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testFileSystemFetcherConfig() throws Exception {
        loadAndValidate("file-system-fetcher.json");
    }

    @Test
    public void testFileSystemEmitterConfig() throws Exception {
        loadAndValidate("file-system-emitter.json");
    }

    @Test
    public void testFileSystemPipelineConfig() throws Exception {
        loadAndValidate("file-system-pipeline.json");
    }

    @Test
    public void testFileSystemJsonlReporterConfig() throws Exception {
        loadAndValidate("file-system-jsonl-reporter.json");

        JsonNode inner = innerComponent(readExample("file-system-jsonl-reporter.json"),
                "pipes-reporters", null, "file-system-jsonl-reporter");
        FileSystemJsonlReporterConfig config = FileSystemJsonlReporterConfig.load(inner.toString());
        assertEquals("/var/log/tika/pipes-audit.jsonl", config.path().toString());
        assertEquals(FileSystemJsonlReporterConfig.ON_EXISTS.EXCEPTION, config.onExists());
        assertEquals(10000, config.maxMessageLength());
        assertTrue(config.includes().contains("OOM"));
        assertNull(config.excludes());
    }

    // the plugin only resolves by name if the factory made it into pf4j's index
    @Test
    public void testJsonlReporterFactoryIsRegistered() throws Exception {
        // test-classes carries its own (empty) index that shadows main's, so scan them all
        StringBuilder all = new StringBuilder();
        for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/extensions.idx"))) {
            try (InputStream is = url.openStream()) {
                all.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertTrue(all.toString().contains(FileSystemJsonlReporterFactory.class.getName()), all.toString());
    }
}
