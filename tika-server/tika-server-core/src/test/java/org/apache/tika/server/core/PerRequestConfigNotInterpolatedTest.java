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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/**
 * {@code ${env:NAME}} is resolved in the startup config only (TIKA-4909). A request's config
 * part must reach the parse as the literal string: a caller must not be able to read the
 * server's environment through it.
 */
public class PerRequestConfigNotInterpolatedTest extends CXFTestBase {

    private static final String TEST_DOC = "test-documents/mock/hello_world.xml";

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new BadRequestExceptionMapper());
        sf.setProviders(providers);
    }

    @Test
    public void testRequestConfigKeepsEnvReferenceLiteral() throws Exception {
        String var = System.getenv().containsKey("PATH") ? "PATH" : "HOME";
        // renames Content-Type to whatever the value resolves to: the literal, if not interpolated
        String config = "{\"metadata-filters\": [ { \"field-name-mapping-filter\": {"
                + "\"mappings\": { \"Content-Type\": \"${env:" + var + "}\" },"
                + "\"excludeUnmapped\": false } } ] }";
        ContentDisposition cd =
                new ContentDisposition("form-data; name=\"file\"; filename=\"hello_world.xml\"");
        List<Attachment> atts = new ArrayList<>();
        atts.add(new Attachment("file", ClassLoader.getSystemResourceAsStream(TEST_DOC), cd));
        atts.add(new Attachment("config", "application/json",
                new ByteArrayInputStream(config.getBytes(UTF_8))));
        Response response = WebClient.create(endPoint + "/rmeta/config")
                .type("multipart/form-data").accept("application/json")
                .post(new MultipartBody(atts));
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        Metadata m = JsonMetadataList.fromJson(reader).get(0);
        assertNotNull(m.get("${env:" + var + "}"), "the literal name is the mapped field");
        assertNull(m.get(System.getenv(var)), "the environment value never became a field name");
        assertNull(m.get("Content-Type"), "the mapping did apply");
    }
}
