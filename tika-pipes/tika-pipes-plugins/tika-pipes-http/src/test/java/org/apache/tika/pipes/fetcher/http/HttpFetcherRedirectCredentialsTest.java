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
package org.apache.tika.pipes.fetcher.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.http.config.HttpFetcherConfig;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Credentials reach the host a fetch started at and no other. The two origins differ by port,
 * which HttpClient treats as another host.
 */
public class HttpFetcherRedirectCredentialsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASIC = "Basic "
            + Base64.getEncoder().encodeToString("user:secret".getBytes(UTF_8));

    private Origin first;
    private Origin second;

    @BeforeEach
    public void before() throws IOException {
        first = new Origin();
        second = new Origin();
    }

    @AfterEach
    public void after() throws IOException {
        first.close();
        second.close();
    }

    @Test
    public void testJwtAndCookieDoNotFollowARedirectToAnotherHost() throws Exception {
        first.redirect("/start", second.url("/doc"));
        second.document("/doc");

        Metadata metadata = new Metadata();
        metadata.add("httpRequestHeaders", "Cookie: session=abc");
        metadata.add("httpRequestHeaders", "X-Trace: 1");
        assertEquals("/doc", fetch(jwtConfig(), first.url("/start"), metadata));

        Seen atFirst = first.seen.get(0);
        assertTrue(atFirst.authorization().startsWith("Bearer "), atFirst.toString());
        assertEquals("session=abc", atFirst.cookie());

        Seen atSecond = second.seen.get(0);
        assertNull(atSecond.authorization());
        assertNull(atSecond.cookie());
        assertEquals("1", atSecond.trace());
    }

    @Test
    public void testCredentialsStayOffEveryLaterHop() throws Exception {
        // no User-Agent: Authorization is the only header on the request, and HttpClient refills
        // a redirect that has no headers from the original request
        first.redirect("/start", second.url("/hop"));
        second.redirect("/hop", second.url("/doc"));
        second.document("/doc");

        assertEquals("/doc", fetch(jwtConfig(), first.url("/start"), new Metadata()));

        assertEquals(2, second.seen.size());
        for (Seen seen : second.seen) {
            assertNull(seen.authorization(), seen.toString());
        }
    }

    @Test
    public void testBasicChallengeFromAnotherHostIsNotAnswered() throws Exception {
        first.redirect("/start", second.url("/doc"));
        second.challenge("/doc");

        IOException e = assertThrows(IOException.class,
                () -> fetch(basicConfig(), first.url("/start"), new Metadata()));

        assertTrue(e.getMessage().contains("bad status code: 401"), e.getMessage());
        assertEquals(1, second.seen.size());
        assertNull(second.seen.get(0).authorization());
    }

    @Test
    public void testSameHostRedirectKeepsJwt() throws Exception {
        first.redirect("/start", first.url("/doc"));
        first.document("/doc");

        assertEquals("/doc", fetch(jwtConfig(), first.url("/start"), new Metadata()));

        assertEquals(2, first.seen.size());
        for (Seen seen : first.seen) {
            assertTrue(seen.authorization().startsWith("Bearer "), seen.toString());
        }
    }

    @Test
    public void testSameHostRedirectStillAnswersBasicChallenge() throws Exception {
        first.redirect("/start", first.url("/doc"));
        first.challenge("/doc");

        assertEquals("/doc", fetch(basicConfig(), first.url("/start"), new Metadata()));

        assertEquals(BASIC, first.seen.get(first.seen.size() - 1).authorization());
    }

    private static HttpFetcherConfig redirectingConfig() {
        return new HttpFetcherConfig().setMaxRedirects(5);
    }

    private static HttpFetcherConfig jwtConfig() {
        HttpFetcherConfig config = redirectingConfig();
        config.setJwtSecret("0123456789abcdef0123456789abcdef");
        config.setJwtIssuer("issuer");
        config.setJwtSubject("subject");
        return config;
    }

    private static HttpFetcherConfig basicConfig() {
        return redirectingConfig().setAuthScheme("basic").setUserName("user").setPassword("secret");
    }

    private static String fetch(HttpFetcherConfig config, String url, Metadata metadata)
            throws Exception {
        HttpFetcher fetcher = (HttpFetcher) new HttpFetcherFactory().buildExtension(
                new ExtensionConfig("id", "factoryPluginId", OBJECT_MAPPER.writeValueAsString(config)));
        try (InputStream is = fetcher.fetch(url, metadata, new ParseContext())) {
            return new String(is.readAllBytes(), UTF_8);
        }
    }

    private record Seen(String authorization, String cookie, String trace) {
    }

    /** A loopback server that records what each request carried and answers with its path. */
    private static class Origin {
        private final ServerSocket server;
        private final Map<String, Function<Map<String, String>, String>> routes =
                new ConcurrentHashMap<>();
        private final List<Seen> seen = new CopyOnWriteArrayList<>();

        Origin() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread acceptor = new Thread(this::serve);
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getLocalPort() + path;
        }

        void redirect(String path, String location) {
            routes.put(path, headers -> response("302 Found", "Location: " + location + "\r\n", ""));
        }

        void document(String path) {
            routes.put(path, headers -> response("200 OK", "", path));
        }

        void challenge(String path) {
            routes.put(path, headers -> headers.containsKey("authorization")
                    ? response("200 OK", "", path)
                    : response("401 Unauthorized", "WWW-Authenticate: Basic realm=\"tika\"\r\n", ""));
        }

        private static String response(String status, String headers, String body) {
            return "HTTP/1.1 " + status + "\r\n" + headers + "Content-Length: " + body.length()
                    + "\r\nConnection: close\r\n\r\n" + body;
        }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    answer(client);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void answer(Socket client) throws IOException {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), UTF_8));
            String path = in.readLine().split(" ")[1];
            Map<String, String> headers = new HashMap<>();
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                int colon = line.indexOf(':');
                headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
            seen.add(new Seen(headers.get("authorization"), headers.get("cookie"),
                    headers.get("x-trace")));
            client.getOutputStream().write(routes.get(path).apply(headers).getBytes(UTF_8));
        }

        void close() throws IOException {
            server.close();
        }
    }
}
