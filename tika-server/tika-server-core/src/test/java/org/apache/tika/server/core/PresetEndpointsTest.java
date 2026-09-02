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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/**
 * Routing and behavior of the {@code /preset/{name}} endpoints: the preset
 * segment sits directly after the resource root, must win over the wildcard
 * routes ({@code /rmeta/{handlerType}}, {@code /unpack/{id}}), and an unknown
 * name answers 404.
 */
public class PresetEndpointsTest extends CXFTestBase {

    private static final String HELLO_WORLD = "test-documents/mock/hello_world.xml";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected InputStream getTikaConfigInputStream() throws java.io.IOException {
        ObjectNode config = (ObjectNode) MAPPER.readTree(BASIC_CONFIG);
        ObjectNode presets = config.putObject("presets");
        presets.putObject("xml-content")
                .putObject("basic-content-handler-factory").put("type", "XML");
        // exception-reporting is wire-blocked for caller-supplied config; a preset is
        // operator config and must be able to bind it (resolved worker-side)
        presets.putObject("reporting")
                .putObject("exception-reporting").put("maxLength", 512);
        return new ByteArrayInputStream(
                MAPPER.writeValueAsString(config).getBytes(UTF_8));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class, UnpackerResource.class,
                TikaResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
        sf.setResourceProvider(UnpackerResource.class,
                new SingletonResourceProvider(new UnpackerResource(tikaResource)));
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(tikaResource));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testRmetaPresetApplies() throws Exception {
        Response response = WebClient
                .create(endPoint + "/rmeta/preset/xml-content")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata metadata = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        // markup in the content proves the preset's XML handler replaced the
        // markdown default, which emits plain "hello world"
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("<body><p>hello world</p>", content);
    }

    @Test
    public void testRmetaUnknownPresetIs404() throws Exception {
        Response response = WebClient
                .create(endPoint + "/rmeta/preset/nope")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testUnpackPresetRouteBeatsWildcard() throws Exception {
        // /unpack/{id:(/.*)?} is a catch-all; the preset literal must win, so an
        // unknown preset answers 404 from the preset route, not the wildcard
        Response response = WebClient
                .create(endPoint + "/unpack/preset/nope")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testTikaPresetApplies() throws Exception {
        Response response = WebClient
                .create(endPoint + "/tika/preset/xml-content")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        String content = getStringFromInputStream((InputStream) response.getEntity());
        // the preset's XML content handler wins over the endpoint's markdown default
        assertContains("<body><p>hello world</p>", content);
    }

    @Test
    public void testExplicitFormatSegmentWinsOverPresetFactory() throws Exception {
        // /tika/preset/xml-content/text: the URL's own format segment beats the XML
        // factory the preset binds -- it rides the request delta, which the worker
        // overlays on top of the preset
        Response response = WebClient
                .create(endPoint + "/tika/preset/xml-content/text")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        String content = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("hello world", content);
        assertFalse(content.contains("<body>"), "explicit /text segment must win: " + content);
    }

    @Test
    public void testWireBlockedComponentWorksInPreset() throws Exception {
        // previously this 500'd: the preset was pushed through the untrusted wire
        // deserializer, which refuses exception-reporting
        Response response = WebClient
                .create(endPoint + "/tika/preset/reporting")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        assertContains("hello world",
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testTransposedUnpackPresetUrlIs404() throws Exception {
        // /unpack/all/preset/{name} would otherwise fall into the /all{id} wildcard
        // and run with no preset applied -- a silent wrong-config success
        Response response = WebClient
                .create(endPoint + "/unpack/all/preset/xml-content")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(404, response.getStatus());

        response = WebClient
                .create(endPoint + "/unpack/preset")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testTikaUnknownPresetIs404() throws Exception {
        Response response = WebClient
                .create(endPoint + "/tika/preset/nope")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(404, response.getStatus());
    }
}
