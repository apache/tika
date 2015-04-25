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
import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.server.resource.MetadataResource;
import org.apache.tika.server.writer.CSVMessageBodyWriter;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.apache.tika.server.writer.XMPMessageBodyWriter;
import org.junit.Assert;
import org.junit.Test;

public class MetadataResourceTest extends CXFTestBase {
    private static final String META_PATH = "/meta";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetadataResource.class);
        sf.setResourceProvider(MetadataResource.class,
                new SingletonResourceProvider(new MetadataResource(tika)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
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
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), org.apache.tika.io.IOUtils.UTF_8);

        CSVReader csvReader = new CSVReader(reader);

        Map<String, String> metadata = new HashMap<String, String>();

        String[] nextLine;
        while ((nextLine = csvReader.readNext()) != null) {
            metadata.put(nextLine[0], nextLine[1]);
        }
        csvReader.close();

        assertNotNull(metadata.get("Author"));
        assertEquals("Maxim Valyanskiy", metadata.get("Author"));
    }

    @Test
    public void testPasswordProtected() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/csv")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Won't work, no password given
        assertEquals(500, response.getStatus());

        // Try again, this time with the wrong password
        response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/csv")
                .header("Password", "wrong password")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        assertEquals(500, response.getStatus());

        // Try again, this time with the password
        response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/csv")
                .header("Password", "password")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Will work
        assertEquals(200, response.getStatus());

        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), org.apache.tika.io.IOUtils.UTF_8);
        CSVReader csvReader = new CSVReader(reader);

        Map<String, String> metadata = new HashMap<String, String>();

        String[] nextLine;
        while ((nextLine = csvReader.readNext()) != null) {
            metadata.put(nextLine[0], nextLine[1]);
        }
        csvReader.close();

        assertNotNull(metadata.get("Author"));
        assertEquals("pavel", metadata.get("Author"));
    }

    @Test
    public void testJSON() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), org.apache.tika.io.IOUtils.UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata.get("Author"));
        assertEquals("Maxim Valyanskiy", metadata.get("Author"));
    }

    @Test
    public void testXMP() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("application/rdf+xml")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        String result = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", result);
    }

    //Now test requesting one field
    @Test
    public void testGetField_XXX_NotFound() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH + "/xxx").type("application/msword")
                .accept(MediaType.APPLICATION_JSON).put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
        Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetField_Author_TEXT_Partial_BAD_REQUEST() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
                .accept(MediaType.TEXT_PLAIN).put(copy(stream, 8000));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetField_Author_TEXT_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
                .accept(MediaType.TEXT_PLAIN).put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertEquals("Maxim Valyanskiy", s);
    }

    @Test
    public void testGetField_Author_JSON_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
                .accept(MediaType.APPLICATION_JSON).put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) response.getEntity(), org.apache.tika.io.IOUtils.UTF_8));
        assertEquals("Maxim Valyanskiy", metadata.get("Author"));
        assertEquals(1, metadata.names().length);
    }

    @Test
    public void testGetField_Author_XMP_Partial_Found() throws Exception {

        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient.create(endPoint + META_PATH + "/dc:creator").type("application/msword")
                .accept("application/rdf+xml").put(copy(stream, 12000));
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String s = IOUtils.readStringFromStream((InputStream) response.getEntity());
        assertContains("<rdf:li>Maxim Valyanskiy</rdf:li>", s);
    }


}

