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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaGUIHtmlViewTest {

    private static final String HTML = "<html><head><title>t</title>"
            + "<link rel=\"stylesheet\" href=\"http://attacker.invalid/head.css\"></head><body>"
            + "<p>before</p>"
            + "<link rel=\"stylesheet\" href=\"http://attacker.invalid/body.css\">"
            + "<object classid=\"javax.swing.JLabel\"><param name=\"text\" value=\"evil\"></object>"
            + "<img src=\"http://attacker.invalid/remote.png\" alt=\"remote\">"
            + "<img src=\"embedded:image1.png\" alt=\"local\">"
            + "<p>after</p></body></html>";

    // every construct here makes Swing's html view request BASE or build a Component
    private static final String ACTIVE = "<html><head><link rel=\"stylesheet\" href=\"BASE/link.css\"></head>"
            + "<body background=\"BASE/body-bg\"><p style=\"background-image:url(BASE/style)\">styled</p>"
            + "<table background=\"BASE/table-bg\"><tr><td background=\"BASE/td-bg\">cell</td></tr></table>"
            + "<input type=\"image\" src=\"BASE/input\">"
            + "<img src=\"BASE/img.png\" alt=\"remote\">"
            + "<object classid=\"javax.swing.JLabel\"><param name=\"text\" value=\"evil\"></object>"
            + "<frameset rows=\"50%,50%\"><frame src=\"BASE/frame\"></frameset>"
            + "<p>visible</p></body></html>";

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
            deleteSavedImages(html);
        }
    }

    @Test
    public void testHtmlViewMatchesSerializedName() throws Exception {
        StringWriter view = new StringWriter();
        ContentHandler handler = TikaGUI.getHtmlHandler(view, new TikaGUI.ImageSavingParser(null));
        AttributesImpl objectAtts = new AttributesImpl();
        objectAtts.addAttribute("", "", "classid", "CDATA", "javax.swing.JLabel");
        AttributesImpl imgAtts = new AttributesImpl();
        imgAtts.addAttribute("", "", "SRC", "CDATA", "http://attacker.invalid/remote.png");
        handler.startDocument();
        handler.startElement(XHTMLContentHandler.XHTML, "body", "body", new AttributesImpl());
        handler.startElement("", "", "OBJECT", objectAtts);
        handler.endElement("", "", "OBJECT");
        handler.startElement("", "", "IMG", imgAtts);
        handler.endElement("", "", "IMG");
        handler.processingInstruction("x", "a><object classid=\"javax.swing.JLabel\"></object><x ");
        char[] text = "<object classid=\"javax.swing.JLabel\">".toCharArray();
        handler.characters(text, 0, text.length);
        handler.endElement(XHTMLContentHandler.XHTML, "body", "body");
        handler.endDocument();
        String html = view.toString();
        assertFalse(html.toLowerCase(Locale.ROOT).contains("<object"), html);
        assertTrue(html.contains("&lt;object"), html);
        assertFalse(html.contains("attacker.invalid"), html);
        assertTrue(html.contains("<IMG"), html);
    }

    @Test
    public void testSwingRendersNothingActive() throws Exception {
        // calibration: the unfiltered markup does reach the listener and build a Component
        try (Listener raw = new Listener()) {
            JEditorPane pane = render(ACTIVE.replace("BASE", raw.base()));
            raw.awaitAtLeast(5);
            assertTrue(pane.getComponentCount() > 0);
        }

        // IdentityHtmlMapper passes through whatever the document has, as a custom parser could
        ParseContext context = new ParseContext();
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
        try (Listener filtered = new Listener()) {
            StringWriter view = new StringWriter();
            try (TikaInputStream tis = TikaInputStream.get(ACTIVE.replace("BASE", filtered.base()).getBytes(UTF_8))) {
                new AutoDetectParser().parse(tis, TikaGUI.getHtmlHandler(view, new TikaGUI.ImageSavingParser(null)),
                        new Metadata(), context);
            }
            String html = view.toString();
            assertTrue(html.contains("visible"), html);
            JEditorPane pane = render(html);
            Thread.sleep(1000);
            assertEquals(List.of(), filtered.requests, html);
            assertEquals(0, pane.getComponentCount(), html);
        }
    }

    private static JEditorPane render(String html) throws Exception {
        JEditorPane[] pane = new JEditorPane[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new JEditorPane();
            pane[0].setContentType("text/html");
            pane[0].setText(html);
            pane[0].setSize(800, 600);
            pane[0].getPreferredSize();
        });
        return pane[0];
    }

    private static void deleteSavedImages(String html) throws Exception {
        Matcher m = Pattern.compile("src=\"(file:[^\"]+)\"").matcher(html);
        while (m.find()) {
            Files.deleteIfExists(Path.of(URI.create(m.group(1))));
        }
    }

    private static class Listener implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final List<String> requests = new CopyOnWriteArrayList<>();

        Listener() throws Exception {
            Thread t = new Thread(() -> {
                while (true) {
                    try (Socket s = server.accept()) {
                        requests.add(new BufferedReader(new InputStreamReader(s.getInputStream(), UTF_8)).readLine());
                        s.getOutputStream().write("HTTP/1.0 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(UTF_8));
                    } catch (SocketException e) {
                        return;
                    } catch (Exception e) {
                        //swallow
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        String base() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void awaitAtLeast(int n) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10_000;
            while (requests.size() < n && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(requests.size() >= n, requests.toString());
        }

        @Override
        public void close() throws Exception {
            server.close();
        }
    }
}
