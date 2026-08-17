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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.writer.CSVMessageBodyWriter;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.TextMessageBodyWriter;

/**
 * The Content-Type on the wire comes from the resource method's {@code @Produces},
 * not the selected writer's, so a charset declared only on the writer never reaches
 * the client. Metadata values are arbitrary document text, so /meta's non-JSON
 * representations must declare UTF-8.
 */
public class MetadataResourceCharsetTest extends CXFTestBase {

    private static final String META_PATH = "/meta";
    private static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    private static final String NON_ASCII_TITLE = "你好，世界";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetadataResource.class);
        sf.setResourceProvider(MetadataResource.class,
                new SingletonResourceProvider(new MetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testCsvDeclaresUtf8() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("text/csv")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));

        assertEquals(200, response.getStatus());
        assertEquals("text/csv", response.getMediaType().getType() + "/" + response.getMediaType().getSubtype());
        assertDeclaresUtf8(response);
        assertContains(NON_ASCII_TITLE, getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testFieldTextDeclaresUtf8() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH + "/title")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));

        assertEquals(200, response.getStatus());
        assertDeclaresUtf8(response);
        assertEquals(NON_ASCII_TITLE, getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testDefaultAcceptIsStillJson() {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));

        assertEquals("json", response.getMediaType().getSubtype());
    }

    private static void assertDeclaresUtf8(Response response) {
        String charset = response.getMediaType().getParameters().get("charset");
        assertNotNull(charset, "no charset on " + response.getMediaType());
        // charset names are case-insensitive; CXF lowercases the one it defaults in
        assertEquals(StandardCharsets.UTF_8, Charset.forName(charset));
    }
}
