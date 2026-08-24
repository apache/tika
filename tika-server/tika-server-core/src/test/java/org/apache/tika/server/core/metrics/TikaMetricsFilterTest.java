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
package org.apache.tika.server.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.MaxRequestSizeFilter;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class TikaMetricsFilterTest extends CXFTestBase {

    private static final long MAX_BYTES = 64 * 1024;
    private static final String HELLO_WORLD = "test-documents/mock/hello_world.xml";

    private TikaServerMetrics metrics;

    @Path("/boom")
    public static class BoomResource {
        @GET
        public String get() {
            // Mapped by JAX-RS itself; an unmapped exception never reaches response filters.
            throw new InternalServerErrorException("boom");
        }
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class, BoomResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(tikaResource));
        sf.setResourceProvider(BoomResource.class, new SingletonResourceProvider(new BoomResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        metrics = new TikaServerMetrics(new MetricsConfig());
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new MaxRequestSizeFilter(MAX_BYTES));
        providers.add(new MaxRequestSizeFilter.RequestTooLargeExceptionMapper());
        providers.add(new TikaMetricsFilter(metrics));
        sf.setProviders(providers);
    }

    @Test
    public void testSuccessRecordsTimerAndSizes() throws Exception {
        long helloWorldBytes;
        try (InputStream is = ClassLoader.getSystemResourceAsStream(HELLO_WORLD)) {
            helloWorldBytes = is.readAllBytes().length;
        }
        Response response = WebClient
                .create(endPoint + "/tika/text")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(body.contains("hello world"), body);

        Timer timer = timer("tika", "PUT", "2xx");
        assertNotNull(timer);
        assertEquals(1, timer.count());
        DistributionSummary requestSize = metrics.getRegistry()
                .find(TikaServerMetrics.REQUEST_SIZE).tag("endpoint", "tika").summary();
        assertNotNull(requestSize);
        assertEquals(helloWorldBytes, requestSize.totalAmount(), 0.0);
        // Recorded after the last byte is flushed, so possibly a beat after the client returns.
        DistributionSummary responseSize = null;
        for (int i = 0; i < 50 && responseSize == null; i++) {
            responseSize = metrics.getRegistry()
                    .find(TikaServerMetrics.RESPONSE_SIZE).tag("endpoint", "tika").summary();
            if (responseSize == null) {
                Thread.sleep(50);
            }
        }
        assertNotNull(responseSize);
        assertTrue(responseSize.totalAmount() >= 11.0, "response bytes: " + responseSize.totalAmount());
    }

    @Test
    public void testPayloadTooLargeCountsAsRejected() {
        Response response = WebClient
                .create(endPoint + "/tika/text")
                .put(new ByteArrayInputStream(new byte[(int) MAX_BYTES * 4]));
        assertEquals(413, response.getStatus());
        assertEquals(1, timer("tika", "PUT", "4xx").count());
        assertEquals(1.0, rejected("payload_413").count(), 0.0);
    }

    @Test
    public void testThrownExceptionIs5xx() {
        Response response = WebClient.create(endPoint + "/boom").get();
        assertEquals(500, response.getStatus());
        // /boom is not a tika-server endpoint name, so it folds into "other"
        assertEquals(1, timer(TikaMetricsFilter.OTHER, "GET", "5xx").count());
    }

    @Test
    public void testUnmatchedPathsAreBoundedTags() {
        long timersBefore = -1;
        for (int i = 0; i < 25; i++) {
            Response response = WebClient.create(endPoint + "/no-such-" + i + "/x").get();
            assertEquals(404, response.getStatus());
            long timers = metrics.getRegistry().find(TikaServerMetrics.REQUESTS).timers().size();
            if (timersBefore >= 0) {
                assertEquals(timersBefore, timers, "a new request timer appeared for request " + i);
            }
            timersBefore = timers;
        }
        assertEquals(25, timer(TikaMetricsFilter.UNMATCHED, "GET", "4xx").count());
    }

    private Timer timer(String endpoint, String method, String status) {
        return metrics.getRegistry().find(TikaServerMetrics.REQUESTS)
                .tag("endpoint", endpoint).tag("method", method).tag("status", status).timer();
    }

    private Counter rejected(String reason) {
        return metrics.getRegistry().find(TikaServerMetrics.REJECTED).tag("reason", reason).counter();
    }
}
