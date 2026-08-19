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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

/**
 * Unit tests for {@link PipesWorker#carryCallerHints(Metadata, Metadata)}, which carries the
 * caller-supplied detection hints across the worker's fresh-metadata boundary.
 */
public class PipesWorkerCallerHintsTest {

    @Test
    public void testCarriesResourceNameAndContentType() {
        Metadata tuple = new Metadata();
        tuple.set(TikaCoreProperties.RESOURCE_NAME_KEY, "photo.nef");
        tuple.set(HttpHeaders.CONTENT_TYPE, "image/x-raw-nikon");

        Metadata target = new Metadata();
        PipesWorker.carryCallerHints(tuple, target);

        assertEquals("photo.nef", target.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image/x-raw-nikon", target.get(HttpHeaders.CONTENT_TYPE));
    }

    /**
     * The Content-Type is carried only as a soft hint. The unconditional override keys
     * must never be carried, or a caller could force any type past detection.
     */
    @Test
    public void testDoesNotCarryOverrides() {
        Metadata tuple = new Metadata();
        tuple.set(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, "image/x-raw-nikon");
        tuple.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, "image/x-raw-nikon");

        Metadata target = new Metadata();
        PipesWorker.carryCallerHints(tuple, target);

        assertNull(target.get(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE));
        assertNull(target.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE));
        assertNull(target.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testNullTupleIsNoOp() {
        Metadata target = new Metadata();
        target.set(TikaCoreProperties.RESOURCE_NAME_KEY, "keep.me");
        PipesWorker.carryCallerHints(null, target);
        assertEquals("keep.me", target.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testBlankValuesNotCarried() {
        Metadata tuple = new Metadata();
        tuple.set(TikaCoreProperties.RESOURCE_NAME_KEY, "   ");
        tuple.set(HttpHeaders.CONTENT_TYPE, "");

        Metadata target = new Metadata();
        PipesWorker.carryCallerHints(tuple, target);

        assertNull(target.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertNull(target.get(HttpHeaders.CONTENT_TYPE));
    }

    //content that magic-detects as image/tiff (little-endian TIFF marker, no CR2 marker)
    private static final byte[] TIFF_BYTES = {'I', 'I', 0x2A, 0x00, 0, 0, 0, 8};

    private static MediaType detectWithCarriedContentType(String contentType) throws Exception {
        Metadata tuple = new Metadata();
        tuple.set(HttpHeaders.CONTENT_TYPE, contentType);
        Metadata target = new Metadata();
        PipesWorker.carryCallerHints(tuple, target);
        try (TikaInputStream tis = TikaInputStream.get(TIFF_BYTES)) {
            return MimeTypes.getDefaultMimeTypes().detect(tis, target, new ParseContext());
        }
    }

    /**
     * A carried Content-Type that specializes the content-detected type refines detection.
     * image/x-canon-cr2 is a sub-class-of image/tiff, and these bytes lack the CR2 marker.
     */
    @Test
    public void testSpecializingContentTypeRefinesDetection() throws Exception {
        assertEquals(MediaType.image("x-canon-cr2"),
                detectWithCarriedContentType("image/x-canon-cr2"));
    }

    /**
     * Security boundary: a carried Content-Type that does NOT specialize the content-detected
     * type is ignored, so a caller cannot force an unrelated type onto the document.
     */
    @Test
    public void testNonSpecializingContentTypeIgnored() throws Exception {
        assertEquals(MediaType.image("tiff"), detectWithCarriedContentType("audio/mpeg"));
        assertEquals(MediaType.image("tiff"), detectWithCarriedContentType("not-a-media-type"));
    }
}
