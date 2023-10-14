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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.config.DocumentSelectorConfig;
import org.apache.tika.server.core.config.PasswordProviderConfig;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.standard.config.PDFServerConfig;
import org.apache.tika.server.standard.config.TesseractServerConfig;

public class TikaResourceTest extends CXFTestBase {
    public static final String TEST_DOC = "test-documents/test.doc";
    public static final String TEST_PASSWORD_PROTECTED = "test-documents/password.xls";
    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";
    private static final String TEST_OOM = "mock/fake_oom.xml";

    private static final String STREAM_CLOSED_FAULT = "java.io.IOException: Stream Closed";

    private static final String TIKA_PATH = "/tika";
    private static final String TIKA_POST_PATH = "/tika/form";
    private static final int UNPROCESSEABLE = 422;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource()));
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
        return getClass().getResourceAsStream("/config/tika-config-for-server-tests.xml");
    }

    @Test
    public void testHelloWorld() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("text/plain").accept("text/plain")
                        .get();
        assertEquals(TikaResource.GREETING,
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/msword")
                .accept("text/plain").put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testWordGzipIn() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/msword")
                .accept("text/plain").encoding("gzip")
                .put(gzip(ClassLoader.getSystemResourceAsStream(TEST_DOC)));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testLongGzipOut() throws Exception {
        //if the output is long enough, jax-rs will compress it, otherwise it won't
        //this output is long enough, and should be compressed
        Response response =
                WebClient.create(endPoint + TIKA_PATH).accept("text/plain").acceptEncoding("gzip")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        assertTrue(response.getHeaders().containsKey(CONTENT_ENCODING));
        assertEquals("gzip", response.getHeaderString(CONTENT_ENCODING));
        String responseMsg = getStringFromInputStream(
                new GzipCompressorInputStream((InputStream) response.getEntity()));
        assertTrue(responseMsg.contains("Course of human"));
    }

    @Test
    public void testShortGzipOut() throws Exception {
        //if the output is long enough, jax-rs will compress it, otherwise it won't
        //this output is short enough, and should not be compressed
        Response response =
                WebClient.create(endPoint + TIKA_PATH).accept("text/plain").acceptEncoding("gzip")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        assertFalse(response.getHeaders().containsKey(CONTENT_ENCODING));

        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testTextMain() throws Exception {
        //boilerpipe
        Response response = WebClient.create(endPoint + TIKA_PATH + "/main").accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testHTML.html"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Title : Test Indexation Html"));
        assertFalse(responseMsg.contains("Indexation du fichier"));
    }

    @Test
    public void testTextMainMultipart() throws Exception {
        //boilerpipe
        Attachment attachmentPart = new Attachment("myhtml", "text/html",
                ClassLoader.getSystemResourceAsStream("test-documents/testHTML.html"));


        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/form/main").type("multipart/form-data")
                        .accept("text/plain").post(attachmentPart);
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Title : Test Indexation Html"));
        assertFalse(responseMsg.contains("Indexation du fichier"));
    }


    @Test
    public void testPasswordXLS() {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/vnd.ms-excel")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordHTML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/msword")
                .accept("text/html").put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
        assertContains(
                "<meta name=\"X-TIKA:digest:MD5\" content=\"f8be45c34e8919eedba48cc8d207fbf0\"/>",
                responseMsg);
        assertContains(
                "<meta name=\"X-TIKA:digest:SHA1\" content=\"N4EBCE7EGTIGZWETEJ6WD3W4KN32TLPG\"/>",
                responseMsg);
    }

    @Test
    public void testPasswordXLSHTML() {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/vnd.ms-excel")
                .accept("text/html")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordXML() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/msword").accept("text/xml")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSXML() {
        Response response = WebClient.create(endPoint + TIKA_PATH).type("application/vnd.ms-excel")
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordMultipartXML() throws Exception {
        ClassLoader.getSystemResourceAsStream(TEST_DOC);
        Attachment attachmentPart = new Attachment("myworddoc", "application/msword",
                ClassLoader.getSystemResourceAsStream(TEST_DOC));
        WebClient webClient = WebClient.create(endPoint + TIKA_PATH + "/form");
        Response response =
                webClient.type("multipart/form-data").accept("text/xml").post(attachmentPart);
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("test"));
        assertContains(
                "<meta name=\"X-TIKA:digest:MD5\" content=\"f8be45c34e8919eedba48cc8d207fbf0\"/>",
                responseMsg);

    }

    @Test
    public void testJAXBAndActivationDependency() {
        //TIKA-2778
        AttachmentUtil.getCommandMap();
    }

    @Test
    public void testEmbedded() throws Exception {
        //first try text
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Course of human events"));

        //now go for xml -- different call than text
        response = WebClient.create(endPoint + TIKA_PATH).accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Course of human events"));
        assertContains(
                "<meta name=\"X-TIKA:digest:MD5\" content=\"59f626e09a8c16ab6dbc2800c685f772\"/>",
                responseMsg);

    }

    //TIKA-1845
    @Test
    public void testWMFInRTF() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/rtf").accept("text/plain")
                        .put(ClassLoader.getSystemResourceAsStream(
                                "test-documents/testRTF_npeFromWMFInTikaServer.rtf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Example text"));
    }

    //TIKA-2638 and TIKA-2816
    @Test
    public void testOCRLanguageConfig() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        Response response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "OcrStrategy", "ocr_only")
                .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "Language", "eng+fra")
                .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "MinFileSizeToOcr", "10")
                .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "MaxFileSizeToOcr",
                        "1000000000")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);
    }

    //TIKA-2290
    @Test
    public void testPDFOCRConfig() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "OcrStrategy", "no_ocr")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertEquals("", responseMsg.trim());

        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "skipOcr", "true")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertEquals("", responseMsg.trim());


        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "OcrStrategy",
                                "ocr_only")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);

        //now try a bad value
        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "OcrStrategy",
                                "non-sense-value")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        assertEquals(400, response.getStatus());
    }

    // TIKA-3320
    @Test
    public void testPDFLowerCaseOCRConfig() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                                "ocrstrategy", "no_ocr")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertEquals("", responseMsg.trim());

        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX
                                .toLowerCase(Locale.ROOT) + "skipocr", "true")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertEquals("", responseMsg.trim());


        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                                "ocrstrategy", "ocr_only")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);

        //now try a bad value
        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                                "ocrstrategy", "non-sense-value")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        assertEquals(400, response.getStatus());
    }

    //TIKA-2669
    @Test
    public void testPDFConfig() throws Exception {

        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .put(ClassLoader.getSystemResourceAsStream(
                                "test-documents/testPDFTwoTextBoxes.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg.replaceAll("[\r\n ]+", " ").trim();
        assertEquals(
                "Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2",
                responseMsg);

        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "sortByPosition",
                                "false").put(ClassLoader
                        .getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg.replaceAll("[\r\n ]+", " ").trim();
        assertEquals(
                "Left column line 1 Left column line 2 Right column line 1 Right column line 2",
                responseMsg);

        //make sure that default reverts to initial config option
        response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .put(ClassLoader.getSystemResourceAsStream(
                                "test-documents/testPDFTwoTextBoxes.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        responseMsg = responseMsg.replaceAll("[\r\n ]+", " ").trim();
        assertEquals(
                "Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2",
                responseMsg);

    }


    @Test
    public void testExtractTextAcceptPlainText() throws Exception {
        //TIKA-2384
        Attachment attachmentPart = new Attachment("my-docx-file",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ClassLoader.getSystemResourceAsStream("test-documents/2pic.docx"));

        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/form").type("multipart/form-data")
                        .accept("text/plain").post(attachmentPart);

        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("P1040893.JPG"));
        assertNotFound(STREAM_CLOSED_FAULT, responseMsg);
    }

    @Test
    public void testDataIntegrityCheck() {
        Response response;
        try {
            response = WebClient.create(endPoint + TIKA_PATH).type("application/pdf")
                    .accept("text/plain")
                    .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "tesseractPath",
                            "C://tmp//hello.bat\u0000")
                    .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
            assertEquals(400, response.getStatus());
        } catch (ProcessingException e) {
            //can't tell why this intermittently happens. :(
            //started after the upgrade to 3.2.7
        }

        try {
            response = WebClient.create(endPoint + TIKA_PATH).type("application/pdf")
                    .accept("text/plain")
                    .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX + "tesseractPath",
                            "bogus path")
                    .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
            assertEquals(400, response.getStatus());
        } catch (ProcessingException e) {
            //swallow
        }
    }

    @Test
    public void testTrustedMethodPrevention() {
        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(TesseractServerConfig.X_TIKA_OCR_HEADER_PREFIX +
                                "trustedPageSeparator", "\u0020")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        assertEquals(400, response.getStatus());

    }

    @Test
    public void testFloatInHeader() {
        Response response =
                WebClient.create(endPoint + TIKA_PATH).type("application/pdf").accept("text/plain")
                        .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "averageCharTolerance",
                                "2.0")
                        .put(ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"));
        assertEquals(200, response.getStatus());

    }

    @Test
    public void testUnicodePasswordProtectedSpaces() throws Exception {
        //TIKA-2858
        final String password = "    ";
        final String encoded =
                new Base64().encodeAsString(password.getBytes(StandardCharsets.UTF_8));
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .header(PasswordProviderConfig.PASSWORD_BASE64_UTF8, encoded).put(ClassLoader
                        .getSystemResourceAsStream("test-documents/testPassword4Spaces.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Just some text.", responseMsg);
    }

    @Test
    public void testUnicodePasswordProtectedUnicode() throws Exception {
        //TIKA-2858
        final String password = "  ! < > \" \\ \u20AC \u0153 \u00A4 \u0031\u2044\u0034 " +
                "\u0031\u2044\u0032 \uD841\uDF0E \uD867\uDD98 \uD83D\uDE00  ";
        final String encoded =
                new Base64().encodeAsString(password.getBytes(StandardCharsets.UTF_8));
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .header(PasswordProviderConfig.PASSWORD_BASE64_UTF8, encoded).put(ClassLoader
                        .getSystemResourceAsStream("test-documents/testUnicodePassword.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Just some text.", responseMsg);
    }

    // TIKA-3227
    @Test
    public void testSkipEmbedded() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .header(DocumentSelectorConfig.X_TIKA_SKIP_EMBEDDED_HEADER, "false")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("embed4.txt", responseMsg);

        response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .header(DocumentSelectorConfig.X_TIKA_SKIP_EMBEDDED_HEADER, "true")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertNotFound("embed4.txt", responseMsg);
    }

    // TIKA-3344
    @Test
    public void testPDFLowerCaseOCRConfigPOST() throws Exception {
        if (!new TesseractOCRParser().hasTesseract()) {
            return;
        }

        Response response = WebClient.create(endPoint + TIKA_POST_PATH).type("application/pdf")
                .accept(MediaType.TEXT_PLAIN).type(MediaType.MULTIPART_FORM_DATA)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                        "ocrstrategy", "no_ocr").post(testPDFLowerCaseOCRConfigPOSTBody());
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertEquals("", responseMsg.trim());

        response = WebClient.create(endPoint + TIKA_POST_PATH).type("application/pdf")
                .accept(MediaType.TEXT_PLAIN).type(MediaType.MULTIPART_FORM_DATA)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                        "ocrstrategy", "ocr_only").post(testPDFLowerCaseOCRConfigPOSTBody());
        responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Happy New Year 2003!", responseMsg);

        //now try a bad value
        response = WebClient.create(endPoint + TIKA_POST_PATH).type("application/pdf")
                .accept(MediaType.TEXT_PLAIN).type(MediaType.MULTIPART_FORM_DATA)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX.toLowerCase(Locale.ROOT) +
                        "ocrstrategy", "non-sense-value").post(testPDFLowerCaseOCRConfigPOSTBody());
        assertEquals(400, response.getStatus());
    }

    private MultipartBody testPDFLowerCaseOCRConfigPOSTBody() {
        ContentDisposition cd =
                new ContentDisposition("form-data; name=\"input\"; filename=\"testOCR.pdf\"");
        Attachment att = new Attachment("upload",
                ClassLoader.getSystemResourceAsStream("test-documents/testOCR.pdf"), cd);
        return new MultipartBody(att);
    }

    @Test
    public void testJson() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/text").accept("application/json")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(
                new InputStreamReader(((InputStream) response.getEntity()),
                        StandardCharsets.UTF_8));
        assertContains("embed4.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("General Congress", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("<p", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        //test that embedded parsers are appearing in full set of "parsed bys"
        TikaTest.assertContains("org.apache.tika.parser.microsoft.EMFParser",
                Arrays.asList(metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET)));
    }

    @Test
    public void testJsonWriteLimitEmbedded() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/html").accept("application/json")
                        .header("writeLimit", "500")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(
                new InputStreamReader(((InputStream) response.getEntity()),
                        StandardCharsets.UTF_8));
        assertContains("embed2a.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("When in the Course", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("declare the causes", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertTrue(metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION)
                .startsWith("org.apache.tika.exception.WriteLimitReachedException"));
        assertNotFound("embed4.txt", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testJsonNoThrowWriteLimitEmbedded() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/html").accept("application/json")
                        .header("writeLimit", "500")
                        .header("throwOnWriteLimitReached", "false")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Metadata metadata = JsonMetadata.fromJson(
                new InputStreamReader(((InputStream) response.getEntity()),
                        StandardCharsets.UTF_8));
        String txt = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("embed2a.txt", txt);
        assertContains("When in the Course", txt);
        assertNotFound("declare the causes", txt);
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
        assertContains("<div class=\"embedded\" id=\"embed4.txt",
                metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testWriteLimitInPDF() throws Exception {
        int writeLimit = 10;
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit)).put(ClassLoader
                        .getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes.pdf"));

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        Metadata metadata = JsonMetadata.fromJson(reader);
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));

    }
}
