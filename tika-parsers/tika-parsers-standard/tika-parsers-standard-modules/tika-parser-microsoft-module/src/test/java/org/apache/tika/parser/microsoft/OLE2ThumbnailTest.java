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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * The thumbnail that Office stores in the SummaryInformation of the OLE2
 * formats (PIDSI_THUMBNAIL, a WMF) is emitted as a THUMBNAIL embedded
 * document, as the docProps thumbnail of the OOXML formats is.
 */
public class OLE2ThumbnailTest extends TikaTest {

    @Test
    public void testPptThumbnail() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_various.ppt");
        Metadata thumbnail = byType(metadataList, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL);
        assertNotNull(thumbnail);
        assertEquals("image/wmf", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("thumbnail.wmf", thumbnail.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("1", thumbnail.get(TikaCoreProperties.EMBEDDED_DEPTH));
        //exactly one document thumbnail
        assertEquals(1, count(metadataList, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL));
    }

    @Test
    public void testPptThumbnailRendering() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("wmf-parser", "{\"renderImage\": true, \"renderWidth\": 400}");
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_various.ppt", context);
        Metadata rendering = byType(metadataList, TikaCoreProperties.EmbeddedResourceType.RENDERING);
        assertNotNull(rendering);
        assertEquals("image/png", rendering.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("thumbnail.png", rendering.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("2", rendering.get(TikaCoreProperties.EMBEDDED_DEPTH));
        assertTrue(Long.parseLong(rendering.get(HttpHeaders.CONTENT_LENGTH)) > 100);
    }

    /**
     * Word wraps its thumbnail bitmap in a WMF with a window extent and a
     * single dibStretchBlt record, which has no bounds POI can compute; the
     * renderer falls back to the bitmap.
     */
    @Test
    public void testDocThumbnailRendering() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("wmf-parser", "{\"renderImage\": true}");
        List<Metadata> metadataList = getRecursiveMetadata("testControlCharacters.doc", context);
        Metadata thumbnail = byType(metadataList, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL);
        assertNotNull(thumbnail);
        assertEquals("image/wmf", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        Metadata rendering = byType(metadataList, TikaCoreProperties.EmbeddedResourceType.RENDERING);
        assertNotNull(rendering);
        assertEquals("image/png", rendering.get(HttpHeaders.CONTENT_TYPE));
        assertNull(thumbnail.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    /**
     * A thumbnail that is not a metafile picture is left alone.
     */
    @Test
    public void testUnusableThumbnailIsSkipped() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        assertEquals(0, count(metadataList, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL));
    }

    private static Metadata byType(List<Metadata> metadataList,
                                   TikaCoreProperties.EmbeddedResourceType type) {
        for (Metadata m : metadataList) {
            if (type.name().equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))) {
                return m;
            }
        }
        return null;
    }

    private static int count(List<Metadata> metadataList,
                             TikaCoreProperties.EmbeddedResourceType type) {
        int n = 0;
        for (Metadata m : metadataList) {
            if (type.name().equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))) {
                n++;
            }
        }
        return n;
    }
}
