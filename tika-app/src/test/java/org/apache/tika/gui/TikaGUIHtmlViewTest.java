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
package org.apache.tika.gui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;

public class TikaGUIHtmlViewTest {

    private static final String HTML = "<html><head><title>t</title>"
            + "<link rel=\"stylesheet\" href=\"http://attacker.invalid/head.css\"></head><body>"
            + "<p>before</p>"
            + "<link rel=\"stylesheet\" href=\"http://attacker.invalid/body.css\">"
            + "<object classid=\"javax.swing.JLabel\"><param name=\"text\" value=\"evil\"></object>"
            + "<img src=\"http://attacker.invalid/remote.png\" alt=\"remote\">"
            + "<img src=\"embedded:image1.png\" alt=\"local\">"
            + "<p>after</p></body></html>";

    @Test
    public void testHtmlViewDropsActiveContent() throws Exception {
        StringWriter view = new StringWriter();
        ToXMLContentHandler xhtml = new ToXMLContentHandler();
        TeeContentHandler handler = new TeeContentHandler(
                TikaGUI.getHtmlHandler(view, new TikaGUI.ImageSavingParser(null)), xhtml);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(HTML.getBytes(UTF_8))) {
            new AutoDetectParser().parse(tis, handler, metadata, new ParseContext());
        }
        String html = view.toString();
        try {
            assertTrue(html.contains("before") && html.contains("after"), html);
            assertFalse(html.contains("<object"), html);
            assertFalse(html.contains("classid"), html);
            assertFalse(html.contains("<param"), html);
            assertFalse(html.contains("<link"), html);
            assertFalse(html.contains("attacker.invalid"), html);
            assertTrue(html.contains("alt=\"remote\""), html);
            assertTrue(html.contains("src=\"file:"), html);

            // the tee'd handlers still see the parser's own attributes
            String xml = xhtml.toString();
            assertTrue(xml.contains("src=\"http://attacker.invalid/remote.png\""), xml);
            assertTrue(xml.contains("src=\"embedded:image1.png\""), xml);
        } finally {
            Matcher m = Pattern.compile("src=\"(file:[^\"]+)\"").matcher(html);
            while (m.find()) {
                Files.deleteIfExists(Path.of(java.net.URI.create(m.group(1))));
            }
        }
    }
}
