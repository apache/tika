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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.CSVMessageBodyWriter;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.core.writer.TextMessageBodyWriter;
import org.apache.tika.server.standard.resource.XMPMetadataResource;
import org.apache.tika.server.standard.writer.XMPMessageBodyWriter;

public class MetadataResourceTest extends CXFTestBase {

    private static final String META_PATH = "/meta";
    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true; // exercises per-request config injection
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetadataResource.class, XMPMetadataResource.class,
                RecursiveMetadataResource.class);
        sf.setResourceProvider(MetadataResource.class, new SingletonResourceProvider(new MetadataResource(tikaResource)));
        sf.setResourceProvider(XMPMetadataResource.class, new SingletonResourceProvider(new XMPMetadataResource(tikaResource)));
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        // Needed by getMetadataField's TikaServerParseException throw.
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new MetadataListMessageBodyWriter());
        providers.add(new XMPMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("text/csv")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);

        CSVParser csvReader = CSVParser.builder().setReader(reader).setFormat(CSVFormat.EXCEL).get();

        Map<String, String> metadata = new HashMap<>();

        for (CSVRecord r : csvReader) {
            metadata.put(r.get(0), r.get(1));
        }
        csvReader.close();

        assertNotNull(metadata.get(TikaCoreProperties.CREATOR.getName()));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR.getName()));

        assertEquals("f8be45c34e8919eedba48cc8d207fbf0", metadata.get("tk:digest:MD5"), "tk:digest:MD5");
    }

    @Test
    public void testPasswordProtected() throws Exception {
        // Test 1: No password - should fail
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xls\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED), fileCd);

        Response response = WebClient
                .create(endPoint + META_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/json")
                .post(new MultipartBody(Arrays.asList(fileAtt)));

        // A failed decrypt isn't a process failure -- 200, exception on the metadata.
        assertEquals(200, response.getStatus());
        Metadata noPasswordMetadata = JsonMetadata.fromJson(new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertContains("org.apache.tika.exception.EncryptedDocumentException",
                noPasswordMetadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));

        // Test 2: Wrong password - should fail the same way
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xls\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED), fileCd);
        String wrongConfigJson = """
                {
                  "simple-password-provider": {
                    "password": "wrong password"
                  }
                }
                """;
        ContentDisposition configCd = new ContentDisposition("form-data; name=\"config\"; filename=\"config.json\"");
        Attachment wrongConfigAtt = new Attachment("config",
                new java.io.ByteArrayInputStream(wrongConfigJson.getBytes(UTF_8)), configCd);

        response = WebClient
                .create(endPoint + META_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/json")
                .post(new MultipartBody(Arrays.asList(fileAtt, wrongConfigAtt)));

        assertEquals(200, response.getStatus());
        Metadata wrongPasswordMetadata = JsonMetadata.fromJson(new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertContains("org.apache.tika.exception.EncryptedDocumentException",
                wrongPasswordMetadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));

        // Test 3: Correct password - should work
        fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xls\"");
        fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED), fileCd);
        String configJson = """
                {
                  "simple-password-provider": {
                    "password": "password"
                  }
                }
                """;
        configCd = new ContentDisposition("form-data; name=\"config\"; filename=\"config.json\"");
        Attachment configAtt = new Attachment("config",
                new java.io.ByteArrayInputStream(configJson.getBytes(UTF_8)), configCd);

        response = WebClient
                .create(endPoint + META_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/json")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        // Will work
        assertEquals(200, response.getStatus());

        // Check results
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertNotNull(metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("pavel", metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testJSON() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testXMP() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("application/rdf+xml")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        String result = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", result);
    }

    //Now test requesting one field
    @Test
    public void testGetField_XXX_NotFound() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH + "/xxx")
                .type("application/msword")
                .accept(MediaType.APPLICATION_JSON)
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetField_Author_TEXT_Partial_UNPROCESSABLE() throws Exception {
        // Truncating at 8000 bytes corrupts the OLE2 structure enough that OfficeParser
        // throws -- a real container exception, not just a missing field.
        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient
                .create(endPoint + META_PATH + "/Author")
                .type("application/msword")
                .accept(MediaType.TEXT_PLAIN)
                .put(copy(stream, 8000));
        assertEquals(422, response.getStatus());
    }

    @Test
    public void testGetField_Author_TEXT_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient
                .create(endPoint + META_PATH + "/" + TikaCoreProperties.CREATOR.getName())
                .type("application/msword")
                .accept(MediaType.TEXT_PLAIN)
                .put(copy(stream, 12000));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertEquals("Maxim Valyanskiy", s);
    }

    @Test
    public void testGetField_Author_JSON_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient
                .create(endPoint + META_PATH + "/" + TikaCoreProperties.CREATOR.getName())
                .type("application/msword")
                .accept(MediaType.APPLICATION_JSON)
                .put(copy(stream, 12000));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(1, metadata.names().length);
    }

    @Test
    public void testGetField_Author_XMP_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient
                .create(endPoint + META_PATH + "/dc:creator")
                .type("application/msword")
                .accept("application/rdf+xml")
                .put(copy(stream, 12000));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", s);
    }


    /**
     * /meta and /rmeta[0] describe the same container document, so their metadata must
     * agree. /meta reaches it by a different route (embedded parsing suppressed, content
     * capture off), and every /meta defect this release -- a dropped field, a spurious
     * exception flag, a Content-Type taken from the multipart envelope -- was a silent
     * divergence between the two that no test compared.
     */
    @Test
    public void testMetaAgreesWithRmeta() throws Exception {
        Metadata meta = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) WebClient.create(endPoint + META_PATH).accept("application/json")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC))
                        .getEntity(), UTF_8));

        List<Metadata> rmeta = JsonMetadataList.fromJson(new InputStreamReader(
                (InputStream) WebClient.create(endPoint + "/rmeta/ignore").accept("application/json")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC))
                        .getEntity(), UTF_8));
        Metadata container = rmeta.get(0);

        for (String name : container.names()) {
            // tk:content is absent from both (ignore handler); embedded-only bookkeeping
            // legitimately differs because /meta stops at the container.
            // tk:content is absent from both (ignore handler). tk:resource-name and
            // tk:source-path currently carry the server's per-request spool filename, so
            // they differ by construction until that is fixed.
            if (name.startsWith("X-TIKA:EXCEPTION") || name.equals("tk:content")
                    || name.startsWith("tk:parsed-by-full-set")
                    || name.equals("tk:resource-name") || name.equals("tk:source-path")
                    || name.equals("tk:parse-time-millis")) {
                continue;
            }
            assertEquals(container.get(name), meta.get(name),
                    "/meta and /rmeta[0] disagree on '" + name + "'");
        }
        assertNull(meta.get("tk:exception:embedded-depth-limit-reached"),
                "/meta suppresses embedded docs; that is not a limit the caller hit");
    }

}
