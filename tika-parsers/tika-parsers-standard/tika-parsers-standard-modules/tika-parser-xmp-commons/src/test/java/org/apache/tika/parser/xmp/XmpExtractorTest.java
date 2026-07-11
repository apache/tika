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
package org.apache.tika.parser.xmp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDC;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.metadata.XMPTIFF;

public class XmpExtractorTest {

    private static final String PACKET =
            "<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='Test 1.0'>"
          + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'"
          + "         xmlns:xmp='http://ns.adobe.com/xap/1.0/'"
          + "         xmlns:xmpMM='http://ns.adobe.com/xap/1.0/mm/'"
          + "         xmlns:stRef='http://ns.adobe.com/xap/1.0/sType/ResourceRef#'"
          + "         xmlns:stEvt='http://ns.adobe.com/xap/1.0/sType/ResourceEvent#'"
          + "         xmlns:tiff='http://ns.adobe.com/tiff/1.0/'"
          + "         xmlns:crs='http://ns.adobe.com/camera-raw-settings/1.0/'"
          + "         xmlns:dc='http://purl.org/dc/elements/1.1/'>"
          + "<rdf:Description rdf:about='' tiff:Make='Canon' crs:Contrast='25'>"
          + "  <xmp:CreateDate>2020-01-02T03:04:05Z</xmp:CreateDate>"
          + "  <dc:creator><rdf:Seq><rdf:li>Alice</rdf:li><rdf:li>Bob</rdf:li></rdf:Seq></dc:creator>"
          + "  <dc:title><rdf:Alt><rdf:li xml:lang='x-default'>Hello</rdf:li></rdf:Alt></dc:title>"
          + "  <xmpMM:DerivedFrom rdf:parseType='Resource'>"
          + "    <stRef:documentID>docid-123</stRef:documentID>"
          + "    <stRef:originalDocumentID>orig-999</stRef:originalDocumentID>"
          + "    <stRef:renditionClass>proof:pdf</stRef:renditionClass>"
          + "  </xmpMM:DerivedFrom>"
          + "  <xmpMM:History><rdf:Seq>"
          + "    <rdf:li rdf:parseType='Resource'><stEvt:action>created</stEvt:action>"
          + "      <stEvt:changed>/metadata</stEvt:changed>"
          + "      <stEvt:parameters>from application/pdf</stEvt:parameters></rdf:li>"
          + "    <rdf:li rdf:parseType='Resource'><stEvt:action>saved</stEvt:action></rdf:li>"
          + "  </rdf:Seq></xmpMM:History>"
          + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    private Metadata metadata;

    @BeforeEach
    public void setUp() throws Exception {
        metadata = new Metadata();
        new XmpExtractor().extract(PACKET.getBytes(UTF_8), metadata);
    }

