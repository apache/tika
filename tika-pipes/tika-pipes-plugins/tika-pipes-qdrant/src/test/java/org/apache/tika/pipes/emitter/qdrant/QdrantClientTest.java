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
package org.apache.tika.pipes.emitter.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class QdrantClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static String b64(float... values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * Float.BYTES);
        for (float v : values) {
            bb.putFloat(v);
        }
        return Base64.getEncoder().encodeToString(bb.array());
    }

    // tk:chunks is reserved, so only a trusted writer can set it
    static Metadata withChunks(String json) {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TikaCoreProperties.TIKA_CHUNKS.getName(), json);
        return metadata;
    }

    static List<ObjectNode> points(String emitKey, List<Metadata> metadataList,
                                   List<String> metadataFields) throws Exception {
        List<ObjectNode> points = new ArrayList<>();
        QdrantClient.forEachPoint(emitKey, metadataList,
                metadataFields == null ? List.of() : metadataFields, points::add);
        return points;
    }

    /** {@code extraFields} is JSON object members without the braces, or null. */
    static String chunk(String producer, String vector, String extraFields) throws Exception {
        ObjectNode node = extraFields == null ? MAPPER.createObjectNode()
                : (ObjectNode) MAPPER.readTree("{" + extraFields + "}");
        if (producer != null) {
            node.put("producer", producer);
        }
        if (vector != null) {
            node.put("vector", vector);
        }
        return node.toString();
    }

    @Test
    public void testCorrelatedChunksBecomeOnePointWithNamedVectors() throws Exception {
        String chunks = "[" + chunk("jina-video", b64(1f, 2f),
                "\"correlator\":\"seg-1\",\"locators\":{\"temporal\":[{\"start_ms\":0,\"end_ms\":5000}]}")
                + "," + chunk("text-vectors", b64(0.5f, 10f, 6f),
                "\"text\":\"hello\",\"locators\":{\"text\":[{\"start_offset\":0,\"end_offset\":5}],"
                        + "\"paginated\":[{\"page\":3}]}")
                + "," + chunk("jina-audio", b64(3f, 4f), "\"correlator\":\"seg-1\"") + "]";

        List<ObjectNode> points = points("video.mp4", List.of(withChunks(chunks)), null);

        assertEquals(2, points.size());
        JsonNode segment = points.get(0);
        assertEquals(List.of("jina-video", "jina-audio"), fieldNames(segment.get("vector")));
        assertEquals(2.0, segment.get("vector").get("jina-video").get(1).asDouble());
        assertEquals(4.0, segment.get("vector").get("jina-audio").get(1).asDouble());
        JsonNode payload = segment.get("payload");
        assertEquals("video.mp4", payload.get("container_id").asText());
        assertEquals(0, payload.get("file_index").asInt());
        assertEquals(0, payload.get("chunk_index").asInt());
        assertEquals("seg-1", payload.get("correlator").asText());
        assertEquals(5000, payload.get("end_ms").asInt());

        JsonNode text = points.get(1);
        assertEquals("[0.5,10.0,6.0]", text.get("vector").get("text-vectors").toString());
        assertEquals("hello", text.get("payload").get("text").asText());
        assertEquals(5, text.get("payload").get("end_offset").asInt());
        assertEquals(3, text.get("payload").get("page").asInt());
        assertEquals(1, text.get("payload").get("chunk_index").asInt());
    }

    @Test
    public void testSecondVectorOfOneProducerInAUnitGetsItsOwnPoint() throws Exception {
        String chunks = "[" + chunk("text-vectors", b64(1f),
                "\"text\":\"first\",\"correlator\":\"r1\"")
                + "," + chunk("text-vectors", b64(2f), "\"text\":\"second\",\"correlator\":\"r1\"")
                + "," + chunk("crop-vectors", b64(3f), "\"correlator\":\"r1\"") + "]";
        List<ObjectNode> points = points("a.pdf", List.of(withChunks(chunks)), null);
        assertEquals(2, points.size());
        assertEquals("first", points.get(0).get("payload").get("text").asText());
        assertEquals(List.of("text-vectors", "crop-vectors"), fieldNames(points.get(0).get("vector")));
        assertEquals(1.0, points.get(0).get("vector").get("text-vectors").get(0).asDouble());
        assertEquals("second", points.get(1).get("payload").get("text").asText());
        assertEquals(2.0, points.get(1).get("vector").get("text-vectors").get(0).asDouble());
        assertEquals("r1", points.get(1).get("payload").get("correlator").asText());
    }

    @Test
    public void testVectorlessChunkStaysInItsCorrelatedUnit() throws Exception {
        String chunks = "[" + chunk("p", b64(1f), "\"correlator\":\"c\"")
                + "," + chunk("p", null, "\"correlator\":\"c\",\"text\":\"caption\"") + "]";
        List<ObjectNode> points = points("a.mp4", List.of(withChunks(chunks)), null);
        assertEquals(1, points.size());
        assertEquals("caption", points.get(0).get("payload").get("text").asText());
    }

    @Test
    public void testChunkWithoutProducerUsesDefaultName() throws Exception {
        List<ObjectNode> points = points("a.txt",
                List.of(withChunks("[" + chunk(null, b64(1f), null) + "]")), null);
        assertEquals(List.of(QdrantClient.DEFAULT_VECTOR_NAME),
                fieldNames(points.get(0).get("vector")));
    }

    @Test
    public void testUnitsWithoutUsableVectorsWriteNoPoint() throws Exception {
        String chunks = "[" + chunk("p", null, "\"text\":\"no vector\"")
                + "," + chunk("p", b64(1f, Float.NaN), null)
                + "," + chunk("p", b64(1f, Float.POSITIVE_INFINITY), null)
                + "," + chunk("p", "AAAA", null)
                + "," + chunk("p", "not base64!", null)
                + "," + chunk("p", "", null)
                + "," + chunk("p", b64(7f), null) + "]";
        List<ObjectNode> points = points("a.txt", List.of(withChunks(chunks)), null);
        assertEquals(1, points.size());
        assertEquals(6, points.get(0).get("payload").get("chunk_index").asInt(),
                "chunk_index is the unit's position, so it is stable across runs");
    }

    @Test
    public void testFloatsRejectsBadInput() {
        assertEquals("[0.5, 10.0, 6.0]", Arrays.toString(QdrantClient.floats("PwAAAEEgAABAwAAA")));
        assertThrows(IllegalArgumentException.class, () -> QdrantClient.floats(b64(Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> QdrantClient.floats("AAAA"));
        assertThrows(IllegalArgumentException.class, () -> QdrantClient.floats(""));
    }

    @Test
    public void testNoOrInvalidChunksWriteNoPoints() throws Exception {
        Metadata plain = new Metadata();
        plain.set("dc:title", "no chunks");
        assertTrue(points("a", List.of(plain), null).isEmpty());
        assertTrue(points("a", List.of(withChunks("not json")), null).isEmpty());
        assertTrue(points("a", List.of(withChunks("{\"text\":\"x\"}")), null).isEmpty());
        assertTrue(points("a", List.of(withChunks("[1,\"x\",null]")), null).isEmpty());
    }

    @Test
    public void testEmbeddedFilesGetTheirOwnFileIndex() throws Exception {
        String chunks = "[" + chunk("p", b64(1f), null) + "]";
        List<ObjectNode> points = points("mail.eml",
                List.of(new Metadata(), withChunks(chunks), withChunks(chunks)), null);
        assertEquals(2, points.size());
        assertEquals(1, points.get(0).get("payload").get("file_index").asInt());
        assertEquals(2, points.get(1).get("payload").get("file_index").asInt());
        assertNotEquals(points.get(0).get("id"), points.get(1).get("id"));
    }

    @Test
    public void testPointIdsAreStableAndUnambiguous() {
        assertEquals(QdrantClient.pointId("a", 0, 1), QdrantClient.pointId("a", 0, 1));
        assertNotEquals(QdrantClient.pointId("a", 1, 0), QdrantClient.pointId("a-1", 0, 0));
        assertNotEquals(QdrantClient.pointId("a", 1, 0), QdrantClient.pointId("a", 0, 1));
        assertNotEquals(QdrantClient.pointId("a\",1", 0, 0), QdrantClient.pointId("a", 1, 0));
    }

    @Test
    public void testMetadataPayload() throws Exception {
        Metadata metadata = withChunks("[" + chunk("p", b64(1f), "\"locators\":{\"spatial\":"
                + "[{\"bbox\":[1,2,3,4],\"label\":\"table\"}]}") + "]");
        metadata.set("dc:title", "Q4 report");
        metadata.add("dc:creator", "a");
        metadata.add("dc:creator", "b");
        metadata.setTrusted(TikaCoreProperties.TIKA_CONTENT.getName(), "the whole text");

        JsonNode none = points("a", List.of(metadata), List.of()).get(0).get("payload");
        assertFalse(none.has("metadata"), "the default copies no metadata");
        assertEquals("table", none.get("label").asText());

        JsonNode some = points("a", List.of(metadata), List.of("dc:title", "dc:creator", "missing",
                TikaCoreProperties.TIKA_CONTENT.getName(), TikaCoreProperties.TIKA_CHUNKS.getName()))
                .get(0).get("payload").get("metadata");
        assertEquals(List.of("dc:title", "dc:creator", TikaCoreProperties.TIKA_CONTENT.getName()),
                fieldNames(some));
        assertEquals("[\"a\",\"b\"]", some.get("dc:creator").toString());
    }

    @Test
    public void testScrollAndDeleteJson() throws Exception {
        JsonNode first = MAPPER.readTree(QdrantClient.scrollJson("a", null));
        assertEquals("a", first.get("filter").get("must").get(0).get("match").get("value").asText());
        assertFalse(first.get("with_payload").asBoolean());
        assertFalse(first.get("with_vector").asBoolean());
        assertFalse(first.has("offset"));
        JsonNode next = MAPPER.readTree(QdrantClient.scrollJson("a", MAPPER.readTree("\"x\"")));
        assertEquals("x", next.get("offset").asText());

        JsonNode delete = MAPPER.readTree(QdrantClient.deleteJson(
                List.of(MAPPER.readTree("\"u-1\""), MAPPER.readTree("7"))));
        assertEquals("[\"u-1\",7]", delete.get("points").toString());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
