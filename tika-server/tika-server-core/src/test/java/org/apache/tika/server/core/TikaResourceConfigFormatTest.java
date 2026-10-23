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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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

import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

/**
 * A format segment on /tika/config/&lt;format&gt; fixes the route's {@code @Produces}, so a
 * config part must not be able to change the body's format out from under it.
 */
public class TikaResourceConfigFormatTest extends CXFTestBase {

    private static final String TEST_DOC = "test-documents/mock/hello_world.xml";
    private static final String MARKDOWN_FACTORY =
            "{\"basic-content-handler-factory\": {\"type\": \"MARKDOWN\"}}";
    private static final String XML_FACTORY =
            "{\"basic-content-handler-factory\": {\"type\": \"XML\"}}";

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
    }

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
    public void testFormatSegmentPlusConfigFactoryIsBadRequest() throws Exception {
        assertEquals(400, post("/tika/config/xml", MARKDOWN_FACTORY).getStatus());
    }

    /** Agreeing is still a conflict: the rule is syntactic, so callers name the handler once. */
    @Test
    public void testAgreeingSegmentAndConfigFactoryIsAlsoBadRequest() throws Exception {
        assertEquals(400, post("/tika/config/xml", XML_FACTORY).getStatus());
    }

    @Test
    public void testFormatSegmentAloneStillSetsTheFormat() throws Exception {
        Response response = post("/tika/config/xml", null);
        assertEquals("text/xml;charset=utf-8", response.getHeaderString("Content-Type"));
        assertTrue(body(response).startsWith("<html"));
        assertFalse(body(post("/tika/config/md", null)).contains("<p>"));
    }

    /** No format segment: the config part is the only thing naming a handler, so it applies. */
    @Test
    public void testBareConfigStillHonorsTheConfigPartFactory() throws Exception {
        assertTrue(body(post("/tika/config", XML_FACTORY)).startsWith("<html"));
    }

    private Response post(String path, String configJson) throws Exception {
        ContentDisposition cd =
                new ContentDisposition("form-data; name=\"file\"; filename=\"hello_world.xml\"");
        List<Attachment> atts = new ArrayList<>();
        atts.add(new Attachment("file", ClassLoader.getSystemResourceAsStream(TEST_DOC), cd));
        if (configJson != null) {
            atts.add(new Attachment("config", "application/json",
                    new ByteArrayInputStream(configJson.getBytes(UTF_8))));
        }
        return WebClient.create(endPoint + path)
                .type("multipart/form-data")
                .post(new MultipartBody(atts));
    }

    private String body(Response response) throws Exception {
        assertEquals(200, response.getStatus());
        return getStringFromInputStream((InputStream) response.getEntity()).trim();
    }
}
