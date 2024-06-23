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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.config.DocumentSelectorConfig;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

public class RecursiveMetadataResourceTest extends CXFTestBase {

    private static final String FORM_PATH = "/form";
    private static final String META_PATH = "/rmeta";
    private static final String TEXT_PATH = "/text";
    private static final String IGNORE_PATH = "/ignore";
    private static final String XML_PATH = "/xml";
    private static final String UNPARSEABLE_PATH = "/somethingOrOther";
    private static final String SLASH = "/";

    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class, new SingletonResourceProvider(new RecursiveMetadataResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/config/tika-config-for-server-tests.xml");
    }

    @Test
    public void testGZOut() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .acceptEncoding("gzip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader(new GzipCompressorInputStream((InputStream) response.getEntity()), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList
                .get(0)
                .get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList
                .get(6)
                .get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList
                .get(10)
                .get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testGZIn() throws Exception {

        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .encoding("gzip")
                .put(gzip(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC)));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        String[] parsedBy = metadataList
                .get(0)
                .getValues(TikaCoreProperties.TIKA_PARSED_BY);
        //make sure the CompressorParser doesn't show up here
        assertEquals(3, parsedBy.length);
        assertEquals("org.apache.tika.parser.CompositeParser", parsedBy[0]);
        assertEquals("org.apache.tika.parser.DefaultParser", parsedBy[1]);
        assertEquals("org.apache.tika.parser.microsoft.ooxml.OOXMLParser", parsedBy[2]);

        //test that the rest is as it should be
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList
                .get(0)
                .get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList
                .get(6)
                .get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList
                .get(10)
                .get("X-TIKA:digest:MD5"));

    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList
                .get(0)
                .get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList
                .get(6)
                .get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList
                .get(10)
                .get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testHeaders() throws Exception {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        map.addAll("meta_mymeta", "first", "second", "third");

        Response response = WebClient
                .create(endPoint + META_PATH)
                .headers(map)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals("first,second,third", metadataList
                .get(0)
                .get("mymeta"));
    }

    @Test
    public void testPasswordProtected() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Won't work, no password given
        assertEquals(200, response.getStatus());
        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertNotNull(metadataList
                .get(0)
                .get(TikaCoreProperties.CREATOR));
        assertContains("org.apache.tika.exception.EncryptedDocumentException", metadataList
                .get(0)
                .get(TikaCoreProperties.CONTAINER_EXCEPTION));
        // Try again, this time with the password
        response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("application/json")
                .header("Password", "password")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Will work
        assertEquals(200, response.getStatus());

        // Check results
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertNotNull(metadataList
                .get(0)
                .get(TikaCoreProperties.CREATOR));
        assertEquals("pavel", metadataList
                .get(0)
                .get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testHandlerType() throws Exception {
        //default unspecified
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        String content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //extra slash
        response = WebClient
                .create(endPoint + META_PATH + SLASH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //unparseable
        response = WebClient
                .create(endPoint + META_PATH + UNPARSEABLE_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //xml
        response = WebClient
                .create(endPoint + META_PATH + XML_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //text
        response = WebClient
                .create(endPoint + META_PATH + TEXT_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("embed_3"));

        //ignore
        response = WebClient
                .create(endPoint + META_PATH + IGNORE_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertNull(metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));

    }

    @Test
    public void testHandlerTypeInMultipartXML() throws Exception {
        //default unspecified
        Attachment attachmentPart =
                new Attachment("myworddocx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        WebClient webClient = WebClient.create(endPoint + META_PATH + FORM_PATH);

        Response response = webClient
                .type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        String content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //unparseable
        attachmentPart =
                new Attachment("myworddocx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + UNPARSEABLE_PATH);

        response = webClient
                .type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //xml
        attachmentPart =
                new Attachment("myworddocx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + XML_PATH);

        response = webClient
                .type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //text
        attachmentPart =
                new Attachment("myworddocx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + TEXT_PATH);

        response = webClient
                .type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim();
        assertTrue(content.startsWith("embed_3"));

        //ignore -- no content
        attachmentPart =
                new Attachment("myworddocx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + IGNORE_PATH);

        response = webClient
                .type("multipart/form-data")
                .accept("application/json")
                .query("handler", "ignore")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertNull(metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedResourceLimit() throws Exception {
        for (int i : new int[]{0, 1, 5}) {
            Response response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .header("maxEmbeddedResources", Integer.toString(i))
                    .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

            assertEquals(200, response.getStatus());
            // Check results
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(i + 1, metadataList.size());
        }
    }

    // TIKA-3227
    @Test
    public void testSkipEmbedded() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header(DocumentSelectorConfig.X_TIKA_SKIP_EMBEDDED_HEADER, "false")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());

        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header(DocumentSelectorConfig.X_TIKA_SKIP_EMBEDDED_HEADER, "true")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
    }

    @Test
    public void testWriteLimit() throws Exception {
        int writeLimit = 10;
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertEquals("true", metadataList
                .get(0)
                .get(TikaCoreProperties.WRITE_LIMIT_REACHED));

        //now try with a write limit of 500
        writeLimit = 550;
        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(10, metadataList.size());
        assertEquals("true", metadataList
                .get(6)
                .get(TikaCoreProperties.WRITE_LIMIT_REACHED));
        assertContains("When in the Course of human events it becomes necessary for one people", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
        TikaTest.assertNotContained("We hold these truths", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));

    }

    @Test
    public void testWriteLimitInPDF() throws Exception {
        int writeLimit = 10;
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream("test-documents/testPDFTwoTextBoxes" + ".pdf"));

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata metadata = metadataList.get(0);
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
    }

    @Test
    public void testNoThrowOnWriteLimitReached() throws Exception {
        int writeLimit = 100;
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .header("throwOnWriteLimitReached", "false")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("true", metadataList
                .get(0)
                .get(TikaCoreProperties.WRITE_LIMIT_REACHED));

        //now try with a write limit of 550
        writeLimit = 550;
        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .header("throwOnWriteLimitReached", "false")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("true", metadataList
                .get(0)
                .get(TikaCoreProperties.WRITE_LIMIT_REACHED));
        assertContains("When in the Course of human events it becomes necessary for one people", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
        TikaTest.assertNotContained("We hold these truths", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));

    }

}
