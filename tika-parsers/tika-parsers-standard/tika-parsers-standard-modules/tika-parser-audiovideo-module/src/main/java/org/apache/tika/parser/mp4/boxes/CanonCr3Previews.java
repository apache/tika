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
package org.apache.tika.parser.mp4.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.xml.sax.SAXException;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The JPEG previews of a Canon CR3 raw file, which keeps them in two Canon
 * {@code uuid} boxes of its ISO base media file:
 * <ul>
 *   <li>{@code eaf42b5e-1c98-4b88-b9fb-b7dc406e4d16} at the top level holds, after
 *   eight bytes, a {@code PRVW} box with the large preview (1620 pixels wide on
 *   current bodies); it is the
 *   {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}</li>
 *   <li>{@code 85c0b687-820f-11e0-8111-f4ce462b6a48} in {@code moov} holds, among
 *   its child boxes, a {@code THMB} box with a 160x120 thumbnail; it is
 *   {@link TikaCoreProperties.EmbeddedResourceType#INLINE}</li>
 * </ul>
 * Both records carry width and height and the JPEG length ahead of the JPEG,
 * which starts 20 bytes into the record's payload after its type.
 */
public final class CanonCr3Previews {

    private static final byte[] PREVIEW_UUID = uuid("eaf42b5e1c984b88b9fbb7dc406e4d16");
    private static final byte[] METADATA_UUID = uuid("85c0b687820f11e08111f4ce462b6a48");
    private static final int UUID_LENGTH = 16;
    /** The preview box's children start after eight bytes of its own. */
    private static final int PREVIEW_CHILDREN_OFFSET = 8;
    /** From the start of a PRVW or THMB box to its JPEG: size, type and twelve header bytes. */
    private static final int JPEG_OFFSET = 24;

    private CanonCr3Previews() {
    }

    /**
     * Emits the preview a {@code uuid} box carries, if it is one of Canon's.
     *
     * @return whether the box was a Canon preview box
     */
    public static boolean process(byte[] payload, XHTMLContentHandler xhtml,
                                  ParseContext context) throws IOException {
        if (payload == null || payload.length < UUID_LENGTH) {
            return false;
        }
        byte[] uuid = Arrays.copyOf(payload, UUID_LENGTH);
        if (Arrays.equals(uuid, PREVIEW_UUID)) {
            emit(payload, UUID_LENGTH + PREVIEW_CHILDREN_OFFSET, "PRVW", 10, 16,
                    TikaCoreProperties.EmbeddedResourceType.THUMBNAIL, xhtml, context);
            return true;
        }
        if (Arrays.equals(uuid, METADATA_UUID)) {
            emit(payload, UUID_LENGTH, "THMB", 8, 12,
                    TikaCoreProperties.EmbeddedResourceType.INLINE, xhtml, context);
            return true;
        }
        return false;
    }

    /**
     * Finds the child box of the given type from {@code start} on and emits its JPEG.
     * {@code sizeOffset} and {@code lengthOffset} are the positions of width/height
     * and of the JPEG length, counted from the box's type.
     */
    private static void emit(byte[] payload, int start, String type, int sizeOffset,
                             int lengthOffset, TikaCoreProperties.EmbeddedResourceType resourceType,
                             XHTMLContentHandler xhtml, ParseContext context) throws IOException {
        int box = findChild(payload, start, type);
        if (box < 0) {
            return;
        }
        int typeAt = box + 4;
        long jpegLength = readInt(payload, typeAt + lengthOffset) & 0xFFFFFFFFL;
        int jpeg = box + JPEG_OFFSET;
        if (jpegLength < 2 || jpegLength > payload.length - jpeg
                || (payload[jpeg] & 0xFF) != 0xFF || (payload[jpeg + 1] & 0xFF) != 0xD8) {
            return;
        }
        Metadata metadata = Metadata.newInstance(context);
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, resourceType.name());
        metadata.set(TIFF.IMAGE_WIDTH, readShort(payload, typeAt + sizeOffset));
        metadata.set(TIFF.IMAGE_LENGTH, readShort(payload, typeAt + sizeOffset + 2));
        EmbeddedDocumentExtractor extractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(metadata, context)) {
            return;
        }
        byte[] bytes = Arrays.copyOfRange(payload, jpeg, jpeg + (int) jpegLength);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            extractor.parseEmbedded(tis, xhtml, metadata, context, true);
        } catch (SAXException e) {
            //need to punch through IOException catching in MP4Reader
            throw new RuntimeSAXException(e);
        }
    }

    /** The offset of the first child box of that type, walking the boxes from {@code start}. */
    private static int findChild(byte[] payload, int start, String type) {
        byte[] wanted = type.getBytes(StandardCharsets.US_ASCII);
        int offset = start;
        while (offset >= 0 && offset + 8 <= payload.length) {
            long size = readInt(payload, offset) & 0xFFFFFFFFL;
            if (Arrays.equals(payload, offset + 4, offset + 8, wanted, 0, 4)) {
                return size >= JPEG_OFFSET && size <= payload.length - offset ? offset : -1;
            }
            if (size < 8) {
                return -1;
            }
            offset += (int) Math.min(size, Integer.MAX_VALUE);
        }
        return -1;
    }

    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

    private static int readShort(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
    }

    private static byte[] uuid(String hex) {
        byte[] bytes = new byte[UUID_LENGTH];
        for (int i = 0; i < UUID_LENGTH; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
