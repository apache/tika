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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for a hosted inference engine, for the tika-server UAT. Speaks the two
 * OpenAI-shaped endpoints Tika's engines call, plus the health check, with no dependencies
 * beyond the JDK, so it runs from source:
 *
 * <pre>
 *   java release-tools/uat/MockInferenceServer.java [port] [--flaky]
 * </pre>
 *
 * <ul>
 *   <li>{@code GET /v1/models}: the VLM health check.</li>
 *   <li>{@code POST /v1/embeddings}: one 4-dimension unit vector per input, keyed by index,
 *       whatever the input's modality (text, image, audio or video); the vector encodes the
 *       input's position so a client can tell vectors apart.</li>
 *   <li>{@code POST /v1/chat/completions}: fixed markdown with an HTML table, as a document-OCR
 *       model returns it.</li>
 * </ul>
 * With {@code --flaky}, every other POST is answered 429 first, the way a concurrency-capped
 * engine refuses the excess, so a client's retry is what makes the run pass.
 * {@code GET /stats} reports the counts, for a test to assert on.
 */
public class MockInferenceServer {

    static final String OCR_MARKDOWN = "# Mock OCR\n\nThe quick brown fox.\n\n"
            + "<table>\n<tr><th>Region</th><th>Q1</th></tr>\n"
            + "<tr><td>North</td><td>120</td></tr>\n</table>\n";

    static final AtomicInteger POSTS = new AtomicInteger();
    static final AtomicInteger REFUSED = new AtomicInteger();
    static final AtomicInteger EMBEDDED_INPUTS = new AtomicInteger();
    static final AtomicInteger OCR_CALLS = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        int port = 0;
        boolean flaky = false;
        for (String a : args) {
            if ("--flaky".equals(a)) {
                flaky = true;
            } else {
                port = Integer.parseInt(a);
            }
        }
        boolean refuseEveryOther = flaky;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/v1/models", ex -> reply(ex, 200,
                "{\"data\":[{\"id\":\"mock-embed\"},{\"id\":\"mock-ocr\"}]}"));
        server.createContext("/stats", ex -> reply(ex, 200, "{\"posts\":" + POSTS.get()
                + ",\"refused\":" + REFUSED.get() + ",\"embeddedInputs\":" + EMBEDDED_INPUTS.get()
                + ",\"ocrCalls\":" + OCR_CALLS.get() + "}"));
        server.createContext("/v1/embeddings", ex -> {
            String body = read(ex);
            if (refuse(ex, refuseEveryOther)) {
                return;
            }
            int n = countInputs(body);
            EMBEDDED_INPUTS.addAndGet(n);
            StringBuilder sb = new StringBuilder("{\"data\":[");
            for (int i = 0; i < n; i++) {
                // unit vector, position-dependent so vectors differ by input
                double a = (i + 1) * 0.5;
                sb.append(i > 0 ? "," : "").append("{\"index\":").append(i)
                        .append(",\"embedding\":[").append(Math.cos(a)).append(',')
                        .append(Math.sin(a)).append(",0.0,0.0]}");
            }
            reply(ex, 200, sb.append("],\"model\":\"mock-embed\"}").toString());
        });
        server.createContext("/v1/chat/completions", ex -> {
            read(ex);
            if (refuse(ex, refuseEveryOther)) {
                return;
            }
            OCR_CALLS.incrementAndGet();
            reply(ex, 200, "{\"model\":\"mock-ocr\",\"choices\":[{\"index\":0,\"message\":{\"role\":"
                    + "\"assistant\",\"content\":" + json(OCR_MARKDOWN) + "}}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}");
        });
        server.start();
        System.out.println("mock inference server on port " + server.getAddress().getPort()
                + (flaky ? " (flaky: every other POST is refused with 429 first)" : ""));
    }

    /** With refusal on, odd-numbered POSTs get a 429 the way a saturated engine answers. */
    private static boolean refuse(HttpExchange ex, boolean refuseEveryOther) throws IOException {
        int n = POSTS.incrementAndGet();
        if (refuseEveryOther && n % 2 == 1) {
            REFUSED.incrementAndGet();
            reply(ex, 429, "{\"detail\":\"Concurrency limit exceeded: 2/2 concurrent requests\"}");
            return true;
        }
        return false;
    }

    /** Counts the items of the top-level "input" array without a JSON parser: strings or objects. */
    static int countInputs(String body) {
        int at = body.indexOf("\"input\"");
        if (at < 0) {
            return 0;
        }
        int start = body.indexOf('[', at);
        if (start < 0) {
            return 1;
        }
        int depth = 0;
        int items = 0;
        boolean inString = false;
        boolean sawValue = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                sawValue = true;
            } else if (c == '[' || c == '{') {
                depth++;
                if (depth == 2) {
                    sawValue = true;
                }
            } else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return items + (sawValue ? 1 : 0);
                }
            } else if (c == ',' && depth == 1) {
                items++;
                sawValue = false;
            } else if (depth == 1 && !Character.isWhitespace(c)) {
                sawValue = true;
            }
        }
        return items;
    }

    private static String read(HttpExchange ex) throws IOException {
        try (var in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String json(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
