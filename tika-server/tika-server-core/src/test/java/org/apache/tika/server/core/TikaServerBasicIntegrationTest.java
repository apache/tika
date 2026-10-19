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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.metrics.MetricsServer;

/**
 * Production-assembly checks that need a real server process but no special config: they
 * share one server started from {@code tika-config-server-basic.json}.
 */
public class TikaServerBasicIntegrationTest extends IntegrationTestBase {

    @TempDir
    private static Path WORKING_DIR;

    @BeforeAll
    public void startServer() throws Exception {
        startClassProcess(new String[]{"-config", getConfig("tika-config-server-basic.json")}, WORKING_DIR);
    }

    @Test
    public void testBasic() throws Exception {
        testBaseline();
    }

    /**
     * Production wiring pin: BadRequestExceptionMapper must be registered in the real
     * server assembly, or 400 bodies are empty -- the CXF tests register it by hand,
     * so only a forked-server test can catch a dropped registration.
     */
    @Test
    public void testBadRequestBodyReachesClient() throws Exception {
        Response response = WebClient
                .create(endPoint + RMETA_PATH + "/txet")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        assertEquals(400, response.getStatus());
        String body = new String(((InputStream) response.getEntity()).readAllBytes(), UTF_8);
        assertTrue(body.contains("Valid types"), body);
    }

    @Test
    public void testH2c() throws Exception {
        // Using HttpClient in order to check Http2 Version
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + STATUS_PATH))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
    }

    @Test
    public void testStdErrOutBasic() throws Exception {
        Response response = WebClient
                .create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("tk:content"));
        testBaseline();
    }

    /** Without --metricsPort the scrape endpoint must not leak onto the main port. */
    @Test
    public void testMetricsOffByDefault() throws Exception {
        assertEquals(404, WebClient.create(endPoint + MetricsServer.PATH).get().getStatus());
    }
}
