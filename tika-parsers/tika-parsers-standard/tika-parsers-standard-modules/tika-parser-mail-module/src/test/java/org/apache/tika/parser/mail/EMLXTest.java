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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Apple Mail emlx: a byte-count line, the RFC822 message, then an XML plist.
 */
public class EMLXTest extends TikaTest {

    private static final String MULTIPART = "testEMLX_multipart_html.emlx";
    private static final String MULTIPART_TEXT =
            "The plan for the Foundation is in this message’s HTML part.";
    private static final String SINGLE_PART = "testEMLX_single_part_html.emlx";
    private static final String SINGLE_PART_TEXT = "The trial balance is ready for review.";

    private static final String PLIST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\">\n<dict>\n\t<key>date-received</key>\n" +
            "\t<integer>1698393810</integer>\n</dict>\n</plist>\n";

    /** The three TikaInputStream backings; the count check seeks differently in each. */
    enum Source { BYTES, STREAM, FILE }

    @TempDir
    Path tmp;

    @ParameterizedTest
    @EnumSource(Source.class)
    public void testLongHeaderBlockWithHtmlAlternative(Source source) throws Exception {
        XMLResult r = parse(fixture(MULTIPART), source);
        assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Set the Foundation Straight", r.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("hseldon@example.com", r.metadata.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("2015-09-26T16:30:20Z", r.metadata.get(TikaCoreProperties.CREATED));
        assertContains(MULTIPART_TEXT, r.xml);
        assertPlistAndCountDropped(r.xml, "2803");
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    public void testSinglePartHtml(Source source) throws Exception {
        XMLResult r = parse(fixture(SINGLE_PART), source);
        assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Trial balance", r.metadata.get(TikaCoreProperties.TITLE));
        assertContains(SINGLE_PART_TEXT, r.xml);
        assertPlistAndCountDropped(r.xml, "446");
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    public void testStaleCountIsNotTrusted(Source source) throws Exception {
        // an edited file whose count no longer lands on the plist must not be cut
        for (String fixture : new String[]{MULTIPART, SINGLE_PART}) {
            byte[] good = fixture(fixture);
            String text = fixture.equals(MULTIPART) ? MULTIPART_TEXT : SINGLE_PART_TEXT;
            for (long bad : new long[]{0, 100, 1500, 99999}) {
                XMLResult r = parse(withCount(good, bad), source);
                assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE),
                        fixture + " count=" + bad);
                assertContains(text, r.xml);
                assertNotContained(paddedCount(bad), body(r.xml));
            }
        }
    }

    @Test
    public void testUnpaddedByteCount() throws Exception {
        String message = "Subject: Unpadded\nFrom: a@example.com\nTo: b@example.com\n\n" +
                "the body\n";
        byte[] emlx = (message.length() + "\n" + message + PLIST).getBytes(US_ASCII);
        XMLResult r = parse(emlx, Source.BYTES);
        assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Unpadded", r.metadata.get(TikaCoreProperties.TITLE));
        assertContains("the body", r.xml);
        assertPlistAndCountDropped(r.xml, Integer.toString(message.length()));
    }

    @Test
    public void testForcedTypeStillStripsFraming() throws Exception {
        // an emlx handed to the parser as message/rfc822 must not leak the plist either
        String message = "Subject: Forced\nFrom: a@example.com\n\nthe body\n";
        byte[] emlx = ((message.length() + "      \n") + message + PLIST).getBytes(US_ASCII);
        XMLResult r = parseAsRfc822(emlx);
        assertEquals("Forced", r.metadata.get(TikaCoreProperties.TITLE));
        assertContains("the body", r.xml);
        assertPlistAndCountDropped(r.xml, Integer.toString(message.length()));
    }

    @Test
    public void testCrlfIsNotFraming() throws Exception {
        // a CRLF-converted file has a stale count and a "\r\n" count line: left alone
        byte[] crlf = new String(fixture(SINGLE_PART), US_ASCII).replace("\n", "\r\n")
                .getBytes(US_ASCII);
        XMLResult r = parseAsRfc822(crlf);
        assertEquals("Trial balance", r.metadata.get(TikaCoreProperties.TITLE));
        assertContains(SINGLE_PART_TEXT, r.xml);
    }

    private XMLResult parse(byte[] emlx, Source source) throws Exception {
        try (TikaInputStream tis = open(emlx, source)) {
            return getXML(tis, new AutoDetectParser(), new Metadata());
        }
    }

    private XMLResult parseAsRfc822(byte[] bytes) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "message/rfc822");
        ParseContext context = new ParseContext();
        context.set(Parser.class, new AutoDetectParser());
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            return getXML(tis, new RFC822Parser(), metadata, context);
        }
    }

    private TikaInputStream open(byte[] bytes, Source source) throws IOException {
        switch (source) {
            case BYTES:
                return TikaInputStream.get(bytes);
            case STREAM:
                return TikaInputStream.get(new ByteArrayInputStream(bytes));
            default:
                Path p = Files.createTempFile(tmp, "emlx", ".emlx");
                Files.write(p, bytes);
                return TikaInputStream.get(p);
        }
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = EMLXTest.class.getResourceAsStream("/test-documents/" + name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }

    /** Replaces the fixture's 10-column count line. */
    private static byte[] withCount(byte[] emlx, long count) {
        byte[] line = (paddedCount(count) + "\n").getBytes(US_ASCII);
        byte[] out = new byte[emlx.length];
        System.arraycopy(line, 0, out, 0, 11);
        System.arraycopy(emlx, 11, out, 11, emlx.length - 11);
        return out;
    }

    private static String paddedCount(long count) {
        StringBuilder sb = new StringBuilder(Long.toString(count));
        while (sb.length() < 10) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String body(String xml) {
        return xml.substring(xml.indexOf("<body"));
    }

    private static void assertPlistAndCountDropped(String xml, String byteCount) {
        String body = body(xml);
        assertNotContained(byteCount, body);
        assertNotContained("date-received", body);
        assertNotContained("plist", body);
    }
}
