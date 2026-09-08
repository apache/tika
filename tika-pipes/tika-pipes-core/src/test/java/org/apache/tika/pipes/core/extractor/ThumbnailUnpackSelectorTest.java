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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class ThumbnailUnpackSelectorTest {

    @Test
    public void testCandidates() {
        ThumbnailUnpackSelector selector = new ThumbnailUnpackSelector();
        assertTrue(selector.select(metadata("image/jpeg", "THUMBNAIL", 1)), "stored thumbnail");
        assertTrue(selector.select(metadata("image/png", "THUMBNAIL", 2)),
                "rendering of a vector thumbnail");
        assertTrue(selector.select(metadata("image/png", "RENDERING", 1)), "first page render");
        assertTrue(selector.select(metadata("image/png; charset=binary", "THUMBNAIL", 1)));
        assertFalse(selector.select(metadata("image/emf", "THUMBNAIL", 1)), "vector thumbnail");
        assertFalse(selector.select(metadata("image/png", "RENDERING", 2)),
                "render of an embedded document");
        assertFalse(selector.select(metadata("image/png", "THUMBNAIL", 3)));
        assertFalse(selector.select(metadata("image/png", "INLINE", 1)));
        assertFalse(selector.select(metadata("application/pdf", "ATTACHMENT", 1)));
        assertFalse(selector.select(metadata(null, "THUMBNAIL", 1)));
        Metadata noDepth = metadata("image/png", "THUMBNAIL", 1);
        noDepth.remove(TikaCoreProperties.EMBEDDED_DEPTH.getName());
        assertFalse(selector.select(noDepth));
    }

    @Test
    public void testFirstWinsPerParse() {
        ThumbnailUnpackSelector selector = new ThumbnailUnpackSelector();
        ParseContext parse = new ParseContext();
        assertFalse(selector.select(metadata("image/png", "INLINE", 1), parse),
                "a rejected candidate does not take the slot");
        assertTrue(selector.select(metadata("image/jpeg", "THUMBNAIL", 1), parse));
        assertFalse(selector.select(metadata("image/jpeg", "THUMBNAIL", 1), parse),
                "second thumbnail of the same parse");
        assertTrue(selector.select(metadata("image/png", "RENDERING", 1), new ParseContext()),
                "a shared selector starts over on the next parse");
    }

    private static Metadata metadata(String contentType, String type, int depth) {
        Metadata metadata = new Metadata();
        if (contentType != null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, type);
        metadata.set(TikaCoreProperties.EMBEDDED_DEPTH, depth);
        return metadata;
    }
}