    @Test
    public void testScalarsAndDates() {
        assertEquals("Hello", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("2020-01-02T03:04:05Z", metadata.get(XMP.CREATE_DATE));
        // xmp:CreateDate also fills the canonical created date when nothing else set it
        assertEquals("2020-01-02T03:04:05Z", metadata.get(TikaCoreProperties.CREATED));
    }

    /** Canonical created is filled set-if-absent: a doc date (docinfo/EXIF) already present wins. */
    @Test
    public void testCanonicalCreatedNotOverwritten() throws Exception {
        Metadata md = new Metadata();
        md.set(TikaCoreProperties.CREATED, "1999-12-31T00:00:00Z");   // e.g. docinfo/EXIF got here first
        new XmpExtractor().extract(PACKET.getBytes(UTF_8), md);
        assertEquals("1999-12-31T00:00:00Z", md.get(TikaCoreProperties.CREATED));   // unchanged
        assertEquals("2020-01-02T03:04:05Z", md.get(XMP.CREATE_DATE));              // xmp key still set
    }

    @Test
    public void testMultiValued() {
        assertArrayEquals(new String[]{"Alice", "Bob"}, metadata.getValues(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testStructs() {
        assertEquals("docid-123", metadata.get(XMPMM.DERIVED_FROM_DOCUMENTID));
        assertArrayEquals(new String[]{"created", "saved"}, metadata.getValues(XMPMM.HISTORY_ACTION));
    }

    /** History/DerivedFrom subfields beyond action/when/documentID map to dedicated properties. */
    @Test
    public void testHistoryAndDerivedFromSubfields() {
        assertEquals("/metadata", metadata.get(XMPMM.HISTORY_CHANGED));
        assertEquals("from application/pdf", metadata.get(XMPMM.HISTORY_PARAMETERS));
        assertEquals("orig-999", metadata.get(XMPMM.DERIVED_FROM_ORIGINAL_DOCUMENTID));
        assertEquals("proof:pdf", metadata.get(XMPMM.DERIVED_FROM_RENDITION_CLASS));
    }

    @Test
    public void testUnmappedPassthrough() {
        assertEquals("25", metadata.get("xmp-raw:crs:Contrast"));   // still-unmapped -> namespaced raw
        assertEquals("Canon", metadata.get(TIFF.EQUIPMENT_MAKE));   // tiff:Make is now promoted
    }

    /** Group-1 + Group-2(clean) + aux promotions: top raw keys map to first-class properties. */
    @Test
    public void testPromotedKeys() throws Exception {
        String packet = "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
                + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
                + "<rdf:Description rdf:about=''"
                + "   xmlns:photoshop='http://ns.adobe.com/photoshop/1.0/'"
                + "   xmlns:xmpTPg='http://ns.adobe.com/xap/1.0/t/pg/'"
                + "   xmlns:pdf='http://ns.adobe.com/pdf/1.3/'"
                + "   xmlns:tiff='http://ns.adobe.com/tiff/1.0/'"
                + "   xmlns:exif='http://ns.adobe.com/exif/1.0/'"
                + "   xmlns:aux='http://ns.adobe.com/exif/1.0/aux/'"
                + "   photoshop:ColorMode='3'"
                + "   photoshop:ICCProfile='sRGB IEC61966-2.1'"
                + "   photoshop:DateCreated='2011-08-05T10:48:16-05:00'"
                + "   xmpTPg:NPages='7'"
                + "   pdf:Trapped='False'"
                + "   tiff:Make='Canon'"
                + "   tiff:Model='EOS 5D'"
                + "   tiff:Orientation='1'"
                + "   exif:DateTimeOriginal='2005-04-06T10:34:58-04:00'"
                + "   exif:ISOSpeedRatings='64'"
                + "   exif:PixelXDimension='600'"
                + "   exif:PixelYDimension='734'"
                + "   aux:SerialNumber='142026001685'"
                + "   aux:Lens='iPhone 5s back camera 4.12mm f/2.2'/>"
                + "</rdf:RDF></x:xmpmeta>";
        Metadata md = new Metadata();
        new XmpExtractor().extract(packet.getBytes(UTF_8), md);

        // group 1
        assertEquals("3", md.get(Photoshop.COLOR_MODE));
        assertEquals("sRGB IEC61966-2.1", md.get(Photoshop.ICC_PROFILE));
        assertEquals("7", md.get(PagedText.N_PAGES));
        assertEquals("False", md.get(PDF.TRAPPED));
        assertNotNull(md.get(Photoshop.DATE_CREATED));                 // date-typed, normalized
        assertNotNull(md.get(TikaCoreProperties.CREATED));             // DateCreated fills created
        // group 2 (clean exif/tiff) + aux -> the shared TIFF interface
        assertEquals("Canon", md.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("EOS 5D", md.get(TIFF.EQUIPMENT_MODEL));
        assertEquals("1", md.get(TIFF.ORIENTATION));
        assertNotNull(md.get(TIFF.ORIGINAL_DATE));                     // exif:DateTimeOriginal, normalized
        assertEquals("64", md.get(TIFF.ISO_SPEED_RATINGS));
        assertEquals("142026001685", md.get(TIFF.SERIAL_NUMBER));
        assertEquals("iPhone 5s back camera 4.12mm f/2.2", md.get(TIFF.LENS));
        // exif:Pixel*Dimension folds into the canonical IMAGE_WIDTH/LENGTH (+ xmp-marked variant)
        assertEquals("600", md.get(TIFF.IMAGE_WIDTH));
        assertEquals("734", md.get(TIFF.IMAGE_LENGTH));
        assertEquals("600", md.get(XMPTIFF.PIXEL_X_DIMENSION));
        // tiff:/exif: are double-keyed: the XMP-marked variant preserves provenance
        assertEquals("Canon", md.get(XMPTIFF.EQUIPMENT_MAKE));
        assertEquals("EOS 5D", md.get(XMPTIFF.EQUIPMENT_MODEL));
        assertNotNull(md.get(XMPTIFF.ORIGINAL_DATE));
        // aux: is single-key (no binary source) -> no xmp-marked variant
        assertNull(md.get("xmp:aux:Lens"));
        // no longer raw
        assertNull(md.get("xmp-raw:photoshop:ColorMode"));
        assertNull(md.get("xmp-raw:tiff:Make"));
        assertNull(md.get("xmp-raw:aux:Lens"));
    }

    /** Adobe-internal digests and the base64 thumbnail blob are dropped, not exposed even as raw. */
    @Test
    public void testJunkKeysDropped() throws Exception {
        String packet = "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
                + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
                + "<rdf:Description rdf:about=''"
                + "   xmlns:tiff='http://ns.adobe.com/tiff/1.0/'"
                + "   xmlns:exif='http://ns.adobe.com/exif/1.0/'"
                + "   xmlns:xmp='http://ns.adobe.com/xap/1.0/'"
                + "   xmlns:xmpGImg='http://ns.adobe.com/xap/1.0/g/img/'>"
                + "  <tiff:NativeDigest>256,257,258;A1B2C3</tiff:NativeDigest>"
                + "  <exif:NativeDigest>36864,40960;123456</exif:NativeDigest>"
                + "  <xmp:Thumbnails><rdf:Alt><rdf:li rdf:parseType='Resource'>"
                + "    <xmpGImg:image>/9j/4AAQSkZJRgABAgEBLAEsAAD</xmpGImg:image>"
                + "    <xmpGImg:width>256</xmpGImg:width>"
                + "    <xmpGImg:format>JPEG</xmpGImg:format>"
                + "  </rdf:li></rdf:Alt></xmp:Thumbnails>"
                + "</rdf:Description></rdf:RDF></x:xmpmeta>";
        Metadata md = new Metadata();
        new XmpExtractor().extract(packet.getBytes(UTF_8), md);

        boolean nativeDigest = false;
        boolean blob = false;
        boolean widthKept = false;
        for (String n : md.names()) {
            if (n.contains("NativeDigest")) {
                nativeDigest = true;
            }
            if (md.get(n).startsWith("/9j/")) {
                blob = true;
            }
            if (n.endsWith("xmpGImg:width")) {
                widthKept = true;
            }
        }
        assertFalse(nativeDigest, "tiff/exif:NativeDigest must be dropped");
        assertFalse(blob, "base64 thumbnail blob must be dropped");
        assertTrue(widthKept, "harmless thumbnail siblings are kept");
    }

    /** Non-ASCII UTF-8 values must survive the SAX flatten/extract path intact. */
    @Test
    public void testNonAsciiUtf8() throws Exception {
        String xmp =
                "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
              + "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
              + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'"
              + "         xmlns:dc='http://purl.org/dc/elements/1.1/'>"
              + "<rdf:Description rdf:about=''><dc:title><rdf:Alt>"
              + "<rdf:li xml:lang='x-default'>Tosteberga Ängar</rdf:li>"
              + "</rdf:Alt></dc:title></rdf:Description></rdf:RDF></x:xmpmeta>"
              + "<?xpacket end=\"w\"?>";
        Metadata md = new Metadata();
        new XmpExtractor().extract(xmp.getBytes(java.nio.charset.StandardCharsets.UTF_8), md);
        assertEquals("Tosteberga Ängar", md.get(TikaCoreProperties.TITLE));
    }

    /** Raw passthrough is namespaced (xmp-raw:) so untrusted XMP can never shadow a known Tika field. */
    @Test
    public void testPassthroughCannotShadowKnownFields() throws Exception {
        String evil =
                "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
              + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'"
              + "         xmlns:dc='http://evil.example/'"     // spoofed dc URI -> table miss -> raw
              + "         xmlns:ev='http://evil.example/2'>"
              + "<rdf:Description rdf:about=''>"
              + "  <dc:title>injected</dc:title>"
              + "  <ev:harmless>ok</ev:harmless>"
              + "</rdf:Description></rdf:RDF></x:xmpmeta>";
        Metadata md = new Metadata();
        new XmpExtractor().extract(evil.getBytes(UTF_8), md);
        assertNull(md.get(TikaCoreProperties.TITLE), "spoofed dc:title must not write the known field");
        assertNull(md.get("dc:title"));
        assertEquals("injected", md.get("xmp-raw:dc:title"));   // it lands under the raw namespace instead
        assertEquals("ok", md.get("xmp-raw:ev:harmless"));
    }

    /** Option A: dc values land in both the canonical and the xmp-namespaced property. */
    @Test
    public void testDoubleKeying() {
        assertEquals("Hello", metadata.get(XMPDC.TITLE));
        assertArrayEquals(new String[]{"Alice", "Bob"}, metadata.getValues(XMPDC.CREATOR));
    }

    /** Language-alt items are exposed under key:lang, for both canonical and xmp-namespaced keys. */
    @Test
    public void testLanguageVariants() throws Exception {
        String ml =
                "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
              + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'"
              + "         xmlns:dc='http://purl.org/dc/elements/1.1/'>"
              + "<rdf:Description rdf:about=''><dc:title><rdf:Alt>"
              + "  <rdf:li xml:lang='x-default'>Hello World</rdf:li>"
              + "  <rdf:li xml:lang='fr-ca'>Bonjour World</rdf:li>"
              + "</rdf:Alt></dc:title></rdf:Description></rdf:RDF></x:xmpmeta>";
        Metadata md = new Metadata();
        new XmpExtractor().extract(ml.getBytes(UTF_8), md);
        assertEquals("Hello World", md.get("dc:title:x-default"));
        assertEquals("Bonjour World", md.get("dc:title:fr-ca"));
        assertEquals("Bonjour World", md.get("xmp:dc:title:fr-ca"));   // Option A: xmp-namespaced too
    }
}
