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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.serialization.config.JsonConfigHelper;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.core.writer.TextMessageBodyWriter;

/**
 * With exception-reporting set to MESSAGE_REDACTED in the server config, no channel may leak
 * an exception message: /rmeta metadata, /unpack 422 body, /meta/{field} 422 body, and the
 * catch-all 500. Also pins the mapper chain: a WebApplicationException keeps its own status,
 * and a bad request config is a 400 with its reason rather than a redacted 500.
 * {@link StackTraceTest} pins the FULL default.
 */
public class RedactedStackTraceTest extends CXFTestBase {

    private static final String TEST_NULL = "test-documents/mock/null_pointer.xml";
    private static final String MESSAGE = "null pointer message";
    private static final String CLASS = "java.lang.NullPointerException";
    private static final String UNPACK_CONFIG_TEMPLATE = "/configs/cxf-unpack-test-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    private static Path unpackTempDir;

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true; // /rmeta/config must reach the config gate to be rejected by it
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> providers = new ArrayList<>();
        providers.add(new SingletonResourceProvider(new MetadataResource(tikaResource)));
        providers.add(new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
        providers.add(new SingletonResourceProvider(tikaResource));
        providers.add(new SingletonResourceProvider(new UnpackerResource(tikaResource)));
        providers.add(new SingletonResourceProvider(new ThrowingResource()));
        sf.setResourceProviders(providers);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(tikaResource.getExceptionReporting()));
        providers.add(new CatchAllExceptionMapper(tikaResource.getExceptionReporting()));
        providers.add(new BadRequestExceptionMapper());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    /** No production route throws a bare RuntimeException; this one stands in for one. */
    @jakarta.ws.rs.Path("/throwing")
    public static class ThrowingResource {
        @PUT
        @Produces(MediaType.TEXT_PLAIN)
        public String boom() {
            throw new IllegalStateException(MESSAGE);
        }
    }

    /** The server JVM's own policy: what the exception mappers apply to their bodies. */
    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        ObjectNode config = (ObjectNode) MAPPER.readTree(BASIC_CONFIG);
        redact(config);
        return new ByteArrayInputStream(
                MAPPER.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
    }

    private static void redact(ObjectNode config) {
        ((ObjectNode) config.get("parse-context")).putObject("exception-reporting")
                .put("level", "MESSAGE_REDACTED").put("maxLength", 10000);
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UNPACK_EMITTER_BASE_PATH", unpackTempDir.toAbsolutePath().toString());
        replacements.put("PLUGINS_PATHS",
                Paths.get("target/plugins").toAbsolutePath().toString().replace("\\", "/"));
        replacements.put("TIMEOUT_MILLIS", 60000L);
        JsonNode config = JsonConfigHelper.loadFromResource(UNPACK_CONFIG_TEMPLATE,
                CXFTestBase.class, replacements);
        redact((ObjectNode) config);
        return new ByteArrayInputStream(
                MAPPER.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Path getUnpackEmitterBasePath() {
        return unpackTempDir;
    }

    private static void assertRedacted(String s) {
        assertTrue(s.contains(CLASS), s);
        assertTrue(s.contains("\tat "), s);
        assertFalse(s.contains(MESSAGE), s);
    }

    @Test
    public void rmeta() throws Exception {
        Response response = WebClient.create(endPoint + "/rmeta")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
        assertEquals(200, response.getStatus());
        List<Metadata> list = JsonMetadataList.fromJson(
                new InputStreamReader((InputStream) response.getEntity(),
                        StandardCharsets.UTF_8));
        assertRedacted(list.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    @Test
    public void unpack() throws Exception {
        Response response = WebClient.create(endPoint + "/unpack")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
        assertEquals(422, response.getStatus());
        assertRedacted(getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void metaField() throws Exception {
        Response response = WebClient.create(endPoint + "/meta/Content-Type")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
        assertEquals(422, response.getStatus());
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertRedacted(body);
        // the body is the container exception itself, not re-wrapped with server frames
        assertFalse(body.contains("MetadataResource"), body);
    }

    /**
     * An entity-less WebApplicationException does reach {@link CatchAllExceptionMapper} -- CXF
     * only short-circuits WAEs that already carry a body -- so its passthrough branch is what
     * keeps a 404 a 404 rather than a 500 trace. {@code StackTraceTest#testEmptyParser} pins
     * the same branch for /unpack's 204.
     */
    @Test
    public void unknownPathStillReturns404() throws Exception {
        Response response = WebClient.create(endPoint + "/no-such-endpoint")
                .accept("text/plain")
                .put("hello");
        assertEquals(404, response.getStatus());
    }

    /** A resource-thrown non-WAE reaches the catch-all: 500 text/plain under the policy. */
    @Test
    public void resourceExceptionIsRedacted500() throws Exception {
        Response response = WebClient.create(endPoint + "/throwing")
                .accept("text/plain")
                .put("hello");
        assertEquals(500, response.getStatus());
        assertEquals("text/plain", response.getMediaType().getType() + "/"
                + response.getMediaType().getSubtype());
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(body.contains("java.lang.IllegalStateException"), body);
        assertTrue(body.contains("\tat "), body);
        assertFalse(body.contains(MESSAGE), body);
    }

    /**
     * A request config carrying a wire-blocked component is the caller's error: 400 with the
     * reason. As a 500 the reason would be exactly what the policy strips.
     */
    @Test
    public void wireBlockedRequestConfigIs400() throws Exception {
        Attachment file = new Attachment("file", "application/xml",
                ClassLoader.getSystemResourceAsStream(TEST_NULL));
        Attachment config = new Attachment("config", "application/json",
                new ByteArrayInputStream(
                        "{\"exception-reporting\":{\"level\":\"FULL\"}}"
                                .getBytes(StandardCharsets.UTF_8)));
        Response response = WebClient.create(endPoint + "/rmeta/config")
                .type("multipart/form-data")
                .post(new MultipartBody(List.of(file, config)));
        assertEquals(400, response.getStatus());
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(body.contains("may not be supplied via a request parseContext"), body);
    }
}
