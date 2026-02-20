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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.inference.locator.SpatialLocator;
import org.apache.tika.inference.locator.TemporalLocator;
import org.apache.tika.inference.locator.TextLocator;

public class ChunkSerializerTest {

    @Test
    void testRoundTripTextLocator() throws Exception {
        Chunk c1 = new Chunk("Hello world", 0, 11);
        c1.setVector(new float[]{0.1f, 0.2f, 0.3f});

        Chunk c2 = new Chunk("Goodbye", 12, 19);
        c2.setVector(new float[]{0.4f, 0.5f, 0.6f});

        String json = ChunkSerializer.toJson(List.of(c1, c2));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals(2, restored.size());

        assertEquals("Hello world", restored.get(0).getText());
        assertNotNull(restored.get(0).getLocators().getText());
        assertEquals(1, restored.get(0).getLocators().getText().size());
        assertEquals(0, restored.get(0).getLocators().getText().get(0).getStartOffset());
        assertEquals(11, restored.get(0).getLocators().getText().get(0).getEndOffset());
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f},
                restored.get(0).getVector(), 1e-6f);

        assertEquals("Goodbye", restored.get(1).getText());
        assertEquals(12, restored.get(1).getLocators().getText().get(0).getStartOffset());
    }

    @Test
    void testPaginatedLocator() throws Exception {
        Locators loc = new Locators()
                .addText(new TextLocator(0, 100))
                .addPaginated(new PaginatedLocator(4, new float[]{0.1f, 0.1f, 0.3f, 0.5f}))
                .addPaginated(new PaginatedLocator(5, new float[]{0.8f, 0.1f, 0.9f, 0.5f}));

        Chunk c = new Chunk("Spanning two pages", loc);
        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals(1, restored.size());
        Locators rl = restored.get(0).getLocators();
        assertEquals(1, rl.getText().size());
        assertEquals(2, rl.getPaginated().size());
        assertEquals(4, rl.getPaginated().get(0).getPage());
        assertArrayEquals(new float[]{0.1f, 0.1f, 0.3f, 0.5f},
                rl.getPaginated().get(0).getBbox(), 1e-6f);
        assertEquals(5, rl.getPaginated().get(1).getPage());
    }

    @Test
    void testSpatialLocator() throws Exception {
        Locators loc = new Locators()
                .addSpatial(new SpatialLocator(
                        new float[]{0.2f, 0.2f, 0.4f, 0.4f}, "leak_point"))
                .addSpatial(new SpatialLocator(
                        new float[]{0.5f, 0.5f, 0.7f, 0.7f}, null));

        Chunk c = new Chunk(null, loc);
        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertNull(restored.get(0).getText());
        assertEquals(2, restored.get(0).getLocators().getSpatial().size());
        assertEquals("leak_point",
                restored.get(0).getLocators().getSpatial().get(0).getLabel());
        assertNull(restored.get(0).getLocators().getSpatial().get(1).getLabel());
    }

    @Test
    void testTemporalLocator() throws Exception {
        Locators loc = new Locators()
                .addTemporal(new TemporalLocator(12000, 15000))
                .addTemporal(new TemporalLocator(45000, 48000));

        Chunk c = new Chunk("speech segment", loc);
        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals(2, restored.get(0).getLocators().getTemporal().size());
        assertEquals(12000,
                restored.get(0).getLocators().getTemporal().get(0).getStartMs());
        assertEquals(48000,
                restored.get(0).getLocators().getTemporal().get(1).getEndMs());
    }

    @Test
    void testAllLocatorTypes() throws Exception {
        Locators loc = new Locators()
                .addText(new TextLocator(0, 500))
                .addPaginated(new PaginatedLocator(3))
                .addSpatial(new SpatialLocator(
                        new float[]{0.1f, 0.2f, 0.3f, 0.4f}, "table"))
                .addTemporal(new TemporalLocator(0, 5000));

        Chunk c = new Chunk("multimodal chunk", loc);
        c.setVector(new float[]{1.0f, 2.0f});

        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        Locators rl = restored.get(0).getLocators();
        assertEquals(1, rl.getText().size());
        assertEquals(1, rl.getPaginated().size());
        assertEquals(1, rl.getSpatial().size());
        assertEquals(1, rl.getTemporal().size());
        assertNotNull(restored.get(0).getVector());
    }

    @Test
    void testPaginatedWithoutBbox() throws Exception {
        Locators loc = new Locators()
                .addPaginated(new PaginatedLocator(7));

        Chunk c = new Chunk("whole page", loc);
        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals(7, restored.get(0).getLocators().getPaginated().get(0).getPage());
        assertNull(restored.get(0).getLocators().getPaginated().get(0).getBbox());
    }

    @Test
    void testWithoutVector() throws Exception {
        Chunk c = new Chunk("No vector", 0, 9);
        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals(1, restored.size());
        assertEquals("No vector", restored.get(0).getText());
        assertNull(restored.get(0).getVector());
    }

    @Test
    void testEmptyList() throws Exception {
        String json = ChunkSerializer.toJson(List.of());
        assertEquals("[]", json);
        assertEquals(0, ChunkSerializer.fromJson(json).size());
    }

    @Test
    void testSpecialCharacters() throws Exception {
        Chunk c = new Chunk("He said \"hello\" & <goodbye>", 0, 27);
        c.setVector(new float[]{1.0f});

        String json = ChunkSerializer.toJson(List.of(c));
        List<Chunk> restored = ChunkSerializer.fromJson(json);

        assertEquals("He said \"hello\" & <goodbye>",
                restored.get(0).getText());
    }
}
