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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class WMFParserTest extends TikaTest {

    @Test
    public void testTextExtractionWindows() throws Exception {
        testTextExtraction("testXLSX_Thumbnail.xlsx", 1,
                "This file contains an embedded thumbnail");
    }

    @Test
    public void testTextExtractionShiftJISencoding() throws Exception {
        testTextExtraction("testWMF_charset.wmf", 0, "普林斯");
    }

    /**
     * Rendering is off by default; with "wmf-parser": {"renderImage": true}
     * the rendering follows the image as a RENDERING embedded document.
     */
    @Test
    public void testRendering() throws Exception {
        assertEquals(1, getRecursiveMetadata("testWMF.wmf").size());

        ParseContext context = new ParseContext();
        context.setJsonConfig("wmf-parser", "{\"renderImage\": true, \"renderWidth\": 300}");
        List<Metadata> metadataList = getRecursiveMetadata("testWMF.wmf", context);
        assertEquals(2, metadataList.size());
        Metadata rendering = metadataList.get(1);
        assertEquals("image/png", rendering.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.RENDERING.name(),
                rendering.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("testWMF.png", rendering.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("poi-metafile-renderer", rendering.get(Rendering.RENDERED_BY));
        assertTrue(Long.parseLong(rendering.get(HttpHeaders.CONTENT_LENGTH)) > 100);
    }

    /**
     * The docProps thumbnail of this workbook is a WMF; with rendering on its
     * PNG rendering follows it, one level deeper.
     */
    @Test
    public void testXlsxThumbnailRendering() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("wmf-parser", "{\"renderImage\": true}");
        List<Metadata> metadataList = getRecursiveMetadata("testXLSX_Thumbnail.xlsx", context);
        Metadata thumbnail = null;
        Metadata rendering = null;
        for (Metadata m : metadataList) {
            String type = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name().equals(type)) {
                thumbnail = m;
            } else if (TikaCoreProperties.EmbeddedResourceType.RENDERING.name().equals(type)) {
                rendering = m;
            }
        }
        assertNotNull(thumbnail);
        assertEquals("image/wmf", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("1", thumbnail.get(TikaCoreProperties.EMBEDDED_DEPTH));
        assertNotNull(rendering);
        assertEquals("image/png", rendering.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("2", rendering.get(TikaCoreProperties.EMBEDDED_DEPTH));
        assertEquals("thumbnail.png", rendering.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    private void testTextExtraction(String fileName, int metaDataItemIndex, String expectedText)
            throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata(fileName);
        Metadata wmfMetadata = metadataList.get(metaDataItemIndex);

        assertEquals("image/wmf", wmfMetadata.get(HttpHeaders.CONTENT_TYPE));
        assertContains(expectedText, wmfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

}
