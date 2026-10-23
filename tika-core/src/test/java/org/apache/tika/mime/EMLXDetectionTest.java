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
package org.apache.tika.mime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class EMLXDetectionTest {

    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

    private static final String HEADERS = "From: a@example.com\nTo: b@example.com\n" +
            "Subject: hi\nDate: Sat, 26 Sep 2015 09:30:20 -0700\n\nbody\n";

    @Test
    public void testByteCountLine() throws Exception {
        // Apple Mail pads the count to 10 columns; an unpadded count is accepted too
        assertMime("message/x-emlx", "1795      \n" + HEADERS);
        assertMime("message/x-emlx", "1795\n" + HEADERS);
        assertMime("message/x-emlx", "1\n" + HEADERS);
        assertMime("message/x-emlx", "1234567890\n" + HEADERS);

        // not a byte count
        assertMime("message/rfc822", HEADERS);
        assertMime("text/plain", "Hello\n" + HEADERS);
        assertMime("text/plain", "17 95\n" + HEADERS);
        assertMime("text/plain", "1795 \n\n" + HEADERS);
        assertMime("text/plain", "12345678901\n" + HEADERS);
    }

    @Test
    public void testFirstHeader() throws Exception {
        for (String header : new String[]{"Delivered-To: b@example.com", "Return-Path: <a@example.com>",
                "Received: from mx.example.com", "X-Original-To: b@example.com",
                "DKIM-Signature: v=1; a=rsa-sha256", "ARC-Seal: i=1; a=rsa-sha256",
                "Authentication-Results: mx.example.com; spf=pass",
                "Received-SPF: pass (mx.example.com: ...)", "Subject: hi",
                "Mime-Version: 1.0 (Mac OS X Mail 13.4 \\(3608.120.23.2.7\\))",
                "Content-Type: text/html;", "Message-Id: <1@example.com>"}) {
            assertMime("message/x-emlx", "701       \n" + header + "\n" + HEADERS);
        }
        assertMime("text/plain", "701       \nNot a header\n" + HEADERS);
        assertMime("text/plain", "701       \n\n" + HEADERS);
    }

    @Test
    public void testHtmlPartDoesNotWin() throws Exception {
        // a long header block pushes Message-ID past the rfc822 window while
        // an html part sits inside the html window
        StringBuilder sb = new StringBuilder("4569      \nDelivered-To: b@example.com\n");
        for (int i = 0; i < 12; i++) {
            sb.append("Received: from relay").append(i)
                    .append(".example.com by mx.example.com with ESMTP id abc").append(i)
                    .append("; Sat, 26 Sep 2015 13:02:49 -0700 (PDT)\n");
        }
        sb.append(HEADERS.replace("\nbody\n", "\n<html><body>body</body></html>\n"));
        assertMime("message/x-emlx", sb.toString());
    }

    @Test
    public void testHttpCaptureIsNotEmlx() throws Exception {
        // an LWP-style capture: a status line without the version, then HTTP headers
        String capture = "200 OK\nDate: Thu, 08 Aug 2002 10:44:35 GMT\n"
                + "Server: Apache Tomcat/4.0.1 (HTTP/1.1 Connector)\nContent-Type: image/svg+xml\n"
                + "Client-Date: Thu, 08 Aug 2002 10:39:29 GMT\n\n<svg/>\n";
        assertMime("application/x-httpresponse", capture);
        // the .emlx name must not turn it into mail either
        Metadata named = new Metadata();
        named.set(TikaCoreProperties.RESOURCE_NAME_KEY, "capture.emlx");
        try (TikaInputStream tis = TikaInputStream.get(capture.getBytes(StandardCharsets.UTF_8))) {
            assertEquals("application/x-httpresponse",
                    MIME_TYPES.detect(tis, named, new ParseContext()).toString());
        }
        assertMime("application/x-httpresponse", "HTTP/1.1 404 Not Found\r\nServer: nginx\r\n\r\n");
    }

    @Test
    public void testNameDoesNotMakeProseAMessage() throws Exception {
        // the magic looked and said no; the extension cannot overrule it for a message type
        String prose = "The quick brown fox jumps over the lazy dog.\n";
        for (String name : new String[]{"fox.emlx", "fox.mbox", "fox.eml"}) {
            Metadata named = new Metadata();
            named.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            try (TikaInputStream tis = TikaInputStream.get(prose.getBytes(StandardCharsets.UTF_8))) {
                assertEquals("text/plain", MIME_TYPES.detect(tis, named, new ParseContext()).toString(), name);
            }
        }
        // a type without magic still takes its name
        Metadata java = new Metadata();
        java.set(TikaCoreProperties.RESOURCE_NAME_KEY, "Fox.java");
        try (TikaInputStream tis = TikaInputStream.get(prose.getBytes(StandardCharsets.UTF_8))) {
            assertEquals("text/x-java-source", MIME_TYPES.detect(tis, java, new ParseContext()).toString());
        }
    }

    private void assertMime(String expected, String txt) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(txt.getBytes(StandardCharsets.UTF_8))) {
            MediaType mediaType = MIME_TYPES.detect(tis, new Metadata(), new ParseContext());
            assertEquals(expected, mediaType.toString(), txt);
        }
    }
}
