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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;

public class ChunkTargetTest {

    @TempDir
    Path tmp;

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

    private InferenceUnit unit(Metadata target, Metadata parent) throws IOException {
        Path file = Files.createTempFile(tmp, "unit", ".png");
        return new InferenceUnit(InputKind.IMAGES, MediaType.image("png"), target, parent, file);
    }

    /** The unit's destination is the dispatcher's call; a lifted unit names its document. */
    @Test
    public void testOfFollowsTheUnit() throws Exception {
        Metadata parent = new Metadata();
        for (String type : List.of("INLINE", "RENDERING")) {
            ChunkTarget target = ChunkTarget.of(unit(child(type, "/1", "image1.png"), parent));
            assertSame(parent, target.getMetadata());
            assertEquals("/1", target.getLocator().getIdPath());
            assertEquals("image1.png", target.getLocator().getName());
        }
        Metadata attachment = child("ATTACHMENT", "/2", "report.pdf");
        ChunkTarget own = ChunkTarget.of(unit(attachment, parent));
        assertSame(attachment, own.getMetadata());
        assertNull(own.getLocator());

        Metadata topLevel = new Metadata();
        assertSame(topLevel, ChunkTarget.of(unit(topLevel, null)).getMetadata());
        assertSame(topLevel, ChunkTarget.self(topLevel).getMetadata());
    }

    @Test
    public void testInParseInlineAndRenderingLiftToParent() {
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
    public void testInParseAttachmentUnnumberedAndOrphanKeepTheirOwn() {
        Metadata parent = new Metadata();
        Metadata attachment = child("ATTACHMENT", "/2", "report.pdf");
        assertSame(attachment, ChunkTarget.resolve(attachment, withParent(parent)).getMetadata());
        assertNull(ChunkTarget.resolve(attachment, withParent(parent)).getLocator());

        Metadata untyped = child(null, "/3", "mystery.bin");
        assertSame(untyped, ChunkTarget.resolve(untyped, withParent(parent)).getMetadata());

        Metadata unnumbered = child("INLINE", null, "image3.png");
        assertSame(unnumbered, ChunkTarget.resolve(unnumbered, withParent(parent)).getMetadata());

        Metadata orphan = child("INLINE", "/4", "image4.png");
        assertSame(orphan, ChunkTarget.resolve(orphan, new ParseContext()).getMetadata());
        assertSame(orphan, ChunkTarget.resolve(orphan, withParent(null)).getMetadata());
    }

    @Test
    public void testWriteTagsAndAppends() throws Exception {
        Metadata parent = new Metadata();
        for (int i = 1; i <= 2; i++) {
            Chunk chunk = new Chunk(null, new Locators());
            chunk.setVector(new float[]{i});
            ChunkTarget.of(unit(child("INLINE", "/" + i, "image" + i + ".png"), parent))
                    .write(List.of(chunk), TikaCoreProperties.TIKA_CHUNKS.getName());
        }
        List<Chunk> chunks = ChunkSerializer.fromJson(parent.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(2, chunks.size());
        assertEquals("/1", chunks.get(0).getLocators().getEmbedded().get(0).getIdPath());
        assertEquals("image2.png", chunks.get(1).getLocators().getEmbedded().get(0).getName());
    }
}
