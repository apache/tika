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
package org.apache.tika.parser.image;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * WebP stores XMP in a RIFF {@code "XMP "} chunk. Tika parses it with the shared
 * {@link org.apache.tika.parser.xmp.XmpExtractor}; regression guard for TIKA where the
 * WebP path dropped XMP entirely (no in-repo WebP carries an XMP packet).
 */
public class WebPXmpTest {

    private static final String XMP =
            "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
            + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">WebP XMP Title</rdf:li>"
            + "</rdf:Alt></dc:title>"
            + "<dc:creator><rdf:Seq><rdf:li>Jane Photographer</rdf:li></rdf:Seq></dc:creator>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

    private static void chunk(ByteArrayOutputStream riff, String fourCC, byte[] data) {
        riff.writeBytes(fourCC.getBytes(US_ASCII));
        riff.writeBytes(le(data.length));
        riff.writeBytes(data);
        if ((data.length & 1) == 1) {
            riff.write(0);   // RIFF pads chunks to even length
        }
    }

    private static byte[] le(int v) {
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static byte[] webpWithXmp() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(US_ASCII));
        // VP8X extended-format header with the XMP flag (0x04) set
        chunk(body, "VP8X", new byte[]{0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        chunk(body, "VP8 ", new byte[16]);   // dummy bitstream; content is irrelevant to metadata
        chunk(body, "XMP ", XMP.getBytes(UTF_8));
        ByteArrayOutputStream riff = new ByteArrayOutputStream();
        riff.writeBytes("RIFF".getBytes(US_ASCII));
        riff.writeBytes(le(body.size()));
        riff.writeBytes(body.toByteArray());
        return riff.toByteArray();
    }

    @Test
    public void testWebpXmpIsExtracted(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("xmp.webp");
        Files.write(file, webpWithXmp());

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/webp");
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new WebPParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("WebP XMP Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("WebP XMP Title", metadata.get("dc:title"));
        assertEquals("Jane Photographer", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Jane Photographer", metadata.get("dc:creator"));
    }

    /** A malformed XMP packet is recorded, not thrown: the rest of the parse still succeeds. */
    @Test
    public void testMalformedXmpIsNonFatal(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write("WEBP".getBytes(US_ASCII));
        chunk(body, "VP8X", new byte[]{0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        chunk(body, "VP8 ", new byte[16]);
        chunk(body, "XMP ", "<x:xmpmeta><rdf:RDF><dc:title>unclosed".getBytes(UTF_8));
        ByteArrayOutputStream riff = new ByteArrayOutputStream();
        riff.write("RIFF".getBytes(US_ASCII));
        riff.writeBytes(le(body.size()));
        riff.writeBytes(body.toByteArray());
        Path file = tmp.resolve("bad.webp");
        Files.write(file, riff.toByteArray());

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/webp");
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            // must not throw; the XMP failure is recorded on the metadata instead
            new WebPParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertNull(metadata.get(TikaCoreProperties.TITLE));
        assertNotNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }
}
