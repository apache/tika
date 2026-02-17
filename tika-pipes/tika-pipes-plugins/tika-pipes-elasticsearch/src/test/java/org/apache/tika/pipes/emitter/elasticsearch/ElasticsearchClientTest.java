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
package org.apache.tika.pipes.emitter.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;

public class ElasticsearchClientTest extends TikaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSerialization() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("authors", "author1");
        metadata.add("authors", "author2");
        metadata.add("title", "title1");
        for (ElasticsearchEmitterConfig.AttachmentStrategy strategy :
                ElasticsearchEmitterConfig.AttachmentStrategy.values()) {
            String json =
                    ElasticsearchClient.metadataToJsonContainerInsert(
                            metadata, strategy);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }
        for (ElasticsearchEmitterConfig.AttachmentStrategy strategy :
                ElasticsearchEmitterConfig.AttachmentStrategy.values()) {
            String json =
                    ElasticsearchClient.metadataToJsonEmbeddedInsert(
                            metadata, strategy, "myEmitKey",
                            ElasticsearchEmitter
                                    .DEFAULT_EMBEDDED_FILE_FIELD_NAME);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }
    }

    @Test
    public void testParentChildContainer() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("title", "parent doc");
        String json =
                ElasticsearchClient.metadataToJsonContainerInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD);
        assertContains("relation_type", json);
        assertContains("container", json);
    }

    @Test
    public void testParentChildEmbedded() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("title", "child doc");
        String json =
                ElasticsearchClient.metadataToJsonEmbeddedInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD,
                        "parentKey", "embedded");
        assertContains("relation_type", json);
        assertContains("parentKey", json);
        assertContains("embedded", json);
    }

    @Test
    public void testSeparateDocumentsEmbedded() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("title", "child doc");
        String json =
                ElasticsearchClient.metadataToJsonEmbeddedInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS,
                        "parentKey", "embedded");
        assertContains("parent", json);
        assertContains("parentKey", json);
        assertNotContained("relation_type", json);
    }

    @Test
    public void testChunksFieldWrittenAsRawJson() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("title", "test doc");
        metadata.set("tika:chunks",
                "[{\"text\":\"hello\",\"vector\":\"AAAA\","
                        + "\"locators\":{\"text\":[{\"start_offset\":0,"
                        + "\"end_offset\":5}]}}]");
        String json =
                ElasticsearchClient.metadataToJsonContainerInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS);

        // Parse the output JSON — tika:chunks should be a real JSON
        // array, not a double-escaped string
        JsonNode doc = MAPPER.readTree(json);
        JsonNode chunks = doc.get("tika:chunks");
        assertTrue(chunks.isArray(),
                "tika:chunks should be a JSON array, not a string");
        assertEquals(1, chunks.size());
        assertEquals("hello", chunks.get(0).get("text").asText());
        assertEquals("AAAA", chunks.get(0).get("vector").asText());
    }

    @Test
    public void testNonJsonFieldStaysString() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("tika:chunks", "not json at all");
        String json =
                ElasticsearchClient.metadataToJsonContainerInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS);
        JsonNode doc = MAPPER.readTree(json);
        // Should be a plain string since it doesn't look like JSON
        assertTrue(doc.get("tika:chunks").isTextual());
        assertEquals("not json at all",
                doc.get("tika:chunks").asText());
    }

    @Test
    public void testRegularFieldNotRawJson() throws Exception {
        Metadata metadata = new Metadata();
        // A regular field whose value happens to look like JSON
        // should NOT be written as raw JSON
        metadata.set("description", "[some bracketed text]");
        String json =
                ElasticsearchClient.metadataToJsonContainerInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS);
        JsonNode doc = MAPPER.readTree(json);
        assertTrue(doc.get("description").isTextual());
    }

    @Test
    public void testMultiValuedMetadata() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("tags", "tag1");
        metadata.add("tags", "tag2");
        metadata.add("tags", "tag3");
        String json =
                ElasticsearchClient.metadataToJsonContainerInsert(
                        metadata,
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS);
        assertContains("tag1", json);
        assertContains("tag2", json);
        assertContains("tag3", json);
    }
}
