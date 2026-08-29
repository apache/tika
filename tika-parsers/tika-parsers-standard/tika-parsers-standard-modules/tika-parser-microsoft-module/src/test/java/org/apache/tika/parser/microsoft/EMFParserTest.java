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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class EMFParserTest extends TikaTest {


    @Test
    public void testTextExtractionMac() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        Metadata emfMetadata = metadataList.get(2);
        assertEquals("image/emf", emfMetadata.get(HttpHeaders.CONTENT_TYPE));
        assertContains("is a toolkit for detecting",
                emfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        //test that a space was inserted before url
        assertContains("Tika http://incubator.apache.org/tika/",
                emfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testIconOnly() throws Exception {
        String fullFileName = "some word doc with a very long name that should be wrapped.docx";
        //test file contributed by Ross Spencer on TIKA-3968
        List<Metadata> metadataList = getRecursiveMetadata("testEMF_iconOnlyLongFilename.emf");
        assertEquals("true", metadataList.get(0).get(EMFParser.EMF_ICON_ONLY));
        assertEquals(fullFileName, metadataList.get(0).get(EMFParser.EMF_ICON_STRING));
        assertContains("some word doc", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("a very long name that should be wrapped.docx",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    /**
     * Rendering is off by default: an EMF yields no embedded document of
     * its own.
     */
    @Test
    public void testNoRenderingByDefault() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEMF.emf");
        assertEquals(1, metadataList.size());
    }

    @Test
    public void testRenderingFromConfig() throws Exception {
        Parser parser = TikaLoader
                .load(getConfigPath(EMFParserTest.class, "tika-config-emf-render.json"))
                .loadParsers();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testEMF.emf");
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/emf");
        List<Metadata> metadataList =
                getRecursiveMetadata("testEMF.emf", parser, metadata, new ParseContext(), false);
        assertEquals(2, metadataList.size());
        assertRendering(metadataList.get(1), "testEMF.png");
    }

    /**
     * The per-request form: the parser config is supplied through the
     * ParseContext, as tika-server does for a multipart config part.
     */
    @Test
    public void testRenderingFromParseContext() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("emf-parser", "{\"renderImage\": true}");
        List<Metadata> metadataList = getRecursiveMetadata("testEMF.emf", context);
        assertEquals(2, metadataList.size());
        assertRendering(metadataList.get(1), "testEMF.png");
        assertEquals("1", metadataList.get(1).get(TikaCoreProperties.EMBEDDED_DEPTH));
    }

    /**
     * The docProps thumbnail of a Word document is an EMF; with rendering on
     * its PNG rendering follows it, one level deeper.
     */
    @Test
    public void testDocxThumbnailRendering() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("emf-parser", "{\"renderImage\": true, \"renderWidth\": 200}");
        List<Metadata> metadataList = getRecursiveMetadata("testDOCX_Thumbnail.docx", context);
        //the document, its thumbnail, the WMF picture inside the thumbnail's
        //EMF and the thumbnail's rendering
        assertEquals(4, metadataList.size());
        Metadata thumbnail = byName(metadataList, "thumbnail.emf");
        assertEquals("image/emf", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("1", thumbnail.get(TikaCoreProperties.EMBEDDED_DEPTH));
        Metadata rendering = byName(metadataList, "thumbnail.png");
        assertRendering(rendering, "thumbnail.png");
        assertEquals("2", rendering.get(TikaCoreProperties.EMBEDDED_DEPTH));
        assertEquals("/thumbnail.emf/thumbnail.png",
                rendering.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }

    /**
     * Restricted to THUMBNAIL embedded documents, the parser renders the
     * document's thumbnail but not a picture that is merely embedded.
     */
    @Test
    public void testRenderOnlyThumbnails() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("emf-parser",
                "{\"renderImage\": true, \"renderOnlyEmbeddedResourceTypes\": [\"THUMBNAIL\"]}");
        List<Metadata> metadataList = getRecursiveMetadata("testDOCX_Thumbnail.docx", context);
        assertRendering(byName(metadataList, "thumbnail.png"), "thumbnail.png");

        //a bare EMF is the document itself, not a THUMBNAIL: no rendering
        metadataList = getRecursiveMetadata("testEMF.emf", context);
        assertEquals(1, metadataList.size());
    }

    private static Metadata byName(List<Metadata> metadataList, String name) {
        for (Metadata m : metadataList) {
            if (name.equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
                return m;
            }
        }
        throw new AssertionError("no embedded document named " + name);
    }

    /**
     * There is no image parser on this module's test classpath, so the PNG
     * is checked by its type, name and size rather than its dimensions.
     */
    private static void assertRendering(Metadata rendering, String name) {
        assertEquals("image/png", rendering.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.RENDERING.name(),
                rendering.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(name, rendering.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("poi-metafile-renderer", rendering.get(Rendering.RENDERED_BY));
        assertTrue(Long.parseLong(rendering.get(HttpHeaders.CONTENT_LENGTH)) > 100);
        assertNull(rendering.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    @Test
    public void testMissingCoords() throws Exception {
        //TIKA-4432
        List<Metadata> metadataList = getRecursiveMetadata("testEMF_zero_coords.emf");
        String txt = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertNotContained("title13At", txt);
        assertContains("Presentation title 13", txt);
        assertContains("<p>At Contoso", txt);
        assertContains("next-generation", txt);//this is stored in three records -- test that no spaces are interpolated
    }


}
