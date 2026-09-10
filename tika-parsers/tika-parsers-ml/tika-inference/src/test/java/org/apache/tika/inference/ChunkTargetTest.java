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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class ChunkTargetTest {

    private static Metadata child(String type, String idPath, String name) {
        Metadata m = new Metadata();
        if (type != null) {
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, type);
        }
        if (idPath != null) {
            m.set(TikaCoreProperties.EMBEDDED_ID_PATH, idPath);
        }
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        return m;
    }

    private static ParseContext withParent(Metadata parent) {
        ParseContext context = new ParseContext();
        context.set(ParentMetadata.class, new ParentMetadata(parent));
        return context;
    }

    @Test
    public void testInlineAndRenderingLiftToParent() throws Exception {
        Metadata parent = new Metadata();
        for (String type : List.of("INLINE", "RENDERING")) {
            Metadata child = child(type, "/1", "image1.png");
            ChunkTarget target = ChunkTarget.resolve(child, withParent(parent));
            assertSame(parent, target.getMetadata());
            assertEquals("/1", target.getLocator().getIdPath());
            assertEquals("image1.png", target.getLocator().getName());
        }
    }

    @Test
    public void testAttachmentTopLevelAndNoParentKeepTheirOwn() {
        Metadata parent = new Metadata();
        Metadata attachment = child("ATTACHMENT", "/2", "report.pdf");
        assertSame(attachment, ChunkTarget.resolve(attachment, withParent(parent)).getMetadata());
        assertNull(ChunkTarget.resolve(attachment, withParent(parent)).getLocator());

        Metadata untyped = child(null, "/3", "mystery.bin");
        assertSame(untyped, ChunkTarget.resolve(untyped, withParent(parent)).getMetadata());

        Metadata orphan = child("INLINE", "/4", "image4.png");
        assertSame(orphan, ChunkTarget.resolve(orphan, new ParseContext()).getMetadata());

        Metadata topLevel = new Metadata();
        assertSame(topLevel, ChunkTarget.resolve(topLevel, withParent(parent)).getMetadata());
        assertSame(topLevel, ChunkTarget.self(topLevel).getMetadata());
    }

    @Test
    public void testWriteTagsAndAppends() throws Exception {
        Metadata parent = new Metadata();
        ParseContext context = withParent(parent);
        for (int i = 1; i <= 2; i++) {
            Chunk chunk = new Chunk(null, new Locators());
            chunk.setVector(new float[]{i});
            ChunkTarget.resolve(child("INLINE", "/" + i, "image" + i + ".png"), context)
                    .write(List.of(chunk), TikaCoreProperties.TIKA_CHUNKS.getName());
        }
        List<Chunk> chunks = ChunkSerializer.fromJson(parent.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(2, chunks.size());
        assertEquals("/1", chunks.get(0).getLocators().getEmbedded().get(0).getIdPath());
        assertEquals("image2.png", chunks.get(1).getLocators().getEmbedded().get(0).getName());
    }
}
