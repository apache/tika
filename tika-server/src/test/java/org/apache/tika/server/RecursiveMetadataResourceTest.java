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

import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.junit.Test;

public class RecursiveMetadataResourceTest extends CXFTestBase {
    private static final String META_PATH = "/rmeta";
    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tika)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), IOUtils.UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertEquals(11, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get("Application-Name"));
        assertContains("plundered our seas", metadataList.get(5).get("X-TIKA:content"));
    }

    @Test
    public void testPasswordProtected() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Won't work, no password given
        assertEquals(500, response.getStatus());

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
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), IOUtils.UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertNotNull(metadataList.get(0).get("Author"));
        assertEquals("pavel", metadataList.get(0).get("Author"));
    }
}
