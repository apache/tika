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
package org.apache.tika.pipes.emitter.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class OpenSearchClientTest extends TikaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSerialization() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("authors", "author1");
        metadata.add("authors", "author2");
        metadata.add("title", "title1");
        for (OpenSearchEmitterConfig.AttachmentStrategy strategy :
                OpenSearchEmitterConfig.AttachmentStrategy.values()) {
            String json = OpenSearchClient.metadataToJsonContainerInsert(metadata,
                    strategy);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }
        for (OpenSearchEmitterConfig.AttachmentStrategy strategy :
                OpenSearchEmitterConfig.AttachmentStrategy.values()) {
            String json = OpenSearchClient.metadataToJsonEmbeddedInsert(metadata, strategy,
                    "myEmitKey", OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }

    }

    /** Chunks sharing a correlator become one document with a vector per producer. */
    @Test
    public void testChunkDocumentsGroupByCorrelator() throws Exception {
        String one = Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(8).putFloat(1.0f).putFloat(0.5f).array());
        String two = Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(4).putFloat(2.0f).array());
        JsonNode chunks = MAPPER.readTree("["
                + "{\"correlator\":\"t:25000-55000\",\"producer\":\"jina-video\",\"modality\":\"visual\","
                + " \"vector\":\"" + one + "\",\"locators\":{\"temporal\":[{\"start_ms\":25000,\"end_ms\":55000}],"
                + " \"embedded\":[{\"id_path\":\"/1\",\"name\":\"clip.mp4\"}]}},"
                + "{\"text\":\"solo\",\"vector\":\"" + two + "\",\"locators\":{\"text\":[{\"start_offset\":0,\"end_offset\":4}]}},"
                + "{\"correlator\":\"t:25000-55000\",\"producer\":\"jina-audio\",\"modality\":\"audio\","
                + " \"vector\":\"" + two + "\",\"locators\":{\"temporal\":[{\"start_ms\":25000,\"end_ms\":55000}]}}"
                + "]");
        List<ObjectNode> docs =
                OpenSearchClient.chunkDocuments(chunks, "k-abc", "k");
        assertEquals(2, docs.size());
        ObjectNode segment = docs.get(0);
        assertEquals("k-abc", segment.get("file_id").asText());
        assertEquals("k", segment.get("container_id").asText());
        assertEquals("t:25000-55000", segment.get("correlator").asText());
        assertEquals(25000, segment.get("start_ms").asLong());
        assertEquals("/1", segment.get("embedded_id_path").asText());
        assertEquals(2, segment.get("v").size());
        assertEquals(1.0, segment.get("v").get("jina-video").get(0).asDouble(), 1e-6);
        assertEquals(0.5, segment.get("v").get("jina-video").get(1).asDouble(), 1e-6);
        assertEquals(2.0, segment.get("v").get("jina-audio").get(0).asDouble(), 1e-6);
        assertFalse(segment.has("modality"));
        ObjectNode solo = docs.get(1);
        assertEquals("solo", solo.get("text").asText());
        assertEquals(0, solo.get("start_offset").asInt());
        assertEquals(2.0, solo.get("v").get("default").get(0).asDouble(), 1e-6);
        assertFalse(solo.has("correlator"));
    }

    private static void setChunks(Metadata metadata, String json) {
        metadata.setTrusted(TikaCoreProperties.TIKA_CHUNKS.getName(), json);
    }

    /** Captures the bulk body instead of posting it. */
    private static final class CapturingClient extends OpenSearchClient {
        final List<String> bodies = new ArrayList<>();

        CapturingClient(OpenSearchEmitterConfig config) {
            super(config, null);
        }

        @Override
        public JsonResponse postJson(String url, String json) {
            bodies.add(json);
            return new JsonResponse(200, MAPPER.createObjectNode().put("errors", false));
        }
    }

    private static OpenSearchEmitterConfig config(OpenSearchEmitterConfig.UpdateStrategy update,
                                          OpenSearchEmitterConfig.ChunkStrategy chunks) {
        return new OpenSearchEmitterConfig("http://os/idx", "_id",
                OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS, update, 10,
                "embedded", null, chunks);
    }

    private static String vector(float... floats) {
        ByteBuffer bb = ByteBuffer.allocate(4 * floats.length);
        for (float f : floats) {
            bb.putFloat(f);
        }
        return Base64.getEncoder().encodeToString(bb.array());
    }

    /** A real Tika document: reserved keys next to the chunks. */
    private static Metadata document(String chunks) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "the text");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "SomeParser");
        metadata.add("tags", "a");
        metadata.add("tags", "b");
        setChunks(metadata, chunks);
        return metadata;
    }

    private static List<JsonNode> lines(String bulk) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (String line : bulk.split("\n")) {
            if (!line.isBlank()) {
                out.add(MAPPER.readTree(line));
            }
        }
        return out;
    }

    /** The production path: reserved keys survive, tk:chunks leaves the document, chunk docs follow. */
    @Test
    public void testDocumentsBulkBody() throws Exception {
        String chunks = "[{\"correlator\":\"t:0-30000\",\"producer\":\"v\",\"vector\":\""
                + vector(1f) + "\",\"locators\":{\"temporal\":[{\"start_ms\":0,\"end_ms\":30000}]}},"
                + "{\"correlator\":\"t:0-30000\",\"producer\":\"a\",\"vector\":\"" + vector(2f)
                + "\",\"locators\":{\"temporal\":[{\"start_ms\":0,\"end_ms\":30000}]}},"
                + "{\"text\":\"solo\",\"vector\":\"" + vector(3f) + "\"}]";
        for (OpenSearchEmitterConfig.UpdateStrategy update : OpenSearchEmitterConfig.UpdateStrategy.values()) {
            CapturingClient client = new CapturingClient(
                    config(update, OpenSearchEmitterConfig.ChunkStrategy.DOCUMENTS));
            client.emitDocument("k", List.of(document(chunks)));
            List<JsonNode> lines = lines(client.bodies.get(0));
            assertEquals(6, lines.size(), update + ": one action+body per document");
            boolean upsert = update == OpenSearchEmitterConfig.UpdateStrategy.UPSERT;
            String action = upsert ? "update" : "index";
            assertEquals("k", lines.get(0).get(action).get("_id").asText());
            JsonNode file = upsert ? lines.get(1).get("doc") : lines.get(1);
            assertEquals("the text", file.get("tk:content").asText(), update.toString());
            assertTrue(file.get("tk:parsed-by").toString().contains("SomeParser"));
            assertEquals(2, file.get("tags").size());
            assertNull(file.get("tk:chunks"), "chunks leave the file document");
            assertEquals("k-chunk-0", lines.get(2).get(action).get("_id").asText());
            JsonNode segment = upsert ? lines.get(3).get("doc") : lines.get(3);
            assertEquals("k", segment.get("file_id").asText());
            assertEquals(1.0, segment.get("v").get("v").get(0).asDouble(), 1e-6);
            assertEquals(2.0, segment.get("v").get("a").get(0).asDouble(), 1e-6);
            if (upsert) {
                assertTrue(lines.get(3).get("doc_as_upsert").asBoolean());
            }
            assertEquals("k-chunk-1", lines.get(4).get(action).get("_id").asText());
            JsonNode solo = upsert ? lines.get(5).get("doc") : lines.get(5);
            assertEquals("solo", solo.get("text").asText());
        }
        CapturingClient inline = new CapturingClient(
                config(OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE, OpenSearchEmitterConfig.ChunkStrategy.INLINE));
        inline.emitDocument("k", List.of(document(chunks)));
        List<JsonNode> lines = lines(inline.bodies.get(0));
        assertEquals(2, lines.size(), "INLINE writes no chunk documents");
        assertTrue(lines.get(1).get("tk:chunks").isArray());
    }

    /** A vector that is not base64 loses the vector, not the batch; chunks that are not JSON stay inline. */
    @Test
    public void testDocumentsToleratesBadChunks() throws Exception {
        CapturingClient client = new CapturingClient(
                config(OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE, OpenSearchEmitterConfig.ChunkStrategy.DOCUMENTS));
        client.emitDocument("k", List.of(document(
                "[{\"text\":\"one\",\"producer\":\"p\",\"vector\":\"not*base64\"},"
                + "{\"text\":\"two\",\"producer\":\"p\",\"vector\":\"" + vector(1f) + "\"}]")));
        List<JsonNode> lines = lines(client.bodies.get(0));
        assertEquals(6, lines.size());
        assertNull(lines.get(3).get("v"), "the undecodable vector is dropped");
        assertEquals("one", lines.get(3).get("text").asText());
        assertEquals(1.0, lines.get(5).get("v").get("p").get(0).asDouble(), 1e-6);

        client = new CapturingClient(
                config(OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE, OpenSearchEmitterConfig.ChunkStrategy.DOCUMENTS));
        client.emitDocument("k", List.of(document("not json")));
        lines = lines(client.bodies.get(0));
        assertEquals(2, lines.size(), "nothing to split: the value stays on the document");
        assertEquals("not json", lines.get(1).get("tk:chunks").asText());
    }

    /** A missing chunkStrategy is INLINE; DOCUMENTS cannot join a PARENT_CHILD index. */
    @Test
    public void testChunkStrategyConfig() throws Exception {
        String base = "{\"openSearchUrl\": \"http://os/idx\", \"idField\": \"_id\","
                + " \"updateStrategy\": \"OVERWRITE\", \"attachmentStrategy\": \"%s\"%s}";
        assertEquals(OpenSearchEmitterConfig.ChunkStrategy.INLINE, OpenSearchEmitterConfig.load(
                String.format(Locale.ROOT, base, "PARENT_CHILD", "")).chunkStrategy());
        assertEquals(OpenSearchEmitterConfig.ChunkStrategy.DOCUMENTS, OpenSearchEmitterConfig.load(
                String.format(Locale.ROOT, base, "SEPARATE_DOCUMENTS", ", \"chunkStrategy\": \"DOCUMENTS\""))
                .chunkStrategy());
        assertThrows(TikaConfigException.class, () -> OpenSearchEmitterConfig.load(
                String.format(Locale.ROOT, base, "PARENT_CHILD", ", \"chunkStrategy\": \"DOCUMENTS\"")));
    }
}
