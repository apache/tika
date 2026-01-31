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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

/**
 * Tests for UnpackerResource with custom tika-config that enables PDF rendering.
 * Uses pipes-based UNPACK mode.
 */
public class UnpackerResourceWithConfigTest extends CXFTestBase {
    private static final String BASE_PATH = "/unpack";
    private static final String ALL_PATH = BASE_PATH + "/all";

    private static final String UNPACK_CONFIG_TEMPLATE = "/configs/cxf-unpack-test-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path unpackTempDir;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class, new SingletonResourceProvider(new UnpackerResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return this.getClass().getResourceAsStream("/configs/tika-config-unpacker.json");
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        // Create temp directory for unpack emitter
        unpackTempDir = Files.createTempDirectory("tika-unpack-test-");

        Path pluginsDir = Paths.get("target/plugins").toAbsolutePath();

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UNPACK_EMITTER_BASE_PATH", unpackTempDir.toAbsolutePath().toString());
        replacements.put("PLUGINS_PATHS", pluginsDir.toString().replace("\\", "/"));
        replacements.put("TIMEOUT_MILLIS", 60000L);

        JsonNode config = JsonConfigHelper.loadFromResource(UNPACK_CONFIG_TEMPLATE,
                CXFTestBase.class, replacements);
        String json = MAPPER.writeValueAsString(config);
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Path getUnpackEmitterBasePath() {
        return unpackTempDir;
    }

    /**
     * Test that the PDFParser's renderer can be configured at parse time
     * when specified in tika-config.json.
     * POST to /unpack/all (not /unpack/all/config - URL changed in 4.0)
     */
    @Test
    public void testPDFPerPageRenderColor() throws Exception {
        // Default is gray scale png; change to rgb and tiff
        String configJson = """
                {
                  "pdf-parser": {
                    "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                    "ocrImageType": "RGB",
                    "ocrImageFormat": "TIFF"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testColorRendering.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testColorRendering.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        // URL changed: POST to /unpack/all instead of /unpack/all/config
        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        Map<String, byte[]> results = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] renderedImage = null;
        for (Map.Entry<String, byte[]> e : results.entrySet()) {
            // With new naming, look for TIFF files
            if (e.getKey().endsWith(".tiff") || e.getKey().endsWith(".tif")) {
                renderedImage = e.getValue();
                break;
            }
        }
        assertNotNull(renderedImage, "Should have a rendered TIFF image");

        try (TikaInputStream tis = TikaInputStream.get(renderedImage)) {
            assertEquals("image/tiff", TikaLoader
                    .loadDefault()
                    .loadDetectors()
                    .detect(tis, new Metadata(), new ParseContext())
                    .toString());
        }

        try (InputStream is = new ByteArrayInputStream(renderedImage)) {
            BufferedImage image = ImageIO.read(is);
            // Top left should be red
            AverageColor averageColor = getAverageColor(image, 0, image.getWidth() / 5, 0, image.getHeight() / 10);
            assertTrue(averageColor.getRed() > 250);
            assertTrue(averageColor.getGreen() < 1);
            assertTrue(averageColor.getBlue() < 1);

            // Bottom left should be green
            averageColor = getAverageColor(image, 0, image.getWidth() / 5,
                    image.getHeight() / 2 + image.getHeight() / 10,
                    image.getHeight() / 2 + 2 * image.getHeight() / 10);
            assertTrue(averageColor.getRed() < 1);
            assertTrue(averageColor.getGreen() > 250);
            assertTrue(averageColor.getBlue() < 1);

            // Bottom right should be blue
            averageColor = getAverageColor(image,
                    image.getWidth() / 2 + image.getWidth() / 10,
                    image.getWidth() / 2 + 2 * image.getWidth() / 10,
                    image.getHeight() / 2 + image.getHeight() / 10,
                    image.getHeight() / 2 + 2 * image.getHeight() / 10);
            assertTrue(averageColor.getRed() < 1);
            assertTrue(averageColor.getGreen() < 1);
            assertTrue(averageColor.getBlue() > 250);
        }
    }

    @Test
    public void testPDFPerPageRenderGray() throws Exception {
        String configJson = """
                {
                  "pdf-parser": {
                    "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                    "ocrImageType": "GRAY",
                    "ocrImageFormat": "JPEG"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testColorRendering.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testColorRendering.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        // URL changed: POST to /unpack/all instead of /unpack/all/config
        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        Map<String, byte[]> results = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] renderedImage = null;
        for (Map.Entry<String, byte[]> e : results.entrySet()) {
            // With new naming, look for JPEG files
            if (e.getKey().endsWith(".jpg") || e.getKey().endsWith(".jpeg")) {
                renderedImage = e.getValue();
                break;
            }
        }
        assertNotNull(renderedImage, "Should have a rendered JPEG image");

        try (TikaInputStream tis = TikaInputStream.get(renderedImage)) {
            assertEquals("image/jpeg", TikaLoader
                    .loadDefault()
                    .loadDetectors()
                    .detect(tis, new Metadata(), new ParseContext())
                    .toString());
        }

        try (InputStream is = new ByteArrayInputStream(renderedImage)) {
            BufferedImage image = ImageIO.read(is);
            // Top left - grayscale red
            AverageColor averageColor = getAverageColor(image, 0, image.getWidth() / 5, 0, image.getHeight() / 10);
            assertTrue(averageColor.getRed() > 140 && averageColor.getRed() < 160);
            assertTrue(averageColor.getGreen() > 140 && averageColor.getGreen() < 160);
            assertTrue(averageColor.getBlue() > 140 && averageColor.getBlue() < 160);

            // Bottom left - grayscale green
            averageColor = getAverageColor(image, 0, image.getWidth() / 5,
                    image.getHeight() / 2 + image.getHeight() / 10,
                    image.getHeight() / 2 + 2 * image.getHeight() / 10);
            assertTrue(averageColor.getRed() < 210 && averageColor.getRed() > 190);
            assertTrue(averageColor.getGreen() < 210 && averageColor.getGreen() > 190);
            assertTrue(averageColor.getBlue() < 210 && averageColor.getBlue() > 190);

            // Bottom right - grayscale blue
            averageColor = getAverageColor(image,
                    image.getWidth() / 2 + image.getWidth() / 10,
                    image.getWidth() / 2 + 2 * image.getWidth() / 10,
                    image.getHeight() / 2 + image.getHeight() / 10,
                    image.getHeight() / 2 + 2 * image.getHeight() / 10);
            assertTrue(averageColor.getRed() < 100 && averageColor.getRed() > 90);
            assertTrue(averageColor.getGreen() < 100 && averageColor.getGreen() > 90);
            assertTrue(averageColor.getBlue() < 100 && averageColor.getBlue() > 90);
        }
    }
}
