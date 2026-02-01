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
import java.util.ArrayList;
import java.util.Arrays;
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
 * Tests for ConfigEndpointSecurityFilter.
 * Verifies that /config endpoints are gated behind enableUnsecureFeatures.
 */
public class ConfigEndpointSecurityTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";
    private static final String TEST_DOC = "test-documents/mock/hello_world.xml";

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
        // Add security filter with enableUnsecureFeatures=false
        providers.add(new ConfigEndpointSecurityFilter(false));
        sf.setProviders(providers);
    }

    @Test
    public void testConfigEndpointBlockedWhenDisabled() throws Exception {
        // POST to /tika/config should be blocked with 403
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xml\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), fileCd);

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt)));

        assertEquals(403, response.getStatus());
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("Config endpoints are disabled"));
    }

    @Test
    public void testConfigTextEndpointBlockedWhenDisabled() throws Exception {
        // POST to /tika/config/text should be blocked with 403
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xml\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), fileCd);

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config/text")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt)));

        assertEquals(403, response.getStatus());
    }

    @Test
    public void testConfigJsonEndpointBlockedWhenDisabled() throws Exception {
        // POST to /tika/config/json should be blocked with 403
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.xml\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), fileCd);

        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/config/json")
                .type("multipart/form-data")
                .post(new MultipartBody(Arrays.asList(fileAtt)));

        assertEquals(403, response.getStatus());
    }

    @Test
    public void testNonConfigEndpointsNotBlocked() throws Exception {
        // GET to /tika should still work
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .get();

        assertEquals(200, response.getStatus());
        assertEquals(TikaResource.GREETING, getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testPutEndpointsNotBlocked() throws Exception {
        // PUT to /tika/text should still work (no /config in path)
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));

        assertEquals(200, response.getStatus());
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(responseMsg.contains("hello world"));
    }

    @Test
    public void testPutJsonEndpointNotBlocked() throws Exception {
        // PUT to /tika/json should still work (no /config in path)
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));

        assertEquals(200, response.getStatus());
    }
}
