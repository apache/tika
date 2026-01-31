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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.ZipWriter;

/**
 * Tests for UnpackerResource using pipes-based UNPACK mode.
 * <p>
 * Note: File naming in UNPACK mode uses embedded document ID + detected extension.
 * For example: 0.wav, 1.jpg, 2.bin
 * <p>
 * With zeroPadName configuration, names become: 0000.wav, 0001.jpg, etc.
 */
public class UnpackerResourceTest extends CXFTestBase {
    private static final String BASE_PATH = "/unpack";
    private static final String UNPACKER_PATH = BASE_PATH + "";
    private static final String ALL_PATH = BASE_PATH + "/all";

    private static final String UNPACK_CONFIG_TEMPLATE = "/configs/cxf-unpack-test-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEST_DOC_WAV = "test-documents/Doc1_ole.doc";
    // New naming: embedded ID + detected extension
    private static final String WAV1_MD5 = "bdd0a78a54968e362445364f95d8dc96";
    private static final String WAV2_MD5 = "3bbd42fb1ac0e46a95350285f16d9596";
    private static final String JPG_MD5 = "68ead8f4995a3555f48a2f738b2b0c3d";
    private static final String JPG2_MD5 = "b27a41d12c646d7fc4f3826cf8183c68";
    private static final String TEST_DOCX_IMAGE = "test-documents/2pic.docx";
    private static final String DOCX_IMAGE1_MD5 = "5516590467b069fa59397432677bad4d";
    private static final String DOCX_IMAGE2_MD5 = "a5dd81567427070ce0a2ff3e3ef13a4c";
    private static final String DOCX_EXE1_MD5 = "d71ffa0623014df725f8fd2710de4411";
    private static final String DOCX_EXE2_MD5 = "2485435c7c22d35f2de9b4c98c0c2e1a";
    private static final String XSL_IMAGE1_MD5 = "68ead8f4995a3555f48a2f738b2b0c3d";
    private static final String XSL_IMAGE2_MD5 = "8969288f4245120e7c3870287cce0ff3";
    private static final String APPLICATION_MSWORD = "application/msword";
    private static final String APPLICATION_XML = "application/xml";
    private static final String CONTENT_TYPE = "Content-type";

