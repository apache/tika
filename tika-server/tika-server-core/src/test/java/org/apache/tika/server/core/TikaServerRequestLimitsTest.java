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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

/** The CXF attachment-max-size limit set from {@link TikaServerConfig#getMaxAttachmentBytes()}. */
public class TikaServerRequestLimitsTest extends CXFTestBase {

    private static final long MAX_ATTACHMENT_BYTES = 2000;

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

    @Override
    protected TikaServerConfig getTikaServerConfig() {
        TikaServerConfig config = super.getTikaServerConfig();
        config.setMaxAttachmentBytes(MAX_ATTACHMENT_BYTES);
        return config;
    }

    @Test
    public void testAttachmentUnderLimit() throws Exception {
        Response response = postForm(mockXml(500));
        assertEquals(200, response.getStatus());
        assertContains("hello world", getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testAttachmentOverLimit() throws Exception {
        Response response = postForm(mockXml((int) MAX_ATTACHMENT_BYTES * 4));
        assertEquals(413, response.getStatus());
    }

    private Response postForm(byte[] bytes) {
        Attachment attachment = new Attachment("upload", new ByteArrayInputStream(bytes),
                new ContentDisposition("form-data; name=\"upload\"; filename=\"test.xml\""));
        return WebClient
                .create(endPoint + "/tika/form")
                .type("multipart/form-data")
                .accept("text/plain")
                .post(Arrays.asList(attachment));
    }

    /** A mock-parser document whose text is "hello world" followed by padding to the requested size. */
    private static byte[] mockXml(int size) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mock>"
                + "<write element=\"p\">hello world</write><write element=\"p\">");
        String tail = "</write></mock>";
        while (sb.length() + tail.length() < size) {
            sb.append("padding ");
        }
        sb.append(tail);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
