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
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_ENCODING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class TikaResourceTest extends CXFTestBase {
    public static final String TEST_DOC = "test-documents/test.doc";
    public static final String TEST_PASSWORD_PROTECTED = "test-documents/password.xls";
    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";
    private static final String TEST_OOM = "mock/fake_oom.xml";

    private static final String TIKA_PATH = "/tika";
    private static final int UNPROCESSEABLE = 422;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(new TikaResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/configs/tika-config-for-server-tests.json");
    }

    @Override
    protected InputStream getPipesConfigInputStream() {
        return getClass().getResourceAsStream("/configs/tika-config-for-server-tests.json");
    }

    @Test
    public void testHelloWorld() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .get();
        assertEquals(TikaResource.GREETING, getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/msword")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testWordGzipIn() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/msword")
                .encoding("gzip")
                .put(gzip(ClassLoader.getSystemResourceAsStream(TEST_DOC)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testLongGzipOut() throws Exception {
        //if the output is long enough, jax-rs will compress it, otherwise it won't
        //this output is long enough, and should be compressed
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .acceptEncoding("gzip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        assertTrue(response
                .getHeaders()
                .containsKey(CONTENT_ENCODING));
        assertEquals("gzip", response.getHeaderString(CONTENT_ENCODING));
        String responseMsg = getStringFromInputStream(new GzipCompressorInputStream((InputStream) response.getEntity()));
        assertTrue(responseMsg.contains("Course of human"));
    }

    @Test
    public void testShortGzipOut() throws Exception {
        //if the output is long enough, jax-rs will compress it, otherwise it won't
        //this output is short enough, and should not be compressed
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .acceptEncoding("gzip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        assertFalse(response
                .getHeaders()
                .containsKey(CONTENT_ENCODING));

        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLS() {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/vnd.ms-excel")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordMarkdown() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/md")
                .type("application/msword")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
        // Should not contain HTML/XML tags
        assertFalse(responseMsg.contains("<html"));
        assertFalse(responseMsg.contains("<body"));
        assertFalse(responseMsg.contains("<p>"));
    }

    @Test
    public void testSimpleWordHTML() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/html")
                .type("application/msword")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSHTML() {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/html")
                .type("application/vnd.ms-excel")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordXML() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/xml")
                .type("application/msword")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSXML() {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/xml")
                .type("application/vnd.ms-excel")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testJAXBAndActivationDependency() {
        //TIKA-2778
        AttachmentUtil.getCommandMap();
    }

    @Test
    public void testEmbedded() throws Exception {
        //first try text
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Course of human events"));

        //now go for xml -- different call than text
        response = WebClient
                .create(endPoint + TIKA_PATH + "/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Course of human events"));

    }

    //TIKA-1845
    @Test
    public void testWMFInRTF() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/rtf")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testRTF_npeFromWMFInTikaServer.rtf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Example text"));
    }

    //TIKA-2638 and TIKA-2816
    @Test
    public void testOCRLanguageConfig() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        String configJson = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "OCR_ONLY"
                  },
                  "tesseract-ocr-parser": {
                    "language": "eng+fra",
                    "minFileSizeToOcr": 10,
                    "maxFileSizeToOcr": 1000000000
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);
    }

    //TIKA-2290
    @Test
    public void testPDFOCRConfig() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        // Test no_ocr strategy - use /config/text to get plain text (no XHTML envelope)
        String configJson = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "NO_OCR"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        // With NO_OCR, the OCR text should not be present
        assertNotFound("Happy New Year 2003!", responseMsg);

        // Test Tesseract skipOcr via JSON config - use /config/text
        configJson = "{\"tesseract-ocr-parser\": {\"skipOcr\": true}}";
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        // With skipOcr=true, the OCR text should not be present
        assertNotFound("Happy New Year 2003!", responseMsg);

        // Test ocr_only strategy
        configJson = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "OCR_ONLY"
                  }
                }
                """;
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);

        // Test bad value - should return error
        configJson = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "non-sense-value"
                  }
                }
                """;
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        assertEquals(422, response.getStatus());
    }

    //TIKA-2669
    @Test
    public void testPDFConfig() throws Exception {
        // Test default behavior (sortByPosition=true from server config)
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/pdf")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg
                .replaceAll("[\r\n ]+", " ")
                .trim();
        assertEquals("Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2", responseMsg);

        // Test with sortByPosition=false via JSON config
        String configJson = """
                {
                  "pdf-parser": {
                    "sortByPosition": false
                  }
                }
                """;

        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.pdf\"");
        Attachment fileAtt = new Attachment("file", ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"), fileCd);

        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg
                .replaceAll("[\r\n ]+", " ")
                .trim();
        assertEquals("Left column line 1 Left column line 2 Right column line 1 Right column line 2", responseMsg);

        // Make sure that default reverts to initial config option
        response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .type("application/pdf")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg
                .replaceAll("[\r\n ]+", " ")
                .trim();
        assertEquals("Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2", responseMsg);
    }

    @Test
    public void testDataIntegrityCheck() throws Exception {
        // This test requires tesseract to be installed - the validation only happens
        // when TesseractOCRParser is actually invoked during parsing
        assumeTrue(new TesseractOCRParser().hasTesseract(), "Tesseract not installed, skipping test");

        // Test bad tesseract path with null byte - should be rejected
        String configJson = """
                {
                  "tesseract-ocr-parser": {
                    "tesseractPath": "C://tmp//hello.bat\u0000"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        try {
            Response response = WebClient
                    .create(endPoint + TIKA_PATH + "/config")
                    .type("multipart/form-data")
                    .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
            assertEquals(500, response.getStatus());
        } catch (ProcessingException e) {
            //can't tell why this intermittently happens. :(
            //started after the upgrade to 3.2.7
        }

        // Test bogus tesseract path - should fail
        configJson = """
                {
                  "tesseract-ocr-parser": {
                    "tesseractPath": "bogus path"
                  }
                }
                """;
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        try {
            Response response = WebClient
                    .create(endPoint + TIKA_PATH + "/config")
                    .type("multipart/form-data")
                    .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
            assertEquals(422, response.getStatus());
        } catch (ProcessingException e) {
            //swallow
        }
    }

    @Test
    public void testTrustedMethodPrevention() throws Exception {
        // This test requires tesseract to be installed - the validation only happens
        // when TesseractOCRParser is actually invoked during parsing
        assumeTrue(new TesseractOCRParser().hasTesseract(), "Tesseract not installed, skipping test");

        // Trusted methods should not be settable via JSON config
        String configJson = """
                {
                  "tesseract-ocr-parser": {
                    "trustedPageSeparator": " "
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        assertEquals(422, response.getStatus());
    }

    @Test
    public void testFloatInConfig() throws Exception {
        String configJson = """
                {
                  "pdf-parser": {
                    "averageCharTolerance": 2.0
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testOCR.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testUnicodePasswordProtectedSpaces() throws Exception {
        //TIKA-2858
        final String password = "    ";
        String configJson = String.format(Locale.ROOT, """
                {
                  "simple-password-provider": {
                    "password": "%s"
                  }
                }
                """, password);

        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.pdf\"");
        Attachment fileAtt = new Attachment("file", ClassLoader.getSystemResourceAsStream("test-documents/testPassword4Spaces.pdf"), fileCd);

        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Just some text.", responseMsg);
    }

    @Test
    public void testUnicodePasswordProtectedUnicode() throws Exception {
        //TIKA-2858
        final String password = "  ! < > \" \\ \u20AC \u0153 \u00A4 \u0031\u2044\u0034 " + "\u0031\u2044\u0032 \uD841\uDF0E \uD867\uDD98 \uD83D\uDE00  ";
        // Escape the password for JSON
        String escapedPassword = password.replace("\\", "\\\\").replace("\"", "\\\"");
        String configJson = "{\"simple-password-provider\": {\"password\": \"" + escapedPassword + "\"}}";

        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.pdf\"");
        Attachment fileAtt = new Attachment("file", ClassLoader.getSystemResourceAsStream("test-documents/testUnicodePassword.pdf"), fileCd);

        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Just some text.", responseMsg);
    }

    // TIKA-3227 - Skip embedded documents via config
    @Test
    public void testSkipEmbedded() throws Exception {
        // First test: without skip-embedded-document-selector, embedded content IS present
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("embed4.txt", responseMsg);

        // Second test: with skip-embedded-document-selector, embedded content is NOT present
        String configJson = """
                {
                  "skip-embedded-document-selector": {}
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.docx\"");
        Attachment fileAtt = new Attachment("file", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC), fileCd);

        ContentDisposition configCd = new ContentDisposition("form-data; name=\"config\"; filename=\"config.json\"");
        Attachment configAtt = new Attachment("config", "application/json",
                new java.io.ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertNotFound("embed4.txt", responseMsg);
    }

    @Test
    public void testJson() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json/text")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));
        assertContains("embed4.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("General Congress", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("<p", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        //test that embedded parsers are appearing in full set of "parsed bys"
        TikaTest.assertContains("org.apache.tika.parser.microsoft.EMFParser", Arrays.asList(metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET)));
    }

    @Test
    public void testJsonWriteLimitEmbedded() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json/html")
                .header("writeLimit", "500")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));
        assertContains("embed2a.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("When in the Course", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("declare the causes", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertTrue(metadata
                .get(TikaCoreProperties.CONTAINER_EXCEPTION)
                .startsWith("org.apache.tika.exception.WriteLimitReachedException"));
        assertNotFound("embed4.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    @org.junit.jupiter.api.Disabled("throwOnWriteLimitReached header not yet supported with pipes-based parsing")
    public void testJsonNoThrowWriteLimitEmbedded() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json/html")
                .header("writeLimit", "500")
                .header("throwOnWriteLimitReached", "false")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));
        String txt = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("embed2a.txt", txt);
        assertContains("When in the Course", txt);
        assertNotFound("declare the causes", txt);
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
        assertContains("<div class=\"embedded\" id=\"embed4.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testWriteLimitInPDF() throws Exception {
        int writeLimit = 10;
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        Metadata metadata = JsonMetadata.fromJson(reader);
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));

    }
}
