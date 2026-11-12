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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaException;

public class OneNotePtrBoundsTest {

    @Test
    public void testChunkReferenceOutsideTheFileIsRejected(@TempDir Path tempDir)
            throws Exception {
        Path file = Files.write(tempDir.resolve("f.one"), new byte[64]);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            ptr.reposition(new FileChunkReference(56, 8));
            assertEquals(8, ptr.roomLeft());
            assertThrows(IOException.class, () -> ptr.reposition(new FileChunkReference(60, 8)));
            assertThrows(IOException.class, () -> ptr.reposition(new FileChunkReference(-1, 4)));
            assertThrows(IOException.class, () -> ptr.reposition(new FileChunkReference(0, -1)));
            assertThrows(IOException.class,
                    () -> ptr.reposition(new FileChunkReference(8, Long.MAX_VALUE)));
        }
    }

    @Test
    public void testDeferredPropertySetIsBoundedByItsChunk(@TempDir Path tempDir)
            throws Exception {
        // one FourBytesOfLengthFollowedByData property claiming 50 bytes
        ByteBuffer bytes = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) 1).putInt(0x1C001C22).putInt(50);
        Path file = Files.write(tempDir.resolve("f.one"), bytes.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            OneNoteDocument document = new OneNoteDocument();
            new OneNotePtr(document, dif);
            // the same values fit the file, so only the chunk's end can reject them
            assertEquals(1, document.propertySet(deferred(200)).rgPridsData.size());
            assertThrows(TikaException.class, () -> document.propertySet(deferred(10)));
        }
    }

    private static FileNode deferred(long end) {
        FileNode node = new FileNode();
        node.propertySetStreams = new ObjectSpaceObjectPropSet();
        node.propertySetValues = 0;
        node.propertySetEnd = end;
        return node;
    }
}
