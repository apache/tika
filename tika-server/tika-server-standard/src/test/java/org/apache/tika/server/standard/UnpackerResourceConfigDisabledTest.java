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
package org.apache.tika.server.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

/**
 * Verifies that /unpack and /unpack/all honor enableUnsecureFeatures: when per-request
 * config injection is disabled (the default), a multipart "config" part is rejected
 * with 403. Counterpart to {@link UnpackerResourceWithConfigTest}, which covers the
 * enabled path.
 */
public class UnpackerResourceConfigDisabledTest extends CXFTestBase {

    private static final String BASE_PATH = "/unpack";
    private static final String ALL_PATH = BASE_PATH + "/all";

    // isEnableUnsecureFeatures() is intentionally NOT overridden; it defaults to false
    // so a config part must be rejected.

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class, new SingletonResourceProvider(new UnpackerResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    private Response postWithConfig(String path) {
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"test.txt\"");
        Attachment fileAtt = new Attachment("file",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream("{\"pdf-parser\":{}}".getBytes(StandardCharsets.UTF_8)));
        return WebClient
                .create(endPoint + path)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
    }

    @Test
    public void testConfigPartRejectedOnUnpackWhenDisabled() throws Exception {
        Response response = postWithConfig(BASE_PATH);
        assertEquals(403, response.getStatus());
        String msg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(msg.contains("Per-request configuration is disabled"),
                "expected the config-disabled message, got: " + msg);
    }

    @Test
    public void testConfigPartRejectedOnUnpackAllWhenDisabled() throws Exception {
        Response response = postWithConfig(ALL_PATH);
        assertEquals(403, response.getStatus());
    }
}
