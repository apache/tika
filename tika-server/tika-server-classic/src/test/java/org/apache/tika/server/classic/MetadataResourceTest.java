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

package org.apache.tika.server.classic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.server.classic.resource.XMPMetadataResource;
import org.apache.tika.server.classic.writer.XMPMessageBodyWriter;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.writer.CSVMessageBodyWriter;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.TextMessageBodyWriter;

public class MetadataResourceTest extends CXFTestBase {
    private static final String META_PATH = "/meta";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetadataResource.class, XMPMetadataResource.class);
        sf.setResourceProvider(MetadataResource.class,
                new SingletonResourceProvider(new MetadataResource()));
        sf.setResourceProvider(XMPMetadataResource.class,
                new SingletonResourceProvider(new XMPMetadataResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new XMPMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response =
                WebClient.create(endPoint + META_PATH).type("application/msword").accept("text/csv")
                        .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);

        CSVParser csvReader = new CSVParser(reader, CSVFormat.EXCEL);

        Map<String, String> metadata = new HashMap<>();

        for (CSVRecord r : csvReader) {
            metadata.put(r.get(0), r.get(1));
        }
        csvReader.close();

        assertNotNull(metadata.get(TikaCoreProperties.CREATOR.getName()));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR.getName()));
        assertEquals("X-TIKA:digest:MD5", "f8be45c34e8919eedba48cc8d207fbf0",
                metadata.get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testPasswordProtected() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).type("application/vnd.ms-excel")
                .accept("text/csv").put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Won't work, no password given
        assertEquals(500, response.getStatus());

        // Try again, this time with the wrong password
        response = WebClient.create(endPoint + META_PATH).type("application/vnd.ms-excel")
                .accept("text/csv").header("Password", "wrong password").put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        assertEquals(500, response.getStatus());

        // Try again, this time with the password
        response = WebClient.create(endPoint + META_PATH).type("application/vnd.ms-excel")
                .accept("text/csv").header("Password", "password").put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Will work
        assertEquals(200, response.getStatus());

        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        CSVParser csvReader = new CSVParser(reader, CSVFormat.EXCEL);

        Map<String, String> metadata = new HashMap<>();

        for (CSVRecord r : csvReader) {
            metadata.put(r.get(0), r.get(1));
        }
        csvReader.close();

        assertNotNull(metadata.get(TikaCoreProperties.CREATOR.getName()));
        assertEquals("pavel", metadata.get(TikaCoreProperties.CREATOR.getName()));
    }

    @Test
    public void testJSON() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).type("application/msword")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testXMP() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).type("application/msword")
                .accept("application/rdf+xml")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        String result = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", result);
    }

    //Now test requesting one field
    @Test
    public void testGetField_XXX_NotFound() throws Exception {
        Response response =
                WebClient.create(endPoint + META_PATH + "/xxx").type("application/msword")
                        .accept(MediaType.APPLICATION_JSON)
                        .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
        Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetField_Author_TEXT_Partial_BAD_REQUEST() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response =
                WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
                        .accept(MediaType.TEXT_PLAIN).put(copy(stream, 8000));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @Ignore("TODO: add back in xmp handler")
    public void testGetField_Author_TEXT_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response =
                WebClient.create(endPoint + META_PATH + "/" + TikaCoreProperties.CREATOR.getName())
                        .type("application/msword").accept(MediaType.TEXT_PLAIN)
                        .put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertEquals("Maxim Valyanskiy", s);
    }

    @Test
    public void testGetField_Author_JSON_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response =
                WebClient.create(endPoint + META_PATH + "/" + TikaCoreProperties.CREATOR.getName())
                        .type("application/msword").accept(MediaType.APPLICATION_JSON)
                        .put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Metadata metadata = JsonMetadata
                .fromJson(new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertEquals("Maxim Valyanskiy", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(1, metadata.names().length);
    }

    @Test
    @Ignore("TODO: until we can reintegrate xmpwriter")
    public void testGetField_Author_XMP_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response =
                WebClient.create(endPoint + META_PATH + "/dc:creator").type("application/msword")
                        .accept("application/rdf+xml").put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", s);
    }


}

