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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * {@code meta/@name} in the remote service's XML response is tool-derived text, so
 * {@link NetworkParser} routes it through the {@code network:} {@link
 * org.apache.tika.metadata.KeyPrefix}. No test resource fixture is needed or wanted here: a
 * loopback {@link ServerSocket} stands in for the remote parse service (raw sockets only --
 * {@code com.sun.net.httpserver} is forbiddenapis-banned as a non-portable internal JDK class).
 * TIKA-4816.
 */
public class NetworkParserTest {

    private static final String RESPONSE_XML = "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><meta name=\"foo\" content=\"bar\"/></head><body/></html>";

    @Test
    public void telnetSchemeRoutesMetaNameThroughNetworkKeyPrefix() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    accepted.getInputStream().readAllBytes();   // client half-closes after writing
                    accepted.getOutputStream().write(RESPONSE_XML.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignore) {
                    // surfaced indirectly: the client-side assertions below fail instead
                }
            }, "test-network-parser-telnet-server");
            serverThread.start();

            URI uri = URI.create("telnet://localhost:" + server.getLocalPort());
            assertMetaNameRoutedThroughNetworkKeyPrefix(uri);
            serverThread.join(5000);
        }
    }

    @Test
    public void httpSchemeRoutesMetaNameThroughNetworkKeyPrefix() throws Exception {
        // Also exercises the getOutputStream()-before-getInputStream() ordering fix: the
        // original code called URLConnection#getInputStream() before the request had been
        // written, which deadlocks/fails against any real HTTP server (TIKA-4816).
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    // HttpURLConnection keeps the connection open for keep-alive (never
                    // half-closes), so -- unlike the telnet server above -- draining to EOF
                    // would hang forever; read exactly the declared request body instead.
                    readHttpRequest(accepted.getInputStream());
                    byte[] body = RESPONSE_XML.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = accepted.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/xml; charset=utf-8\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(body);
                    out.flush();
                } catch (Exception ignore) {
                    // surfaced indirectly: the client-side assertions below fail instead
                }
            }, "test-network-parser-http-server");
            serverThread.start();

            URI uri = URI.create("http://localhost:" + server.getLocalPort() + "/");
            assertMetaNameRoutedThroughNetworkKeyPrefix(uri);
            serverThread.join(5000);
        }
    }

    private static final Pattern CONTENT_LENGTH =
            Pattern.compile("(?i)content-length:\\s*(\\d+)");

    /** Reads a minimal HTTP request (headers, then the declared Content-Length body bytes) off
     * {@code in} without closing it and without needing the client to signal EOF. */
    private static void readHttpRequest(InputStream in) throws Exception {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int trailingCrLfCrLf = 0;   // count of the last 4 bytes matching "\r\n\r\n" so far
        int[] terminator = {'\r', '\n', '\r', '\n'};
        int b;
        while (trailingCrLfCrLf < terminator.length && (b = in.read()) != -1) {
            headerBytes.write(b);
            trailingCrLfCrLf = (b == terminator[trailingCrLfCrLf]) ? trailingCrLfCrLf + 1
                    : (b == '\r' ? 1 : 0);
        }
        Matcher m = CONTENT_LENGTH.matcher(headerBytes.toString(StandardCharsets.UTF_8));
        int contentLength = m.find() ? Integer.parseInt(m.group(1)) : 0;
        in.readNBytes(contentLength);
    }

    private void assertMetaNameRoutedThroughNetworkKeyPrefix(URI uri) throws Exception {
        NetworkParser parser = new NetworkParser(uri, Collections.singleton(MediaType.OCTET_STREAM));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis =
                TikaInputStream.get("posted document".getBytes(StandardCharsets.UTF_8))) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("bar", metadata.get("network:foo"));
        assertNull(metadata.get("foo"), "the unprefixed legacy key must not appear");
    }
}
