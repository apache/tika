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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import au.com.bytecode.opencsv.CSVReader;

public class MetadataResourceTest extends CXFTestBase {
    private static final String META_PATH = "/meta";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetadataResource.class);
        sf.setResourceProvider(MetadataResource.class,
                        new SingletonResourceProvider(new MetadataResource(tika)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {}

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/msword")
                .accept("text/csv")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), "UTF-8");

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
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), "UTF-8");
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
}
