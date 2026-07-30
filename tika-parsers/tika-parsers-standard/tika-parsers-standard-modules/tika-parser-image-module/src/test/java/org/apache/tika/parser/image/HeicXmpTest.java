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
 * HEIF/HEIC XMP was always dropped — metadata-extractor never read it. Stored as an
 * {@code application/rdf+xml} item but usually {@code <?xpacket?>}-wrapped, so the shared packet
 * scanner catches it (same path as TIFF/PSD/JXL). Fixture is a hand-built HEIC, no corpus binary.
 */
public class HeicXmpTest {

    private static final String XMP =
            "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
            + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">HEIC XMP Title</rdf:li>"
            + "</rdf:Alt></dc:title>"
            + "<dc:creator><rdf:Seq><rdf:li>Jane Photographer</rdf:li></rdf:Seq></dc:creator>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

    /** Same content but with NO xpacket wrapper -> the scanner misses it, the item locator wins. */
    private static final String BARE_XMP =
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
            + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">Bare HEIC Title</rdf:li>"
            + "</rdf:Alt></dc:title>"
            + "<dc:creator><rdf:Seq><rdf:li>Jane Photographer</rdf:li></rdf:Seq></dc:creator>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    private static byte[] u32(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static byte[] u16(int v) {
        return new byte[]{(byte) (v >>> 8), (byte) v};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            b.writeBytes(p);
        }
        return b.toByteArray();
    }

    private static byte[] box(String type, byte[] payload) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(u32(8 + payload.length));
        b.writeBytes(type.getBytes(US_ASCII));
        b.writeBytes(payload);
        return b.toByteArray();
    }

    private static byte[] fullBox(String type, byte[] payload) {
        return fullBox(type, 0, payload);
    }

    private static byte[] fullBox(String type, int version, byte[] payload) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(version);
        b.writeBytes(new byte[3]);   // flags
        b.writeBytes(payload);
        return box(type, b.toByteArray());
    }

    /** ftyp(heic) + minimal meta(hdlr=pict) + mdat holding the XMP packet. */
    private static byte[] heicWithXmp() {
        ByteArrayOutputStream ftypPayload = new ByteArrayOutputStream();
        ftypPayload.writeBytes("heic".getBytes(US_ASCII));   // major brand
        ftypPayload.writeBytes(u32(0));                       // minor version
        ftypPayload.writeBytes("mif1".getBytes(US_ASCII));    // compatible brands
        ftypPayload.writeBytes("heic".getBytes(US_ASCII));

        ByteArrayOutputStream hdlrPayload = new ByteArrayOutputStream();
        hdlrPayload.writeBytes(u32(0));                        // pre_defined
        hdlrPayload.writeBytes("pict".getBytes(US_ASCII));    // handler_type
        hdlrPayload.writeBytes(new byte[13]);                 // reserved[3] + empty name

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(box("ftyp", ftypPayload.toByteArray()));
        out.writeBytes(fullBox("meta", fullBox("hdlr", hdlrPayload.toByteArray())));
        out.writeBytes(box("mdat", XMP.getBytes(UTF_8)));
        return out.toByteArray();
    }

    /**
     * ftyp(heic) + meta(hdlr, iinf declaring a mime/application/rdf+xml item, iloc pointing at
     * mdat) + mdat holding a bare (non-xpacket) XMP packet. Exercises the iinf/iloc item locator.
     */
    private static byte[] bareItemHeic() {
        byte[] xmp = BARE_XMP.getBytes(UTF_8);
        byte[] ftyp = box("ftyp", concat("heic".getBytes(US_ASCII), u32(0),
                "mif1".getBytes(US_ASCII), "heic".getBytes(US_ASCII)));
        byte[] hdlr = fullBox("hdlr", concat(u32(0), "pict".getBytes(US_ASCII), new byte[13]));
        // infe v2: item_ID=1, protection=0, type=mime, empty name, content_type + null terminator
        byte[] infe = fullBox("infe", 2, concat(u16(1), u16(0), "mime".getBytes(US_ASCII),
                new byte[]{0}, "application/rdf+xml".getBytes(US_ASCII), new byte[]{0}));
        byte[] iinf = fullBox("iinf", concat(u16(1), infe));

        // The extent offset is a fixed-width field, so meta's size does not depend on its value:
        // build once with a placeholder to learn meta's length, then again with the real offset.
        int metaLen = fullBox("meta", concat(hdlr, iinf, iloc(0, xmp.length))).length;
        int xmpOffset = ftyp.length + metaLen + 8;   // + mdat header
        byte[] meta = fullBox("meta", concat(hdlr, iinf, iloc(xmpOffset, xmp.length)));
        return concat(ftyp, meta, box("mdat", xmp));
    }

    /** iloc v0: offset/length size 4, no base offset; one item, one extent. */
    private static byte[] iloc(int offset, int length) {
        return fullBox("iloc", concat(
                new byte[]{0x44, 0x00},   // offsetSize=4, lengthSize=4; baseOffsetSize=0
                u16(1),                   // item_count
                u16(1), u16(0), u16(1),   // item_ID, data_reference_index, extent_count
                u32(offset), u32(length)));
    }

    @Test
    public void testHeicXmpIsExtracted(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("xmp.heic");
        Files.write(file, heicWithXmp());

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/heic");
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new HeifParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("HEIC XMP Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("HEIC XMP Title", metadata.get("dc:title"));
        assertEquals("Jane Photographer", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Jane Photographer", metadata.get("dc:creator"));
    }

    /** XMP with no xpacket wrapper is found by locating the rdf+xml item via meta/iinf/iloc. */
    @Test
    public void testBareRdfItemXmpIsExtracted(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("bare.heic");
        Files.write(file, bareItemHeic());

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/heic");
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new HeifParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("Bare HEIC Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Bare HEIC Title", metadata.get("dc:title"));
        assertEquals("Jane Photographer", metadata.get(TikaCoreProperties.CREATOR));
    }
}
