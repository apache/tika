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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class TikaResourceTest extends CXFTestBase {

    public static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    public static final String TEST_HELLO_WORLD_LONG = "test-documents/mock/hello_world_long.xml";
    public static final String TEST_NULL_POINTER = "test-documents/mock/null_pointer.xml";
    public static final String TEST_OOM = "test-documents/mock/fake_oom.xml";

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
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
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
    public void testHeaders() throws Exception {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        map.addAll("meta_mymeta", "first", "second", "third");
        Response response = WebClient.create(endPoint + TIKA_PATH).headers(map).accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        String xml = getStringFromInputStream((InputStream) response.getEntity());
        //can't figure out why these values are comma-delimited, rather
        //than a true list...is this really the expected behavior?
        //this at least tests that the pass-through, basically works...
        //except for multi-values... :D
        assertContains("<meta name=\"mymeta\" content=\"first,second,third\"/>", xml);
    }

    @Test
    public void testJAXBAndActivationDependency() {
        //TIKA-2778
        AttachmentUtil.getCommandMap();
    }

    @Test
    public void testOOMInLegacyMode() throws Exception {

        Response response = null;
        try {
            response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }

        response = WebClient.create(endPoint + TIKA_PATH).accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertContains("hello world", responseMsg);
    }

    @Test
    public void testApplicationWadl() throws Exception {
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "?_wadl").accept("text/plain").get();
        String resp = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(resp.startsWith("<application"));
    }

    @Test
    public void testJson() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).accept(
                "application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        Metadata metadata =
                JsonMetadata.fromJson(new InputStreamReader(
                        ((InputStream)response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testJsonNPE() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).accept(
                "application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL_POINTER));
        Metadata metadata =
                JsonMetadata.fromJson(new InputStreamReader(
                        ((InputStream)response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("some content", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("null pointer message",
                metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    @Test
    public void testJsonWriteLimit() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .header("writeLimit", "100")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        Metadata metadata =
                JsonMetadata.fromJson(new InputStreamReader(
                        ((InputStream)response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("dissolve", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertTrue(metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION).startsWith(
                "org.apache.tika.sax.WriteOutContentHandler$WriteLimitReachedException"
        ));
    }

    @Test
    public void testJsonHandlerType() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        Metadata metadata =
                JsonMetadata.fromJson(new InputStreamReader(
                        ((InputStream)response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        //default is xhtml
        assertContains("<p>", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        response = WebClient.create(endPoint + TIKA_PATH + "/text")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        metadata =
                JsonMetadata.fromJson(new InputStreamReader(
                        ((InputStream)response.getEntity()), StandardCharsets.UTF_8));

        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotFound("<p>", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }
}
