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
package org.apache.tika.parser.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.JsoupFilter;

public class InlineHtmlNotFilteredTest extends TikaTest {

    private static class Recording implements JsoupFilter {
        final List<String> names = new CopyOnWriteArrayList<>();

        @Override
        public Document filter(Document document, Metadata metadata, ParseContext context) {
            names.add(String.valueOf(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY)));
            return document;
        }
    }

    @Test
    public void testBodyIsNotFilteredButHtmlAttachmentIs() throws Exception {
        String eml = "From: a@example.com\r\nTo: b@example.com\r\nSubject: s\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"B\"\r\n\r\n"
                + "--B\r\nContent-Type: text/html\r\n\r\n<html><body><p>the message body</p></body></html>\r\n"
                + "--B\r\nContent-Type: text/html\r\nContent-Disposition: attachment; filename=\"page.html\"\r\n\r\n"
                + "<html><body><p>an attached page</p></body></html>\r\n"
                + "--B--\r\n";
        Recording filter = new Recording();
        ParseContext context = new ParseContext();
        context.set(JsoupFilter.class, filter);
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(eml.getBytes(StandardCharsets.US_ASCII))) {
            list = getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(), context, false);
        }
        assertContains("the message body", list.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(List.of("page.html"), filter.names);
    }
}
