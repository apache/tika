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
package org.apache.tika.pipes;

import static org.apache.tika.TikaTest.assertContains;
import static org.apache.tika.TikaTest.debug;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class PipesClientTest {

    @Test
    public void testMetadataWithNoEmitter(@TempDir Path tmp) throws Exception {
        //this tests that metadata gathered by the fetcher and user metadata
        //comes through into the final set of metadata.

        Path tikaConfigTemplate = Paths.get(PipesClientTest.class.getResource("TIKA-4252.xml").toURI());
        Path tikaConfig = tmp.resolve("tika-config.xml");
        String xml = Files.readString(tikaConfigTemplate, StandardCharsets.UTF_8);
        xml = xml.replace("BASE_PATH",
                Paths.get(PipesClientTest.class.getResource("/test-documents").toURI()).toAbsolutePath().toString());
        Files.writeString(tikaConfig, xml);

        List<Metadata> metadataList;
        try (PipesClient pipesClient = new PipesClient(PipesConfig.load(tikaConfig))) {
            FetchKey fetchKey = new FetchKey("fs", "mock_times.xml");
            Metadata userMetadata = new Metadata();
            userMetadata.set("k1", "v1");
            userMetadata.add("k2", "v2a");
            userMetadata.add("k2", "v2b");
            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple("my-id", fetchKey, new EmitKey(), userMetadata, HandlerConfig.DEFAULT_HANDLER_CONFIG, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            metadataList = pipesResult
                    .getEmitData()
                    .getMetadataList();
        }
        assertEquals("application/mock+xml", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertContains("hello", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("v1", metadataList.get(0).get("k1"));
        assertEquals("v2a", metadataList.get(0).getValues("k2")[0]);
        assertEquals("v2b", metadataList.get(0).getValues("k2")[1]);

        //confirm that metadata from the fetcher is making it into the final result
        assertNotNull(metadataList.get(0).get(FileSystem.CREATED));
    }
}
