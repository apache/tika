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
package org.apache.tika.language.translate.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpUtilsTest {

    private ServerSocket server;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        baseUrl = "http://localhost:" + server.getLocalPort();
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    echo(socket);
                } catch (IOException e) {
                    //closed
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.close();
    }

    /**
     * Responds with "METHOD rawQuery contentType body"; ASCII bodies only.
     */
    private static void echo(Socket socket) throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
        String[] requestLine = reader.readLine().split(" ");
        String target = requestLine[1];
        String query = target.contains("?") ? target.substring(target.indexOf('?') + 1) : null;
        String contentType = null;
        int length = 0;
        String line;
        while (!(line = reader.readLine()).isEmpty()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-type:")) {
                contentType = line.substring(13).trim();
            } else if (lower.startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
        }
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            read += reader.read(body, read, length - read);
        }
        byte[] resp = (requestLine[0] + " " + query + " " + contentType + " " + new String(body))
                .getBytes(UTF_8);
        OutputStream os = socket.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: " + resp.length +
                "\r\n\r\n").getBytes(UTF_8));
        if (!"HEAD".equals(requestLine[0])) {
            os.write(resp);
        }
        os.flush();
    }

    @Test
    public void testGetEncodesAndSkipsNulls() throws Exception {
        // each call must start fresh; CXF's WebClient accumulated query params across calls
        for (int i = 0; i < 2; i++) {
            assertEquals("GET q=a+b%26c%3D%C3%A9&target=en null ",
                    HttpUtils.get(baseUrl + "/t", "q", "a b&c=é", "source", null,
                            "target", "en"));
        }
    }

    @Test
    public void testPostJson() throws Exception {
        assertEquals("POST null application/json {\"k\":\"v\"}",
                HttpUtils.postJson(baseUrl + "/t", "{\"k\":\"v\"}"));
    }

    @Test
    public void testHead() throws Exception {
        assertEquals(200, HttpUtils.head(baseUrl));
    }
}
