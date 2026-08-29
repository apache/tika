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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class ThumbnailSelectorTest {

    @Test
    public void testRasterThumbnailWins() {
        Metadata inline = embedded("INLINE", "image/png", 1, "/image1.png");
        Metadata thumbnail = embedded("THUMBNAIL", "image/jpeg", 1, "/thumbnail.jpeg");
        Metadata rendering = embedded("RENDERING", "image/png", 1, "/page-1.png");
        assertSame(thumbnail, ThumbnailSelector.select(Arrays.asList(inline, rendering, thumbnail)));
    }

    @Test
    public void testVectorThumbnailFallsBackToItsRendering() {
        Metadata emf = embedded("THUMBNAIL", "image/emf", 1, "/thumbnail.emf");
        Metadata wmfInside = embedded("ATTACHMENT", "image/wmf", 2, "/thumbnail.emf/embedded-1.wmf");
        Metadata rendering = embedded("RENDERING", "image/png", 2, "/thumbnail.emf/thumbnail.png");
        Metadata otherRendering = embedded("RENDERING", "image/png", 2, "/embedded-1.emf/rendering.png");
        assertSame(rendering,
                ThumbnailSelector.select(Arrays.asList(emf, wmfInside, otherRendering, rendering)));
    }

    @Test
    public void testVectorThumbnailWithoutRenderingIsNotReturned() {
        Metadata emf = embedded("THUMBNAIL", "image/emf", 1, "/thumbnail.emf");
        assertNull(ThumbnailSelector.select(Collections.singletonList(emf)));
    }

    @Test
    public void testPageRenderingAsLastResort() {
        Metadata page = embedded("RENDERING", "image/png", 1, "/page-1.png");
        Metadata objectPicture = embedded("RENDERING", "image/png", 2, "/embedded-1.emf/rendering.png");
        assertSame(page, ThumbnailSelector.select(Arrays.asList(objectPicture, page)));
    }

    @Test
    public void testNestedThumbnailIsNotTheContainers() {
        //the thumbnail of a document inside a zip
        Metadata nested = embedded("THUMBNAIL", "image/jpeg", 2, "/doc.docx/thumbnail.jpeg");
        assertNull(ThumbnailSelector.select(Collections.singletonList(nested)));
    }

    @Test
    public void testNothingSuitable() {
        Metadata attachment = embedded("ATTACHMENT", "application/pdf", 1, "/a.pdf");
        Metadata inline = embedded("INLINE", "image/png", 1, "/image1.png");
        assertNull(ThumbnailSelector.select(Arrays.asList(attachment, inline)));
        assertNull(ThumbnailSelector.select(Collections.emptyList()));
    }

    private static Metadata embedded(String type, String contentType, int depth, String path) {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, type);
        m.set(HttpHeaders.CONTENT_TYPE, contentType);
        m.set(TikaCoreProperties.EMBEDDED_DEPTH, depth);
        m.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, path);
        return m;
    }
}
