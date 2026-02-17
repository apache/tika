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
package org.apache.tika.inference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.inference.locator.SpatialLocator;
import org.apache.tika.inference.locator.TemporalLocator;
import org.apache.tika.inference.locator.TextLocator;

/**
 * Serializes and deserializes a list of {@link Chunk} objects to/from JSON.
 * Vectors are stored as base64-encoded little-endian float32 via
 * {@link VectorSerializer}. Locators are nested under a {@code "locators"}
 * object with optional {@code text}, {@code paginated}, {@code spatial},
 * and {@code temporal} arrays.
 */
public final class ChunkSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChunkSerializer() {
    }

    /**
     * Serialize chunks to a JSON array string.
     */
    public static String toJson(List<Chunk> chunks) throws IOException {
        ArrayNode array = MAPPER.createArrayNode();
        for (Chunk chunk : chunks) {
            ObjectNode node = array.addObject();
            if (chunk.getText() != null) {
                node.put("text", chunk.getText());
            }
            if (chunk.getVector() != null) {
                node.put("vector", VectorSerializer.encode(chunk.getVector()));
            }
            serializeLocators(node, chunk.getLocators());
        }
        return MAPPER.writeValueAsString(array);
    }

    /**
     * The canonical metadata field for all chunk data (text chunks,
     * image embeddings, audio segments, etc.).
     */
    public static final String CHUNKS_FIELD = "tika:chunks";

    /**
     * Reads any existing chunks from the metadata field, appends the
     * new chunks, and writes the merged list back. This allows
     * multiple components (text chunker, image embedder, etc.) to
     * contribute to the same chunks array.
     *
     * @param metadata  the metadata to read from and write to
     * @param newChunks chunks to append
     */
    public static void mergeInto(
            org.apache.tika.metadata.Metadata metadata,
            List<Chunk> newChunks) throws IOException {
        List<Chunk> existing;
        String current = metadata.get(CHUNKS_FIELD);
        if (current != null && !current.isEmpty()) {
            existing = fromJson(current);
        } else {
            existing = new ArrayList<>();
        }
        existing.addAll(newChunks);
        metadata.set(CHUNKS_FIELD, toJson(existing));
    }

    /**
     * Deserialize a JSON array string back to a list of chunks.
     */
    public static List<Chunk> fromJson(String json) throws IOException {
        JsonNode array = MAPPER.readTree(json);
        List<Chunk> chunks = new ArrayList<>();
        for (JsonNode node : array) {
            String text = node.has("text") ? node.get("text").asText() : null;
            Locators locators = deserializeLocators(node.get("locators"));

            Chunk chunk = new Chunk(text, locators);

            JsonNode vectorNode = node.get("vector");
            if (vectorNode != null && !vectorNode.isNull()) {
                chunk.setVector(VectorSerializer.decode(vectorNode.asText()));
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    // ---- locator serialization --------------------------------------------

    private static void serializeLocators(ObjectNode parent, Locators loc) {
        if (loc == null || loc.isEmpty()) {
            return;
        }

        ObjectNode locNode = parent.putObject("locators");

        if (loc.getText() != null && !loc.getText().isEmpty()) {
            ArrayNode arr = locNode.putArray("text");
            for (TextLocator t : loc.getText()) {
                ObjectNode o = arr.addObject();
                o.put("start_offset", t.getStartOffset());
                o.put("end_offset", t.getEndOffset());
            }
        }

        if (loc.getPaginated() != null && !loc.getPaginated().isEmpty()) {
            ArrayNode arr = locNode.putArray("paginated");
            for (PaginatedLocator p : loc.getPaginated()) {
                ObjectNode o = arr.addObject();
                o.put("page", p.getPage());
                if (p.getBbox() != null) {
                    ArrayNode bboxArr = o.putArray("bbox");
                    for (float v : p.getBbox()) {
                        bboxArr.add(v);
                    }
                }
            }
        }

        if (loc.getSpatial() != null && !loc.getSpatial().isEmpty()) {
            ArrayNode arr = locNode.putArray("spatial");
            for (SpatialLocator s : loc.getSpatial()) {
                ObjectNode o = arr.addObject();
                if (s.getBbox() != null) {
                    ArrayNode bboxArr = o.putArray("bbox");
                    for (float v : s.getBbox()) {
                        bboxArr.add(v);
                    }
                }
                if (s.getLabel() != null) {
                    o.put("label", s.getLabel());
                }
            }
        }

        if (loc.getTemporal() != null && !loc.getTemporal().isEmpty()) {
            ArrayNode arr = locNode.putArray("temporal");
            for (TemporalLocator t : loc.getTemporal()) {
                ObjectNode o = arr.addObject();
                o.put("start_ms", t.getStartMs());
                o.put("end_ms", t.getEndMs());
            }
        }
    }

    private static Locators deserializeLocators(JsonNode locNode) {
        Locators locators = new Locators();
        if (locNode == null || locNode.isNull()) {
            return locators;
        }

        JsonNode textArr = locNode.get("text");
        if (textArr != null && textArr.isArray()) {
            for (JsonNode n : textArr) {
                locators.addText(new TextLocator(
                        n.get("start_offset").asInt(),
                        n.get("end_offset").asInt()));
            }
        }

        JsonNode pagArr = locNode.get("paginated");
        if (pagArr != null && pagArr.isArray()) {
            for (JsonNode n : pagArr) {
                float[] bbox = deserializeBbox(n.get("bbox"));
                locators.addPaginated(
                        new PaginatedLocator(n.get("page").asInt(), bbox));
            }
        }

        JsonNode spatArr = locNode.get("spatial");
        if (spatArr != null && spatArr.isArray()) {
            for (JsonNode n : spatArr) {
                float[] bbox = deserializeBbox(n.get("bbox"));
                String label = n.has("label") ? n.get("label").asText() : null;
                locators.addSpatial(new SpatialLocator(bbox, label));
            }
        }

        JsonNode tempArr = locNode.get("temporal");
        if (tempArr != null && tempArr.isArray()) {
            for (JsonNode n : tempArr) {
                locators.addTemporal(new TemporalLocator(
                        n.get("start_ms").asLong(),
                        n.get("end_ms").asLong()));
            }
        }

        return locators;
    }

    private static float[] deserializeBbox(JsonNode bboxNode) {
        if (bboxNode == null || !bboxNode.isArray()) {
            return null;
        }
        float[] bbox = new float[bboxNode.size()];
        for (int i = 0; i < bboxNode.size(); i++) {
            bbox[i] = (float) bboxNode.get(i).asDouble();
        }
        return bbox;
    }
}
