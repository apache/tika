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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class TikaResourceTest extends CXFTestBase {

    public static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    public static final String TEST_HELLO_WORLD_LONG = "test-documents/mock/hello_world_long.xml";
    public static final String TEST_NULL_POINTER = "test-documents/mock/null_pointer.xml";

    private static final String TIKA_PATH = "/tika";

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

    @Test
    public void testHelloWorld() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .get();
        assertEquals(TikaResource.GREETING, getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testJAXBAndActivationDependency() {
        //TIKA-2778
        AttachmentUtil.getCommandMap();
    }

    @Test
    public void testApplicationWadl() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "?_wadl")
                .get();
        String resp = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(resp.startsWith("<application"));
    }

    @Test
    public void testJson() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testJsonNPE() throws Exception {
        // With pipes-based parsing, parse exceptions are always returned in metadata with HTTP 200
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL_POINTER));

        assertEquals(200, response.getStatus());
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) response.getEntity(), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("some content", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("null pointer message", metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    @Test
    public void testJsonWriteLimit() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .header("writeLimit", "100")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("dissolve", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertTrue(metadata
                .get(TikaCoreProperties.CONTAINER_EXCEPTION)
                .startsWith("org.apache.tika.exception.WriteLimitReachedException"));
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
    }

    @Test
    public void testJsonHandlerType() throws Exception {
        // Default /tika/json uses text handler
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        // /tika/json defaults to text handler, so no HTML tags
        assertNotFound("<p>", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        // /tika/json/text explicitly uses text handler
        response = WebClient
                .create(endPoint + TIKA_PATH + "/json/text")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        metadata = JsonMetadata.fromJson(new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("<p>", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    /*
    @Test
    public void testWriteLimitInAll() throws Exception {
        //specify your file directory here
        Path testDocs = Paths.get("..../tika-parsers/src/test/resources/test-documents");
        for (File f : testDocs.toFile().listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            System.out.println(f.getName());
            testWriteLimit(f);
        }
    }

    private void testWriteLimit(File f) throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/text").accept("application/json").put(f);
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        Metadata metadata = JsonMetadata.fromJson(reader);
        int totalLen = 0;
        StringBuilder sb = new StringBuilder();
        String txt = metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        sb.append(txt);
        totalLen += (txt == null) ? 0 : txt.length();
        String fullText = sb.toString();
        //        System.out.println(fullText);
        Random r = new Random();
        for (int i = 0; i < 20; i++) {
            int writeLimit = r.nextInt(totalLen + 100);
            response = WebClient.create(endPoint + TIKA_PATH + "/text").accept("application/json")
                    .header("writeLimit", Integer.toString(writeLimit)).put(f);
            assertEquals(200, response.getStatus());
            reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            Metadata writeLimitMetadata = JsonMetadata.fromJson(reader);
            int len = 0;
            StringBuilder extracted = new StringBuilder();
            txt = writeLimitMetadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
            len += (txt == null) ? 0 : txt.length();
            extracted.append(txt);
            if (totalLen > len) {
                boolean wlr = "true".equals(writeLimitMetadata
                        .get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED));
                System.out.println(f.getName() + " " + len + " : " + writeLimit);
                assertTrue(f.getName() + ": writelimit: " + writeLimit + " len: " + len,
                        len <= writeLimit);
                assertEquals(
                        f.getName() + " : " + writeLimit + " : " + len + " total len: " + totalLen,
                        true, wlr);
            } else if (len > totalLen) {
                fail("len should never be > totalLen " + len + "  : " + totalLen);
            }
        }
    }*/

}
