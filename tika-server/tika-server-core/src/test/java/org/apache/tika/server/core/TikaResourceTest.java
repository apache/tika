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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    public static final String TEST_HELLO_WORLD_HEADING = "test-documents/mock/hello_world_heading.xml";
    public static final String TEST_NULL_POINTER = "test-documents/mock/null_pointer.xml";

    private static final String TIKA_PATH = "/tika";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(tikaResource));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new BadRequestExceptionMapper());
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
        // Exception detail is reported in full. Tika does not redact it: any scheme that
        // strips the message has to parse the rendered trace, and it would in any case be
        // undone by the same detail travelling in tk:exception:* on other responses. Filter
        // metadata before forwarding it somewhere less trusted.
        assertContains("TikaException", metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
        assertContains("null pointer message", metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
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

    /**
     * The document is spooled to a temp file, so without this the fetcher's path is what
     * comes back as tk:source-path -- and, absent a client filename, as tk:resource-name,
     * which is the field downstream consumers key document identity on.
     */
    @Test
    public void testSpoolNameDoesNotLeak() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) response.getEntity(), StandardCharsets.UTF_8));

        assertNull(metadata.get(TikaCoreProperties.SOURCE_PATH),
                "the server's spool path must not be reported to the caller");
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        assertTrue(name == null || !name.startsWith("tika-"),
                "no client filename was sent, so the spool name must not stand in as one: " + name);
    }

    /** A filename the caller did supply is theirs, and must survive. */
    @Test
    public void testClientFilenameIsPreserved() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .header("Content-Disposition", "attachment; filename=\"my-report.xml\"")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) response.getEntity(), StandardCharsets.UTF_8));

        assertEquals("my-report.xml", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }


    /** An unspecified handler takes the default, which is markdown, not text. */
    @Test
    public void testJsonDefaultsToMarkdown() throws Exception {
        String dflt = jsonContent("/json", TEST_HELLO_WORLD_HEADING);
        String md = jsonContent("/json/md", TEST_HELLO_WORLD_HEADING);
        String text = jsonContent("/json/text", TEST_HELLO_WORLD_HEADING);

        assertContains("# Chapter One", md);
        assertNotFound("# Chapter One", text);
        assertEquals(md, dflt, "/tika/json with no handler must match /tika/json/md");
    }

    /** A handler name we don't recognize is the caller's typo: 400, not a silent default. */
    @Test
    public void testUnknownHandlerNameIsRejected() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json/txet")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        assertEquals(400, response.getStatus());
        // The actionable message must reach the client, not just the server log (TIKA-4809).
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("Valid types", body);
        assertContains("txet", body);
    }

    private String jsonContent(String path, String doc) throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + path)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(doc));
        assertEquals(200, response.getStatus(), path + " should have succeeded");
        return contentOf(response);
    }

    private static String contentOf(Response response) throws Exception {
        Metadata metadata = JsonMetadata.fromJson(new InputStreamReader(
                (InputStream) response.getEntity(), StandardCharsets.UTF_8));
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        return content == null ? "" : content.trim();
    }

    /** Bare /tika must return markdown -- provably distinct from plain text via the heading. */
    @Test
    public void testBareTikaIsMarkdownNotText() throws Exception {
        assertContains("# Chapter One", putRawContent("", TEST_HELLO_WORLD_HEADING));
        assertNotFound("# Chapter One", putRawContent("/text", TEST_HELLO_WORLD_HEADING));
    }

    private String putRawContent(String pathSuffix, String doc) throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + pathSuffix)
                .put(ClassLoader.getSystemResourceAsStream(doc));
        assertEquals(200, response.getStatus(), pathSuffix + " should have succeeded");
        return getStringFromInputStream((InputStream) response.getEntity());
    }

    /** POST /tika/config defaults to markdown; /config/text is body-only text. */
    @Test
    public void testConfigFamilyDefaults() throws Exception {
        assertContains("# Chapter One", postFileContent("/config", "text/plain"));
        assertNotFound("# Chapter One", postFileContent("/config/text", "text/plain"));
    }

    /** The multipart JSON sibling endpoints, incl. the {handler} variant. */
    @Test
    public void testConfigJsonHandlerPlumbs() throws Exception {
        Response md = postFile("/config/json/md", "application/json");
        assertEquals(200, md.getStatus());
        assertContains("# Chapter One", contentOf(md));

        Response text = postFile("/config/json/text", "application/json");
        assertEquals(200, text.getStatus());
        assertNotFound("# Chapter One", contentOf(text));

        Response dflt = postFile("/config/json", "application/json");
        assertEquals(200, dflt.getStatus());
        assertContains("# Chapter One", contentOf(dflt));
    }

    private Response postFile(String pathSuffix, String accept) {
        org.apache.cxf.jaxrs.ext.multipart.ContentDisposition cd =
                new org.apache.cxf.jaxrs.ext.multipart.ContentDisposition(
                        "form-data; name=\"file\"; filename=\"hello.xml\"");
        org.apache.cxf.jaxrs.ext.multipart.Attachment att =
                new org.apache.cxf.jaxrs.ext.multipart.Attachment("file",
                        ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_HEADING), cd);
        return WebClient
                .create(endPoint + TIKA_PATH + pathSuffix)
                .type("multipart/form-data")
                .accept(accept)
                .post(new org.apache.cxf.jaxrs.ext.multipart.MultipartBody(List.of(att)));
    }

    private String postFileContent(String pathSuffix, String accept) throws Exception {
        Response response = postFile(pathSuffix, accept);
        assertEquals(200, response.getStatus(), pathSuffix + " should have succeeded");
        return getStringFromInputStream((InputStream) response.getEntity());
    }
}
