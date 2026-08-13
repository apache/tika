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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class MaxRequestSizeFilterTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";
    private static final long MAX_BYTES = 500;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(tikaResource));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new MaxRequestSizeFilter(MAX_BYTES));
        providers.add(new MaxRequestSizeFilter.RequestTooLargeExceptionMapper());
        sf.setProviders(providers);
    }

    @Test
    public void testOverLimitRejected() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .put(new ByteArrayInputStream(body((int) MAX_BYTES * 4)));

        assertEquals(413, response.getStatus());
    }

    @Test
    public void testUnderLimitAccepted() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .put(new ByteArrayInputStream(body(50)));

        assertNotEquals(413, response.getStatus(),
                "a body well under the limit must not be rejected");
    }

    /**
     * A filename whose extension contains a path separator previously reached
     * Files.createTempFile and threw IllegalArgumentException, surfacing as a 500
     * driven entirely by a request header.
     */
    @Test
    public void testHostileFilenameDoesNotError() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "/text")
                .header("Content-Disposition", "attachment; filename=\"a.b/../../c\"")
                .put(new ByteArrayInputStream(body(50)));

        assertNotEquals(500, response.getStatus(),
                "a hostile filename suffix must not produce a server error");
    }

    /**
     * Chunked uploads carry no usable Content-Length, so the declared-length check cannot
     * fire and the counting stream is the only thing enforcing the limit.
     */
    @Test
    public void testOverLimitRejectedWhenChunked() throws Exception {
        WebClient client = WebClient.create(endPoint + TIKA_PATH + "/text");
        WebClient
                .getConfig(client)
                .getRequestContext()
                .put("use.async.http.conduit", Boolean.FALSE);
        WebClient
                .getConfig(client)
                .getHttpConduit()
                .getClient()
                .setAllowChunking(true);

        Response response = client.put(new ByteArrayInputStream(body((int) MAX_BYTES * 4)));

        assertEquals(413, response.getStatus(),
                "an over-limit chunked body must get the same 413 as a declared one");
        assertContains(MaxRequestSizeFilter.TOO_LARGE_MESSAGE,
                getStringFromInputStream((java.io.InputStream) response.getEntity()));
    }

    private static byte[] body(int approxBytes) {
        StringBuilder sb = new StringBuilder("<html><body>");
        while (sb.length() < approxBytes) {
            sb.append("aaaaaaaaaa");
        }
        return sb
                .append("</body></html>")
                .toString()
                .getBytes(UTF_8);
    }
}
