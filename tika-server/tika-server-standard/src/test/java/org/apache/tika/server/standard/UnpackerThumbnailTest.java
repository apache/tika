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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.config.JsonConfigHelper;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/**
 * {@code /unpack/thumbnail} end to end: the document thumbnail comes back as
 * JSON with its metadata and the image as base64.
 */
public class UnpackerThumbnailTest extends CXFTestBase {

    private static final String THUMBNAIL_PATH = "/unpack/thumbnail";
    private static final String UNPACK_CONFIG_TEMPLATE = "/configs/cxf-unpack-test-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path unpackTempDir;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class, RecursiveMetadataResource.class);
        sf.setResourceProvider(UnpackerResource.class,
                new SingletonResourceProvider(new UnpackerResource(tikaResource)));
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        unpackTempDir = Files.createTempDirectory("tika-unpack-thumbnail-test-");
        Path pluginsDir = Paths.get("target/plugins").toAbsolutePath();
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UNPACK_EMITTER_BASE_PATH", unpackTempDir.toAbsolutePath().toString());
        replacements.put("PLUGINS_PATHS", pluginsDir.toString().replace("\\", "/"));
        replacements.put("TIMEOUT_MILLIS", 60000L);
        JsonNode config = JsonConfigHelper.loadFromResource(UNPACK_CONFIG_TEMPLATE,
                CXFTestBase.class, replacements);
        return new ByteArrayInputStream(
                MAPPER.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Path getUnpackEmitterBasePath() {
        return unpackTempDir;
    }

    /**
     * A stored thumbnail (the docProps thumbnail of a presentation).
     */
    @Test
    public void testStoredThumbnail() throws Exception {
        JsonNode json = thumbnail("test-documents/testPPTX_Thumbnail.pptx");
        JsonNode metadata = json.get("metadata");
        assertEquals("image/jpeg", metadata.get("Content-Type").asText());
        assertEquals("THUMBNAIL", metadata.get("tk:embedded-resource-type").asText());
        assertEquals("1", metadata.get("tk:embedded-depth").asText());
        BufferedImage image = decode(json);
        assertEquals(metadata.get("tiff:ImageWidth").asInt(), image.getWidth());
    }

    /**
     * A camera raw file: the largest embedded JPEG preview.
     */
    @Test
    public void testRawPreview() throws Exception {
        JsonNode json = thumbnail("test-documents/testNEF.nef");
        JsonNode metadata = json.get("metadata");
        assertEquals("image/jpeg", metadata.get("Content-Type").asText());
        assertEquals("THUMBNAIL", metadata.get("tk:embedded-resource-type").asText());
        assertEquals(64, decode(json).getWidth());
    }

    /**
     * A PDF has no thumbnail; with renderThumbnails the rendering of its first
     * page stands in, without it there is nothing.
     */
    @Test
    public void testPdfPageRendering() throws Exception {
        Response plain = WebClient.create(endPoint + THUMBNAIL_PATH)
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));
        assertEquals(204, plain.getStatus());

        JsonNode json = thumbnail("test-documents/testPDFTwoTextBoxes.pdf?renderThumbnails=true");
        JsonNode metadata = json.get("metadata");
        assertEquals("image/png", metadata.get("Content-Type").asText());
        assertEquals("RENDERING", metadata.get("tk:embedded-resource-type").asText());
        assertEquals("1", metadata.get("tk:page:number").asText());
        assertTrue(decode(json).getWidth() > 100);
    }

    /**
     * A document without a thumbnail: no content, no error.
     */
    @Test
    public void testNoThumbnail() throws Exception {
        Response response = WebClient.create(endPoint + THUMBNAIL_PATH)
                .put(ClassLoader.getSystemResourceAsStream("test-documents/2pic.docx"));
        assertEquals(204, response.getStatus());
    }

    /**
     * {@code /rmeta?renderThumbnails=true} lays the thumbnail defaults under a
     * normal metadata request: the first page rendering joins the list, the
     * text is still extracted. Without the switch nothing is rendered.
     */
    @Test
    public void testRmetaRenderThumbnails() throws Exception {
        JsonNode plain = rmeta("test-documents/testPDFTwoTextBoxes.pdf", false);
        assertEquals(1, plain.size());

        JsonNode rendered = rmeta("test-documents/testPDFTwoTextBoxes.pdf", true);
        assertEquals(2, rendered.size());
        assertTrue(rendered.get(0).get(TikaCoreProperties.TIKA_CONTENT.getName()).asText()
                .contains("Left column"), rendered.get(0).toString());
        JsonNode rendering = rendered.get(1);
        assertEquals("image/png", rendering.get("Content-Type").asText());
        assertEquals("RENDERING", rendering.get("tk:embedded-resource-type").asText());
        assertEquals("1", rendering.get("tk:page:number").asText());
        assertTrue(rendering.get("tiff:ImageWidth").asInt() > 100);
    }

    private JsonNode rmeta(String resource, boolean renderThumbnails) throws Exception {
        Response response = WebClient.create(endPoint + "/rmeta/text"
                        + (renderThumbnails ? "?renderThumbnails=true" : ""))
                .put(ClassLoader.getSystemResourceAsStream(resource));
        assertEquals(200, response.getStatus());
        return MAPPER.readTree((InputStream) response.getEntity());
    }

    /**
     * Sends the file name along, as a client would: raw camera formats are
     * detected by their extension.
     */
    private JsonNode thumbnail(String resource) throws Exception {
        String query = "";
        int q = resource.indexOf('?');
        if (q >= 0) {
            query = resource.substring(q);
            resource = resource.substring(0, q);
        }
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        Response response = WebClient.create(endPoint + THUMBNAIL_PATH + query)
                .header("Content-Disposition", "attachment; filename=" + fileName)
                .put(ClassLoader.getSystemResourceAsStream(resource));
        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        return MAPPER.readTree((InputStream) response.getEntity());
    }

    private static BufferedImage decode(JsonNode json) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(json.get("image").asText());
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
