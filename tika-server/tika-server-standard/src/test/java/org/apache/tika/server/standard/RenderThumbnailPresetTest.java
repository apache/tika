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
package org.apache.tika.server.standard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.core.extractor.ThumbnailUnpackSelector;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/**
 * The catalog preset {@code render-thumbnail} adds the thumbnail renders to a full parse, and
 * the documented client rule finds the image {@code thumbnail} would have unpacked (TIKA-4856).
 */
public class RenderThumbnailPresetTest extends CXFTestBase {

    private static final String PRESET_PATH = "/rmeta/preset/render-thumbnail";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    /** The default pipes config carries the "presets" block into the worker. */
    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/configs/tika-config-thumbnail-preset.json");
    }

    @Test
    public void testPdfKeepsItsTextAndRendersPageOne() throws Exception {
        List<Metadata> metadataList = rmeta("testPDF_bookmarks.pdf");
        Metadata pdf = metadataList.get(0);
        assertEquals(2, (int) pdf.getInt(PagedText.N_PAGES));
        assertContains("page about Denmark", pdf.get(TikaCoreProperties.TIKA_CONTENT)); // page 2

        Metadata thumbnail = thumbnail(metadataList);
        assertEquals("RENDERING", thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(1, (int) thumbnail.getInt(TikaCoreProperties.EMBEDDED_DEPTH));
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        int width = thumbnail.getInt(TIFF.IMAGE_WIDTH);
        int height = thumbnail.getInt(TIFF.IMAGE_LENGTH);
        assertTrue(width <= 256 && height <= 256 && height >= 255, width + "x" + height
                + ": the index sees the boxed image /unpack/preset/thumbnail returns");
        assertEquals(1, renderings(metadataList), "only the first page is rendered");
    }

    @Test
    public void testDocxThumbnailIsRasterized() throws Exception {
        Metadata thumbnail = thumbnail(rmeta("testDOCX_Thumbnail.docx"));
        assertEquals("THUMBNAIL", thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(2, (int) thumbnail.getInt(TikaCoreProperties.EMBEDDED_DEPTH));
        assertNotNull(thumbnail.get(Rendering.RENDERED_BY));
        assertNotNull(thumbnail.getInt(TIFF.IMAGE_WIDTH));
    }

    /** The preset's maxDepth: an attached PDF is not rendered, since the rule would refuse its page. */
    @Test
    public void testAttachmentsAreNotRendered() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip);
                InputStream pdf = ClassLoader.getSystemResourceAsStream(
                        "test-documents/testPDF_bookmarks.pdf")) {
            out.putNextEntry(new ZipEntry("attached.pdf"));
            pdf.transferTo(out);
            out.closeEntry();
        }
        List<Metadata> metadataList = rmeta(new ByteArrayInputStream(zip.toByteArray()), "zip");
        assertEquals(2, metadataList.size(), "the zip and the PDF, no render");
        assertEquals(0, renderings(metadataList));
    }

    private List<Metadata> rmeta(String file) throws Exception {
        return rmeta(ClassLoader.getSystemResourceAsStream("test-documents/" + file), file);
    }

    private List<Metadata> rmeta(InputStream document, String label) throws Exception {
        Response response = WebClient.create(endPoint + PRESET_PATH)
                .accept("application/json")
                .put(document);
        assertEquals(200, response.getStatus(), label);
        return JsonMetadataList.fromJson(
                new InputStreamReader((InputStream) response.getEntity(), UTF_8));
    }

    /** The documented client rule, as the selector implements it. */
    private static Metadata thumbnail(List<Metadata> metadataList) {
        for (Metadata m : metadataList) {
            if (ThumbnailUnpackSelector.isCandidate(m)) {
                return m;
            }
        }
        throw new AssertionError("no thumbnail candidate in " + metadataList.size() + " documents");
    }

    private static long renderings(List<Metadata> metadataList) {
        return metadataList.stream()
                .filter(m -> "RENDERING".equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)))
                .count();
    }
}
