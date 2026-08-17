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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/**
 * With {@code maxInlineBytes} at 0 every request takes the spool branch, which the rest of the
 * suite never reaches -- the test corpus is far below the 10MB default, so those runs all go
 * inline. This is the only coverage of the branch that writes to the fetcher's basePath and then
 * scrubs the spool filename back out of the returned metadata.
 */
public class ForcedSpoolPathTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";
    private static final String RMETA_PATH = "/rmeta";
    private static final String TEST_DOC = "test-documents/mock/hello_world.xml";

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        InputStream base = super.getPipesConfigInputStream();
        if (base == null) {
            return null;
        }
        JsonNode config = new com.fasterxml.jackson.databind.ObjectMapper().readTree(base);
        ((ObjectNode) config.get("pipes")).put("maxInlineBytes", 0);
        return new ByteArrayInputStream(
                config.toString().getBytes(UTF_8));
    }

    @Override
    protected String getPipesInputPath() {
        return "target/pipes-input-forced-spool";
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class, RecursiveMetadataResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(tikaResource));
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new BadRequestExceptionMapper());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void spooledTikaRequestParses() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String content = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals(200, response.getStatus());
        assertContains("hello world", content);
    }

    /**
     * The spool filename is server-internal and names a file already deleted; it must not leak
     * out as the document's identity.
     */
    @Test
    public void spooledRequestDoesNotLeakSpoolName() throws Exception {
        Response response = WebClient
                .create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String json = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals(200, response.getStatus());
        assertFalse(json.contains("tika-"),
                "spool filename leaked into the response: " + json);
    }
}
