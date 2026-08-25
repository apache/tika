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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

        @GET
        @Path("mid-write")
        public StreamingOutput midWrite() {
            return out -> {
                out.write("partial".getBytes(StandardCharsets.UTF_8));
                out.flush();
                throw new InternalServerErrorException("mid-write");
            };
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
        metrics = new TikaServerMetrics();
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new MaxRequestSizeFilter(MAX_BYTES));
        providers.add(new MaxRequestSizeFilter.RequestTooLargeExceptionMapper());
        providers.add(new TikaMetricsFilter(metrics, Set.of("tika", "rmeta")));
        sf.setProviders(providers);
    }

    @Test
    public void testSuccessRecordsTimerAndSize() throws Exception {
        long helloWorldBytes;
        try (InputStream is = ClassLoader.getSystemResourceAsStream(HELLO_WORLD)) {
            helloWorldBytes = is.readAllBytes().length;
        }
        long before = count("tika", "PUT", "2xx");
        long sizeBefore = sizeCount("tika");
        double sizeTotalBefore = sizeTotal("tika");
        Response response = WebClient
                .create(endPoint + "/tika/text")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(HELLO_WORLD));
        assertEquals(200, response.getStatus());
        String body = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(body.contains("hello world"), body);

        assertEquals(before + 1, count("tika", "PUT", "2xx"));
        assertEquals(sizeBefore + 1, sizeCount("tika"));
        assertEquals(sizeTotalBefore + helloWorldBytes, sizeTotal("tika"), 0.0);
    }

    /** WebClient streams an InputStream chunked, so this is the BoundedInputStream + mapper path. */
    @Test
    public void testChunkedPayloadTooLargeCountsAsRejected() {
        long before = count("tika", "PUT", "4xx");
        double rejectedBefore = rejected("payload_413");
        Response response = WebClient
                .create(endPoint + "/tika/text")
                .put(new ByteArrayInputStream(new byte[(int) MAX_BYTES * 4]));
        assertEquals(413, response.getStatus());
        assertEquals(before + 1, count("tika", "PUT", "4xx"));
        assertEquals(rejectedBefore + 1, rejected("payload_413"), 0.0);
    }

    /** A declared Content-Length over the limit is refused via abortWith before the body is read. */
    @Test
    public void testDeclaredLengthTooLargeCountsAsRejected() throws Exception {
        long before = count("tika", "PUT", "4xx");
        double rejectedBefore = rejected("payload_413");
        HttpResponse<String> response = put(
                HttpRequest.BodyPublishers.ofByteArray(new byte[(int) MAX_BYTES * 4]));
        assertEquals(413, response.statusCode());
        assertEquals(before + 1, count("tika", "PUT", "4xx"));
        assertEquals(rejectedBefore + 1, rejected("payload_413"), 0.0);
    }

    @Test
    public void testChunkedRequestHasNoSizeSample() throws Exception {
        long before = count("tika", "PUT", "2xx");
        long sizeBefore = sizeCount("tika");
        HttpResponse<String> response = put(HttpRequest.BodyPublishers.ofInputStream(
                () -> ClassLoader.getSystemResourceAsStream(HELLO_WORLD)));
        assertEquals(200, response.statusCode());
        assertEquals(before + 1, count("tika", "PUT", "2xx"));
        assertEquals(sizeBefore, sizeCount("tika"), "no Content-Length must mean no size sample");
    }

    @Test
    public void testThrownExceptionIs5xx() {
        long before = count(TikaMetricsFilter.OTHER, "GET", "5xx");
        Response response = WebClient.create(endPoint + "/boom").get();
        assertEquals(500, response.getStatus());
        // /boom is not a tika-server endpoint name, so it folds into "other"
        assertEquals(before + 1, count(TikaMetricsFilter.OTHER, "GET", "5xx"));
    }

    /** CXF re-runs the response filters for the mapped exception; the request must be timed once. */
    @Test
    public void testMidWriteExceptionRecordedOnce() {
        long before = count(TikaMetricsFilter.OTHER, "GET");
        WebClient.create(endPoint + "/boom/mid-write").get();
        assertEquals(before + 1, count(TikaMetricsFilter.OTHER, "GET"));
    }

    private HttpResponse<String> put(HttpRequest.BodyPublisher body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(endPoint + "/tika/text"))
                        .header("Accept", "text/plain").PUT(body).build(),
                HttpResponse.BodyHandlers.ofString());
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
        assertEquals(25, count(TikaMetricsFilter.UNMATCHED, "GET", "4xx"));
    }

    @Test
    public void testUnknownMethodsAreBoundedTags() throws Exception {
        long timersBefore = -1;
        for (int i = 0; i < 25; i++) {
            assertTrue(rawRequest("BOGUS" + i + " /tika HTTP/1.1").startsWith("HTTP/1.1"));
            long timers = metrics.getRegistry().find(TikaServerMetrics.REQUESTS).timers().size();
            if (timersBefore >= 0) {
                assertEquals(timersBefore, timers, "a new request timer appeared for method BOGUS" + i);
            }
            timersBefore = timers;
        }
        assertNull(timer("tika", "BOGUS0", "4xx"), "the raw method must not become a tag value");
        assertEquals(25, count("tika", "other"), "requests with unknown methods must still be counted");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "GET, GET", "PUT, PUT", "BOGUS, other", "get, other", "null, other"})
    public void testMethodTagIsBounded(String method, String expected) {
        assertEquals(expected, TikaServerMetrics.methodTag(method));
    }

    /** Arbitrary method tokens: HttpURLConnection and java.net.http both refuse to send them. */
    private String rawRequest(String requestLine) throws Exception {
        URI uri = URI.create(endPoint);
        try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(30000);
            socket.getOutputStream().write(
                    (requestLine + "\r\nHost: " + uri.getHost() + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private Timer timer(String endpoint, String method, String status) {
        return metrics.getRegistry().find(TikaServerMetrics.REQUESTS)
                .tag("endpoint", endpoint).tag("method", method).tag("status", status).timer();
    }

    /** Registry is shared across the class (PER_CLASS), so callers assert deltas; absent meters read 0. */
    private long count(String endpoint, String method, String status) {
        Timer t = timer(endpoint, method, status);
        return t == null ? 0 : t.count();
    }

    private long count(String endpoint, String method) {
        return metrics.getRegistry().find(TikaServerMetrics.REQUESTS)
                .tag("endpoint", endpoint).tag("method", method).timers().stream()
                .mapToLong(Timer::count).sum();
    }

    private DistributionSummary size(String endpoint) {
        return metrics.getRegistry().find(TikaServerMetrics.REQUEST_SIZE)
                .tag("endpoint", endpoint).summary();
    }

    private long sizeCount(String endpoint) {
        DistributionSummary s = size(endpoint);
        return s == null ? 0 : s.count();
    }

    private double sizeTotal(String endpoint) {
        DistributionSummary s = size(endpoint);
        return s == null ? 0 : s.totalAmount();
    }

    private double rejected(String reason) {
        Counter c = metrics.getRegistry().find(TikaServerMetrics.REJECTED).tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }
}
