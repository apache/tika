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

package org.apache.tika.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.server.resource.TikaResource;
import org.junit.Test;

public class TikaResourceTest extends CXFTestBase {
    public static final String TEST_DOC = "test.doc";
    public static final String TEST_PASSWORD_PROTECTED = "password.xls";
    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";

    private static final String STREAM_CLOSED_FAULT = "java.io.IOException: Stream Closed";

    private static final String TIKA_PATH = "/tika";
    private static final int UNPROCESSEABLE = 422;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    @Test
    public void testHelloWorld() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("text/plain").accept("text/plain").get();
        assertEquals(TikaResource.GREETING,
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testTextMain() throws Exception {
        //boilerpipe
        Response response = WebClient.create(endPoint + TIKA_PATH + "/main")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("testHTML.html"));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Title : Test Indexation Html"));
        assertFalse(responseMsg.contains("Indexation du fichier"));
    }

    @Test
    public void testTextMainMultipart() throws Exception {
        //boilerpipe
        Attachment attachmentPart =
                new Attachment("myhtml", "text/html", ClassLoader.getSystemResourceAsStream("testHTML.html"));


        Response response = WebClient.create(endPoint + TIKA_PATH+"/form/main")
                .type("multipart/form-data")
                .accept("text/plain")
                .post(attachmentPart);
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Title : Test Indexation Html"));
        assertFalse(responseMsg.contains("Indexation du fichier"));
    }

    @Test
    public void testApplicationWadl() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "?_wadl")
                .accept("text/plain").get();
        String resp = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(resp.startsWith("<application"));
    }

    @Test
    public void testPasswordXLS() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordHTML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/html")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
        assertContains("<meta name=\"X-TIKA:digest:MD5\" content=\"f8be45c34e8919eedba48cc8d207fbf0\"/>",
                responseMsg);
        assertContains("<meta name=\"X-TIKA:digest:SHA1\" content=\"N4EBCE7EGTIGZWETEJ6WD3W4KN32TLPG\"/>",
                responseMsg);
    }

    @Test
    public void testPasswordXLSHTML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/html")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordXML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSXML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordMultipartXML() throws Exception {
        ClassLoader.getSystemResourceAsStream(TEST_DOC);
        Attachment attachmentPart =
                new Attachment("myworddoc", "application/msword", ClassLoader.getSystemResourceAsStream(TEST_DOC));
        WebClient webClient = WebClient.create(endPoint + TIKA_PATH + "/form");
        Response response = webClient.type("multipart/form-data")
                .accept("text/xml")
                .post(attachmentPart);
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
        assertContains("<meta name=\"X-TIKA:digest:MD5\" content=\"f8be45c34e8919eedba48cc8d207fbf0\"/>",
                responseMsg);

    }

    @Test
    public void testEmbedded() throws Exception {
        //first try text
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Course of human events"));

        //now go for xml -- different call than text
        response = WebClient.create(endPoint + TIKA_PATH)
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Course of human events"));
        assertContains("<meta name=\"X-TIKA:digest:MD5\" content=\"59f626e09a8c16ab6dbc2800c685f772\"/>",
                responseMsg);

    }

    //TIKA-1845
    @Test
    public void testWMFInRTF() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/rtf")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("testRTF_npeFromWMFInTikaServer.rtf"));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Example text"));
    }

    //TIKA-2290
    @Test
    public void testPDFOCRConfig() throws Exception {
        if (! new TesseractOCRParser().hasTesseract(new TesseractOCRConfig())) {
            return;
        }

        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/pdf")
                .accept("text/plain")
                .header(TikaResource.X_TIKA_PDF_HEADER_PREFIX+"OcrStrategy", "no_ocr")
                .put(ClassLoader.getSystemResourceAsStream("testOCR.pdf"));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.trim().equals(""));

        response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/pdf")
                .accept("text/plain")
                .header(TikaResource.X_TIKA_PDF_HEADER_PREFIX+"OcrStrategy", "ocr_only")
                .put(ClassLoader.getSystemResourceAsStream("testOCR.pdf"));
        responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertContains("Happy New Year 2003!", responseMsg);

        //now try a bad value
        response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/pdf")
                .accept("text/plain")
                .header(TikaResource.X_TIKA_PDF_HEADER_PREFIX + "OcrStrategy", "non-sense-value")
                .put(ClassLoader.getSystemResourceAsStream("testOCR.pdf"));
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testExtractTextAcceptPlainText() throws Exception {
        //TIKA-2384
        Attachment attachmentPart = new Attachment(
                "my-docx-file",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ClassLoader.getSystemResourceAsStream("2pic.docx")
        );

        Response response = WebClient.create(endPoint + TIKA_PATH + "/form")
                .type("multipart/form-data")
                .accept("text/plain")
                .post(attachmentPart);

        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("P1040893.JPG"));
        assertNotFound(
                STREAM_CLOSED_FAULT,
                responseMsg
        );
    }
}