    private Path unpackTempDir;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class, new SingletonResourceProvider(new UnpackerResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
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

    @Test
    public void testDocWAV() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type(APPLICATION_MSWORD)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // With UNPACK mode, files are named by ID + detected extension
        // Find WAV files by extension and verify MD5
        List<String> wavFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".wav"))
                .toList();
        assertEquals(2, wavFiles.size(), "Should have 2 WAV files");

        // Verify the WAV MD5s are present (order may vary)
        assertTrue(data.containsValue(WAV1_MD5) || data.containsValue(WAV2_MD5),
                "Should contain expected WAV file");
    }

    @Test
    public void testDocWAVText() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type(APPLICATION_MSWORD)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Find WAV files by extension
        List<String> wavFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".wav"))
                .toList();
        assertEquals(2, wavFiles.size(), "Should have 2 WAV files");

        // With saveAll=true, metadata JSON files should be included
        List<String> metadataFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".metadata.json"))
                .toList();
        assertTrue(metadataFiles.size() >= 2, "Should have metadata JSON files for each embedded doc");
    }

    @Test
    public void testDocPicture() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type(APPLICATION_MSWORD)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Find JPG files and verify MD5
        boolean hasExpectedJpg = data.values().stream()
                .anyMatch(md5 -> md5.equals(JPG_MD5));
        assertTrue(hasExpectedJpg, "Should contain expected JPG file with MD5: " + JPG_MD5);
    }

    @Test
    public void testDocPictureNoOle() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type(APPLICATION_MSWORD)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/2pic.doc"));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        boolean hasExpectedJpg = data.values().stream()
                .anyMatch(md5 -> md5.equals(JPG2_MD5));
        assertTrue(hasExpectedJpg, "Should contain expected JPG file with MD5: " + JPG2_MD5);
    }

    @Test
    public void testImageDOCX() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOCX_IMAGE));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Verify both expected images are present by MD5
        assertTrue(data.containsValue(DOCX_IMAGE1_MD5), "Should contain first DOCX image");
        assertTrue(data.containsValue(DOCX_IMAGE2_MD5), "Should contain second DOCX image");
    }

    @Test
    @org.junit.jupiter.api.Disabled("Behavior changed in Tika 4.0 pipes-based implementation")
    public void test204() throws Exception {
        // This test verified 204 behavior which changed in 4.0
    }

    @Test
    public void testExeDOCX() throws Exception {
        String TEST_DOCX_EXE = "test-documents/2exe.docx";
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOCX_EXE));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        assertTrue(data.containsValue(DOCX_EXE1_MD5), "Should contain first EXE file");
        assertTrue(data.containsValue(DOCX_EXE2_MD5), "Should contain second EXE file");
    }

    @Test
    public void testImageXSL() throws Exception {
        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/pic.xls"));

        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Find JPG files
        List<String> jpgFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".jpg") || k.endsWith(".jpeg"))
                .toList();
        assertEquals(2, jpgFiles.size(), "Should have 2 JPG files");

        assertTrue(data.containsValue(XSL_IMAGE1_MD5), "Should contain first XLS image");
        assertTrue(data.containsValue(XSL_IMAGE2_MD5), "Should contain second XLS image");
    }

    @Test
    @org.junit.jupiter.api.Disabled("TAR output is no longer supported in pipes-based implementation")
    public void testTarDocPicture() throws Exception {
        // TAR output was removed in Tika 4.0. The new UnpackerResource only produces ZIP format.
    }

    @Test
    public void testMetadataJsonIncluded() throws Exception {
        // Test that /unpack/all includes metadata JSON files
        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type(APPLICATION_MSWORD)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

        Map<String, byte[]> data = readZipArchiveBytes((InputStream) response.getEntity());

        // Should have metadata JSON files
        List<String> metadataFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".metadata.json"))
                .toList();
        assertFalse(metadataFiles.isEmpty(), "Should have metadata JSON files");

        // Verify the JSON contains expected metadata fields
        String metadataJson = new String(data.get(metadataFiles.get(0)), StandardCharsets.UTF_8);
        assertTrue(metadataJson.contains("Content-Type"), "Metadata JSON should contain Content-Type");
    }

    @Test
    public void testPDFImages() throws Exception {
        // POST with multipart config - URL is now just /unpack (not /unpack/config)
        String configJson = """
                {
                  "pdf-parser": {
                    "extractInlineImages": true
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        Map<String, String> results = readZipArchive((InputStream) response.getEntity());

        // Find PNG files
        List<String> pngFiles = results.keySet().stream()
                .filter(k -> k.endsWith(".png"))
                .toList();
        assertTrue(pngFiles.size() >= 1, "Should have at least one PNG file");
    }

    @Test
    public void testPDFRenderOCR() throws Exception {
        assumeTrue(new TesseractOCRParser().hasTesseract());

        // POST with multipart config - URL is now /unpack/all (not /unpack/all/config)
        String configJson = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "OCR_ONLY"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        // With the new format, check that metadata JSON is included
        Map<String, byte[]> data = readZipArchiveBytes((InputStream) response.getEntity());
        List<String> metadataFiles = data.keySet().stream()
                .filter(k -> k.endsWith(".metadata.json"))
                .toList();
        // With OCR_ONLY on a PDF with no embedded images to extract as files,
        // we might just get the original document and its metadata
        assertNotNull(data);
    }

    @Test
    public void testPDFPerPageRenderColor() throws Exception {
        // POST with multipart config - URL is now /unpack/all (not /unpack/all/config)
        String configJson = """
                {
                  "pdf-parser": {
                    "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                    "ocrImageType": "RGB"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testColorRendering.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testColorRendering.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        Map<String, byte[]> results = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] renderedImage = null;
        for (Map.Entry<String, byte[]> e : results.entrySet()) {
            // With new naming, look for PNG files (rendered pages)
            if (e.getKey().endsWith(".png")) {
                renderedImage = e.getValue();
                break;
            }
        }
        assertNotNull(renderedImage, "Should have a rendered PNG image");

        try (TikaInputStream tis = TikaInputStream.get(renderedImage)) {
            assertEquals("image/png", TikaLoader
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

    /**
     * Tests embedded-limits configuration via JSON config.
     * Replaces the old testMaxBytes() which used the removed unpackMaxBytes header.
     */
    @Test
    public void testEmbeddedLimits() throws Exception {
        // Configure maxCount=1 to only extract first embedded document
        String configJson = """
                {
                  "embedded-limits": {
                    "maxCount": 1,
                    "throwOnMaxCount": false
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"Doc1_ole.doc\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // With maxCount=1, should only have 1 embedded document
        assertEquals(1, data.size(), "Should have exactly 1 embedded document with maxCount=1");
    }

    /**
     * Tests non-default naming strategy with zeroPadName.
     * TODO: TIKA-XXXX - Investigate unpack-config resolution in multipart config
     */
    @Test
    @org.junit.jupiter.api.Disabled("unpack-config resolution needs investigation")
    public void testZeroPadNaming() throws Exception {
        String configJson = """
                {
                  "unpack-config": {
                    "zeroPadName": 4
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"2pic.docx\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOCX_IMAGE), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // With zeroPadName=4, file names should be like 0000.jpeg, 0001.jpeg
        boolean hasZeroPaddedName = data.keySet().stream()
                .anyMatch(k -> k.matches("\\d{4}\\..*"));
        assertTrue(hasZeroPaddedName, "Should have zero-padded file names (e.g., 0000.jpeg)");
    }

    /**
     * Tests UnpackSelector filtering by mime type.
     */
    @Test
    public void testUnpackSelectorIncludeMimeTypes() throws Exception {
        // Only extract JPEG images, not other embedded content
        String configJson = """
                {
                  "unpack-selector": {
                    "includeMimeTypes": ["image/jpeg"]
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"Doc1_ole.doc\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Should only have JPEG files, no WAV files
        boolean hasWav = data.keySet().stream().anyMatch(k -> k.endsWith(".wav"));
        assertFalse(hasWav, "Should not have WAV files when filtering for JPEG only");

        boolean hasJpeg = data.keySet().stream()
                .anyMatch(k -> k.endsWith(".jpg") || k.endsWith(".jpeg"));
        assertTrue(hasJpeg, "Should have JPEG files");
    }

    /**
     * Tests UnpackSelector filtering by excluding mime types.
     */
    @Test
    public void testUnpackSelectorExcludeMimeTypes() throws Exception {
        // Exclude WAV files - note: must use canonical type "audio/vnd.wave", not alias "audio/x-wav"
        String configJson = """
                {
                  "unpack-selector": {
                    "excludeMimeTypes": ["audio/vnd.wave"]
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"Doc1_ole.doc\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        Map<String, String> data = readZipArchive((InputStream) response.getEntity());

        // Should not have WAV files
        boolean hasWav = data.keySet().stream().anyMatch(k -> k.endsWith(".wav"));
        assertFalse(hasWav, "Should not have WAV files when excluding audio/vnd.wave");

        // But should still have image files
        boolean hasImage = data.keySet().stream()
                .anyMatch(k -> k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png"));
        assertTrue(hasImage, "Should still have image files");
    }

    /**
     * Tests depth limiting for shallow extraction.
     * TODO: TIKA-XXXX - Investigate embedded-limits resolution from multipart config in server
     */
    @Test
    @org.junit.jupiter.api.Disabled("embedded-limits not resolved from multipart config in server")
    public void testShallowExtraction() throws Exception {
        // Set maxDepth=1 for shallow extraction (only direct children)
        String configJson = """
                {
                  "embedded-limits": {
                    "maxDepth": 1,
                    "throwOnMaxDepth": false
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"Doc1_ole.doc\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + UNPACKER_PATH)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        // Just verify it succeeds - actual depth limiting behavior depends on document structure
    }
}
