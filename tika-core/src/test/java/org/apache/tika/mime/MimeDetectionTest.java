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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.ByteOrderMark;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class MimeDetectionTest {

    private static MimeTypes MIME_TYPES;

    private static MediaTypeRegistry REGISTRY;

    /**
     * @inheritDoc
     */
    @BeforeAll
    public static void setUp() {
        MIME_TYPES = MimeTypes.getDefaultMimeTypes();
        REGISTRY = MIME_TYPES.getMediaTypeRegistry();
    }

    @Test
    public void testDetection() throws Exception {
        testFile("image/svg+xml", "circles.svg");
        testFile("image/svg+xml", "circles-with-prefix.svg");
        testFile("image/png", "datamatrix.png");
        testFile("text/html", "test.html");
        testFile("application/xml", "test-iso-8859-1.xml");
        testFile("application/xml", "test-utf8.xml");
        testFile("application/xml", "test-utf8-bom.xml");
        testFile("application/xml", "test-utf16le.xml");
        testFile("application/xml", "test-utf16be.xml");
        testFile("application/xml", "test-long-comment.xml");
        testFile("application/xslt+xml", "stylesheet.xsl");
        testUrl("application/rdf+xml", "http://www.ai.sri.com/daml/services/owl-s/1.2/Process.owl",
                "test-difficult-rdf1.xml");
        testUrl("application/rdf+xml", "http://www.w3.org/2002/07/owl#", "test-difficult-rdf2.xml");
        // add evil test from TIKA-327
        testFile("text/html", "test-tika-327.html");
        // add another evil html test from TIKA-357
        testFile("text/html", "testlargerbuffer.html");
        // test fragment of HTML with <div> (TIKA-1102)
        testFile("text/html", "htmlfragment");
        // test fragment of HTML with <iframe> and potentially misleading file suffix
        testFile("text/html", "test-html-snippet-iframe.jsp");
        // test binary CGM detection (TIKA-1170)
        testFile("image/cgm", "plotutils-bin-cgm-v3.cgm");
        // test HTML detection of malformed file, previously identified as image/cgm (TIKA-1170)
        testFile("text/html", "test-malformed-header.html.bin");

        //test GCMD Directory Interchange Format (.dif) TIKA-1561
        testFile("application/dif+xml", "brwNIMS_2014.dif");

        // truncated xml should still be detected as xml, See TIKA-3596
        testFile("application/xml", "truncated-utf16-xml.xyz");

        // Android Binary XML (compiled AndroidManifest.xml / res/*.xml inside an APK).
        // Carries a .xml extension, so magic must win over the *.xml glob and it must
        // NOT be routed to application/xml / the XML parser. See TIKA-4747.
        testFile("application/vnd.android.axml", "test-android-binary.xml");
    }

    /** The html magic needs the tag to end: an XML element such as htmlPath is not html. */
    @Test
    public void testHtmlMagicNeedsTagEnd() throws Exception {
        String pad = " ".repeat(200);
        String[] notHtml = {
                "<htmlPath><![CDATA[TmplData\\tmpl.html]]></htmlPath>",
                "<flashTemplate><info>" + pad + "<htmlPath>x</htmlPath></info></flashTemplate>",
                "<root>" + pad + "<htmlContent>x</htmlContent></root>"
        };
        for (String xml : notHtml) {
            assertDetected(MediaType.TEXT_PLAIN, xml.getBytes(UTF_8));
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "flashTemplate.xml");
            try (TikaInputStream tis = TikaInputStream.get(xml.getBytes(UTF_8))) {
                assertEquals(MediaType.APPLICATION_XML, MIME_TYPES.detect(tis, metadata, new ParseContext()), xml);
            }
        }
        String[] html = {
                "<html><body>x</body></html>",
                "<HTML>\n<body>x</body></HTML>",
                "<html\nlang=\"en\"><body>x</body></html>",
                "garbage\n".repeat(30) + "<html><body>x</body></html>"
        };
        for (String h : html) {
            assertDetected(MediaType.TEXT_HTML, h.getBytes(UTF_8));
        }
    }

    /** An OS/2 bitmap array's first entry header (size 40, next at 46) is not an icon. */
    @Test
    public void testOs2BitmapArrayIsNotAnIcon() throws Exception {
        for (String tag : new String[]{"BM", "IC", "CI", "CP", "PT"}) {
            byte[] bitmapArray = new byte[64];
            bitmapArray[0] = 'B';
            bitmapArray[1] = 'A';
            bitmapArray[2] = 0x28;
            bitmapArray[6] = 0x2e;
            bitmapArray[14] = (byte) tag.charAt(0);
            bitmapArray[15] = (byte) tag.charAt(1);
            try (TikaInputStream tis = TikaInputStream.get(bitmapArray)) {
                assertEquals(MediaType.parse("image/x-os2-graphics; charset=binary"),
                        MIME_TYPES.detect(tis, new Metadata(), new ParseContext()), tag);
            }
        }
    }

    /**
     * TGA has no signature. The rules key on the colour map type and image
     * type, the colour map spec and the pixel depth.
     */
    @Test
    public void testTgaDetection() throws Exception {
        MediaType tga = MediaType.image("x-tga");
        MediaType cursor = MediaType.image("x-win-bitmap");
        // true colour, no id, no colour map, 24 bpp
        assertDetected(tga, tgaHeader(0, 0, 2, 0, 24));
        // the same with an id field and 32 bpp, and the RLE form
        assertDetected(tga, tgaHeader(5, 0, 2, 0, 32));
        assertDetected(tga, tgaHeader(0, 0, 10, 0, 32));
        // 15 bpp, with and without an id
        assertDetected(tga, tgaHeader(0, 0, 2, 0, 15));
        assertDetected(tga, tgaHeader(3, 0, 2, 0, 15));
        // greyscale
        assertDetected(tga, tgaHeader(0, 0, 3, 0, 8));
        assertDetected(tga, tgaHeader(0, 0, 11, 0, 8));
        // colour mapped, 24 bit entries, 8 bit indices, and the RLE form
        assertDetected(tga, tgaHeader(0, 1, 1, 24, 8));
        assertDetected(tga, tgaHeader(0, 1, 9, 24, 8));
        // no colour map but an entry size in the spec, as some writers leave it
        byte[] strayEntrySize = tgaHeader(0, 0, 2, 0, 24);
        strayEntrySize[7] = 24;
        assertDetected(tga, strayEntrySize);

        // a pixel depth no TGA has
        assertDetected(MediaType.OCTET_STREAM, tgaHeader(0, 0, 2, 0, 7));
        assertDetected(MediaType.OCTET_STREAM, tgaHeader(1, 0, 2, 0, 7));
        // a colour map entry size and an index depth no TGA has
        assertDetected(MediaType.OCTET_STREAM, tgaHeader(0, 1, 1, 7, 8));
        assertDetected(MediaType.OCTET_STREAM, tgaHeader(0, 1, 1, 24, 99));
        // a Windows cursor with one image starts like a true colour TGA
        byte[] oneImage = tgaHeader(0, 0, 2, 0, 24);
        oneImage[4] = 1;
        assertDetected(cursor, oneImage);
        // a cursor with no images is nothing; 256 images is a TGA with a colour map length
        byte[] noImages = tgaHeader(0, 0, 2, 0, 24);
        noImages[16] = 0;
        assertDetected(MediaType.OCTET_STREAM, noImages);
        byte[] images256 = tgaHeader(0, 0, 2, 0, 24);
        images256[5] = 1;
        assertDetected(MediaType.OCTET_STREAM, images256);
        // Lotus 1-2-3 shares the first four bytes
        byte[] lotus = new byte[64];
        lotus[2] = 2;
        lotus[4] = 4;
        lotus[5] = 4;
        assertDetected(MediaType.parse("application/vnd.lotus-1-2-3;version=1"), lotus);
    }

    @Test
    public void testAnimatedCursorDetection() throws Exception {
        MediaType ani = MediaType.application("x-navi-animation");
        byte[] acon = new byte[64];
        System.arraycopy("RIFF".getBytes(US_ASCII), 0, acon, 0, 4);
        acon[4] = 56;
        System.arraycopy("ACONanih".getBytes(US_ASCII), 0, acon, 8, 8);
        assertDetected(ani, acon);
        // the content beats a *.cur name
        assertEquals(ani, detect(acon, "pointer.cur"));
    }

    @Test
    public void testWoffDetection() throws Exception {
        assertDetected(MediaType.parse("font/woff"), header(0, "wOFF\u0000\u0001\u0000\u0000"));
        assertDetected(MediaType.parse("font/woff"), header(0, "wOFFOTTO"));
        assertDetected(MediaType.parse("font/woff2"), header(0, "wOF2\u0000\u0001\u0000\u0000"));
        assertDetected(MediaType.OCTET_STREAM, header(0, "wOFFxxxx"));
    }

    @Test
    public void testSquashfsDetection() throws Exception {
        MediaType squashfs = MediaType.application("vnd.squashfs");
        byte[] le = header(0, "hsqs");
        le[28] = 4;
        assertDetected(squashfs, le);
        byte[] be = header(0, "sqsh");
        be[29] = 3;
        assertDetected(squashfs, be);
        // no plausible major version
        assertDetected(MediaType.OCTET_STREAM, header(0, "hsqs"));
    }

    @Test
    public void testAdobeSwatchExchangeDetection() throws Exception {
        assertDetected(MediaType.application("x-adobe-swatch-exchange"),
                header(0, "ASEF\u0000\u0001\u0000\u0000"));
        assertDetected(MediaType.OCTET_STREAM, header(0, "ASEF\u0000\u0002\u0000\u0000"));
    }

    @Test
    public void testPalmDocDetection() throws Exception {
        assertDetected(MediaType.application("x-aportisdoc"), header(60, "TEXtREAd"));
        assertDetected(MediaType.application("x-mobipocket-ebook"), header(60, "BOOKMOBI"));
    }

    @Test
    public void testBinaryPgpSignatureDetection() throws Exception {
        MediaType sig = MediaType.application("pgp-signature");
        // v4, binary sig type, RSA, SHA-256; 2-byte length
        assertDetected(sig, bytes(0x89, 0x01, 0x1c, 0x04, 0x00, 0x01, 0x08, 0x00));
        // v4, text sig type, EdDSA; 1-byte length
        assertDetected(sig, bytes(0x88, 0x75, 0x04, 0x01, 0x16, 0x08, 0x00));
        // v3, hashed length 5
        assertDetected(sig, bytes(0x88, 0x3f, 0x03, 0x05, 0x00, 0x3f, 0xc0));
        // unknown public-key algorithm
        assertDetected(MediaType.OCTET_STREAM, bytes(0x89, 0x01, 0x1c, 0x04, 0x00, 0x07, 0x08));
        // new-format packets (RFC 9580 4.2): v6 Ed25519 SHA-512 with a 4-byte hashed length; v4 RSA
        assertDetected(sig, bytes(0xc2, 0x75, 0x06, 0x00, 0x1b, 0x0a, 0x00, 0x00, 0x00, 0x29));
        assertDetected(sig, bytes(0xc2, 0xc0, 0x1c, 0x04, 0x00, 0x01, 0x08, 0x00));
        // direct-key self-signature, not a detached one
        assertDetected(MediaType.OCTET_STREAM, bytes(0xc2, 0x75, 0x06, 0x1f, 0x1b, 0x0a, 0x00));
        assertDetected(MediaType.image("png"),
                bytes(0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 'I', 'H', 'D', 'R'));
    }

    @Test
    public void testEmbeddedOpenTypeVersions() throws Exception {
        MediaType eot = MediaType.application("vnd.ms-fontobject");
        for (int[] version : new int[][]{{0, 0, 1, 0}, {1, 0, 2, 0}, {2, 0, 2, 0}}) {
            byte[] b = header(34, "LP");
            for (int i = 0; i < 4; i++) {
                b[8 + i] = (byte) version[i];
            }
            assertDetected(eot, b);
        }
    }

    @Test
    public void testBitTorrentFirstKeys() throws Exception {
        MediaType torrent = MediaType.application("x-bittorrent");
        assertDetected(torrent, "d8:announce35:udp://tracker".getBytes(US_ASCII));
        assertDetected(torrent, "d13:announce-listll35:udp".getBytes(US_ASCII));
        assertDetected(torrent, "d7:comment5:hello4:infod".getBytes(US_ASCII));
        assertDetected(torrent, "d4:infod6:lengthi1024e".getBytes(US_ASCII));
    }

    @Test
    public void testMxfDetection() throws Exception {
        assertDetected(MediaType.application("mxf"),
                bytes(0x06, 0x0e, 0x2b, 0x34, 0x02, 0x05, 0x01, 0x01, 0x0d, 0x01, 0x02, 0x01,
                        0x01, 0x02, 0x04, 0x00));
    }

    @Test
    public void testJavaSerializationDetection() throws Exception {
        MediaType ser = MediaType.application("java-serialized-object");
        assertDetected(ser, bytes(0xac, 0xed, 0x00, 0x05, 0x73, 0x72, 0x00, 0x10));
        assertDetected(ser, bytes(0xac, 0xed, 0x00, 0x05, 0x77, 0x04));
        assertDetected(MediaType.OCTET_STREAM, bytes(0xac, 0xed, 0x53, 0xab, 0x49));
    }

    @Test
    public void testHtmlFragmentDetection() throws Exception {
        for (String s : new String[]{
                "<meta http-equiv=\"Content-Type\" content=\"text/html\">",
                "<br />\n<b>Warning</b>:  filesize()",
                "<p><a href='http://example.com'>x</a>",
                "</b> <br>\n<font face=Arial>",
                "<script type='text/javascript'>\nvar x = 1;",
                "<SMALL>x</SMALL>"}) {
            assertDetected(MediaType.TEXT_HTML, s.getBytes(US_ASCII));
        }
        assertDetected(MediaType.TEXT_PLAIN, "<abbr title=x>".getBytes(US_ASCII));
        assertDetected(MediaType.TEXT_PLAIN, "<bread> and butter".getBytes(US_ASCII));
        // injected analytics in front of a real document
        assertDetected(MediaType.application("pdf"),
                "<script>\n  (function(i,s,o,g,r,a,m){})\n</script>\n%PDF-1.4\n".getBytes(US_ASCII));
        assertDetected(MediaType.application("xhtml+xml"),
                ("<script src=\"urchin.js\"></script>\n" + "<!-- x -->\n".repeat(12)
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">").getBytes(US_ASCII));
        // a .xml name still picks XML, except for roots text/html claims (TIKA-377)
        byte[] table = "<table name=\"t\"><row id=\"1\"/></table>".getBytes(US_ASCII);
        assertEquals(MediaType.APPLICATION_XML, detect(table, "test.xml"));
        assertEquals(MediaType.TEXT_HTML, detect(table, "test.html"));
        assertEquals(MediaType.TEXT_HTML, detect(table, null));
        assertEquals(MediaType.TEXT_HTML, detect("<p>x</p>".getBytes(US_ASCII), "test.xml"));
        assertEquals(MediaType.TEXT_HTML,
                detect("<br />\n<b>Warning</b>: x".getBytes(US_ASCII), "test.php"));
    }

    @Test
    public void testAsxWithoutXmlDeclaration() throws Exception {
        MediaType asx = MediaType.application("x-ms-asx");
        assertDetected(asx, "<ASX version = \"3.0\">\n<Entry>".getBytes(US_ASCII));
        assertDetected(asx, "<Asx Version=\"3.0\"><Entry>".getBytes(US_ASCII));
    }

    @Test
    public void testMpegTransportStream() throws Exception {
        MediaType ts = MediaType.video("mp2t");
        assertDetected(ts, packets(0, 188, 5));
        assertDetected(ts, packets(4, 192, 5));
        assertDetected(MediaType.OCTET_STREAM, packets(0, 188, 4));
    }

    @Test
    public void testMarcWithBlankBibliographicLevel() throws Exception {
        assertDetected(MediaType.application("marc"),
                "01252na  a2200325   4500001001300000".getBytes(US_ASCII));
    }

    @Test
    public void testDerCrlAndCertificateAnySignatureAlgorithm() throws Exception {
        // v2 CRL signed with SM2 (OID 1.2.156.10197.1.501), long and short inner lengths
        assertDetected(MediaType.application("pkix-crl"), bytes(0x30, 0x82, 0x01, 0x17, 0x30, 0x81, 0xbe,
                0x02, 0x01, 0x01, 0x30, 0x0a, 0x06, 0x08, 0x2a, 0x81, 0x1c, 0xcf, 0x55, 0x01, 0x83, 0x75));
        assertDetected(MediaType.application("pkix-crl"), bytes(0x30, 0x81, 0xa0, 0x30, 0x48,
                0x02, 0x01, 0x01, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70));
        // v1 CRL (no version field) from openssl ca -gencrl, ecdsa-with-SHA256, issuer CN=T
        byte[] crlV1 = new byte[64];
        byte[] crlHead = {0x30, (byte) 0x81, (byte) 0x91, 0x30, 0x38, 0x30, 0x0a, 0x06, 0x08, 0x2a,
                (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x04, 0x03, 0x02, 0x30, 0x0c, 0x31, 0x0a, 0x30, 0x08,
                0x06, 0x03, 0x55, 0x04, 0x03, 0x0c, 0x01, 0x54, 0x17, 0x0d};
        System.arraycopy(crlHead, 0, crlV1, 0, crlHead.length);
        System.arraycopy("261105180134Z".getBytes(US_ASCII), 0, crlV1, crlHead.length, 13);
        assertDetected(MediaType.application("pkix-crl"), crlV1);
        // v3 certificate, then serialNumber
        assertDetected(MediaType.parse("application/x-x509-cert;format=der"), bytes(0x30, 0x82, 0x0a, 0x5d,
                0x30, 0x82, 0x0a, 0x0c, 0xa0, 0x03, 0x02, 0x01, 0x02, 0x02, 0x11, 0x00));
        // v1 certificate with serial 1 opens like a v2 CRL; its validity tells them apart
        byte[] v1 = new byte[128];
        byte[] head = {0x30, (byte) 0x82, 0x02, 0x7c, 0x30, (byte) 0x81, (byte) 0xe5, 0x02, 0x01, 0x01,
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01,
                0x0b, 0x05, 0x00, 0x30, 0x0b, 0x31, 0x09, 0x30, 0x07, 0x06, 0x03, 0x55, 0x04, 0x03, 0x0c, 0x00,
                0x30, 0x1e, 0x17, 0x0d};
        System.arraycopy(head, 0, v1, 0, head.length);
        System.arraycopy("250101000000Z\u0017\r260101000000Z".getBytes(US_ASCII), 0, v1, head.length, 28);
        assertDetected(MediaType.parse("application/x-x509-cert;format=der"), v1);
        // RFC 3161 reply: granted, "Operation Okay", then the signedData token
        byte[] tsr = bytes(0x30, 0x82, 0x0a, 0xb5, 0x30, 0x15, 0x02, 0x01, 0x00, 0x30, 0x10, 0x0c, 0x0e);
        System.arraycopy("Operation Okay".getBytes(US_ASCII), 0, tsr, 13, 14);
        byte[] token = {0x30, (byte) 0x82, 0x0a, (byte) 0x9a, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x07, 0x02};
        System.arraycopy(token, 0, tsr, 27, token.length);
        assertDetected(MediaType.application("timestamp-reply"), tsr);
        // PKCS#10 request: version 0, then a Name, not an AlgorithmIdentifier
        assertDetected(MediaType.OCTET_STREAM, bytes(0x30, 0x82, 0x01, 0x0a, 0x30, 0x81, 0xb1,
                0x02, 0x01, 0x00, 0x30, 0x46, 0x31, 0x0b));
    }

    private static byte[] packets(int first, int size, int count) {
        byte[] b = new byte[1024];
        for (int i = 0; i < count; i++) {
            b[first + i * size] = 0x47;
        }
        return b;
    }

    @Test
    public void testSubRipLineEndings() throws Exception {
        MediaType srt = MediaType.application("x-subrip");
        for (String eol : new String[]{"\n", "\r", "\r\n"}) {
            assertDetected(srt, ("1" + eol + "00:00:01,000 --> 00:00:02,000" + eol + "Hi").getBytes(US_ASCII));
        }
    }

    private static byte[] header(int offset, String ascii) {
        byte[] header = new byte[128];
        byte[] value = ascii.getBytes(US_ASCII);
        System.arraycopy(value, 0, header, offset, value.length);
        return header;
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[64];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] tgaHeader(int idLength, int colourMapType, int imageType,
                                    int colourMapEntrySize, int pixelDepth) {
        byte[] header = new byte[64];
        header[0] = (byte) idLength;
        header[1] = (byte) colourMapType;
        header[2] = (byte) imageType;
        if (colourMapType == 1) {
            // 256 entries
            header[6] = 1;
            header[7] = (byte) colourMapEntrySize;
        }
        header[12] = 32;
        header[14] = 32;
        header[16] = (byte) pixelDepth;
        return header;
    }

    private static void assertDetected(MediaType expected, byte[] bytes) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            assertEquals(expected, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
    }

    private static MediaType detect(byte[] bytes, String name) throws IOException {
        Metadata metadata = new Metadata();
        if (name != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        }
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            return MIME_TYPES.detect(tis, metadata, new ParseContext());
        }
    }

    @Test
    public void testBinaryContentRefusesTextGlob() throws IOException {
        // headers copied from real files that a text glob used to claim
        String[][] cases = {
                // Terminal Reality POD archive
                {"Bulldog.pod", "2f000000436f6d70696c65642062792042696e456469742056657273696f6e20"
                        + "322e322e38207da62d2900000000000000000000000000000000000000000000"
                        + "00000000000000000000000000000000000000004152545c4241545453352e41"},
                // Mozilla startupCache XDR bytecode, named for its source
                {"AccountManager.js",
                        "0000001c000000b9000000c5fcc6bf73b9000000000000000000000000000000"
                        + "0081030000f0000000b900000031000000c1030000000000003b000000000000"
                        + "0000000000000000000000000022000000000000000010000000010000016368"},
                {"overlay.xul", "42c073b9000000002306000096000000b9000000720000002b02000000000000"
                        + "360000000000000000000000000000005d000000000000000430000001010000"
                        + "016368726f6d653a2f2f676c6f62616c2f636f6e74656e742f42726f77736572"},
                // CODESYS project .meta
                {"1f876365.meta", "02200928000000000000000000000000220000000f01bf0df704c00d0180060f"
                        + "070504f4010000c10df904000a00e70c236f3456d208"},
                // BM-E record
                {"10.T", "424d2d4564312e332d574b0031ff0d1a35460bb1000600000048000600000051"
                        + "0007006f00b800080075005b000b00080077000d006f00d7000e0075001f000e"
                        + "000000270117007500080019007500c3001900750005001a0075004a001b0000"}};
        for (String[] c : cases) {
            byte[] bytes = hex(c[1]);
            assertEquals(MediaType.OCTET_STREAM, detect(bytes, c[0]), c[0]);
            Metadata metadata = new Metadata();
            metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
            try (TikaInputStream tis = TikaInputStream.get(bytes)) {
                assertEquals(MediaType.OCTET_STREAM,
                        MIME_TYPES.detect(tis, metadata, new ParseContext()), c[0]);
            }
        }
    }

    @Test
    public void testTextGlobStillWinsForText() throws IOException {
        String latin = "The quick brown fox jumps over the lazy dog.\n";
        // CJK whose code units have low bytes under 0x20: NULs and controls in both columns
        String cjk = "一丁七万三上下不与专 且"
                + "世业东两 abc\n";
        String russian = "Съешь же ещё "
                + "этих мягких "
                + "французских.\n";
        MediaType log = MediaType.text("x-log");
        assertEquals(MediaType.TEXT_PLAIN, detect(latin.repeat(20).getBytes(UTF_16LE), "a.txt"));
        assertEquals(MediaType.TEXT_PLAIN, detect(latin.repeat(20).getBytes(UTF_16BE), "a.txt"));
        assertEquals(MediaType.TEXT_PLAIN, detect(cjk.repeat(20).getBytes(UTF_16LE), "a.txt"));
        assertEquals(MediaType.TEXT_PLAIN, detect(cjk.repeat(20).getBytes(UTF_16BE), "a.txt"));
        for (String utf32 : new String[] {"UTF-32LE", "UTF-32BE"}) {
            assertEquals(MediaType.TEXT_PLAIN, detect(latin.repeat(20).getBytes(utf32), "a.txt"), utf32);
            assertEquals(MediaType.TEXT_PLAIN, detect(cjk.repeat(20).getBytes(utf32), "a.txt"), utf32);
            Metadata metadata = new Metadata();
            metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
            try (TikaInputStream tis = TikaInputStream.get(russian.repeat(20).getBytes(utf32))) {
                assertEquals(MediaType.TEXT_PLAIN, MIME_TYPES.detect(tis, metadata, new ParseContext()), utf32);
            }
        }
        assertEquals(MediaType.TEXT_PLAIN,
                detect(russian.repeat(20).getBytes("windows-1251"), "a.txt"));
        assertEquals(log, detect(("\u001b[32mINFO\u001b[0m started\n").repeat(40)
                .getBytes(US_ASCII), "server.log"));
        // a stray control byte per line stays under the binary threshold
        assertEquals(log, detect(("2026-11-07 12:00:00 ok\u0000\n").repeat(40)
                .getBytes(US_ASCII), "server.log"));
        // zero fill after the text, as a crashed or preallocated log leaves
        byte[] text = "[Mon Oct 26 03:26:03 2015] [notice] SIGHUP received.\n".repeat(18)
                .getBytes(US_ASCII);
        byte[] zeroFilled = java.util.Arrays.copyOf(text, 8192);
        assertEquals(log, detect(zeroFilled, "error_log.log"));
        assertEquals(MediaType.TEXT_PLAIN, detect(zeroFilled, null));
    }

    @Test
    public void testUtf8BomBeforeBinaryMagic() throws IOException {
        String bom = "efbbbf";
        assertEquals(MediaType.image("jpeg"),
                detect(hex(bom + "ffd8ffe000104a46494600010101004800480000ffdb004300080606"), null));
        assertEquals(MediaType.image("png"),
                detect(hex(bom + "89504e470d0a1a0a0000000d4948445200000710000004b008060000"), null));
        assertEquals(MediaType.audio("mpeg"),
                detect(hex(bom + "49443303000000001f76434f4d4d0000001e000000646575007777"), null));
        assertEquals(MediaType.image("gif"),
                detect(hex(bom + "4749463839610a000a00f70000000000800000008000808000000080"), null));
        assertEquals(MediaType.application("zip"),
                detect(hex(bom + "504b03041400000008001c3f364d479244b25801000000a3d4b2"), null));
        // text that merely starts with a magic string stays text
        assertEquals(MediaType.TEXT_PLAIN,
                detect("﻿ID3 tags hold the title and artist.\n".getBytes(UTF_8), null));
        assertEquals(MediaType.TEXT_PLAIN,
                detect("﻿GIF89a is the animated variant.\n".getBytes(UTF_8), "notes.txt"));
    }

    @Test
    public void testTextHeaderOnBinaryBodyIsNotText() throws IOException {
        // BSB/KAP chart: text header lines, then 0x1A 0x00 and raster bytes
        StringBuilder header = new StringBuilder("VER/2.0\r\nBSB/NA=Mallorca,NU=1\r\n");
        for (int i = 0; header.length() < 1900; i++) {
            header.append("RGB/").append(i).append(',').append(i * 7 % 256).append(',')
                    .append(i * 13 % 256).append(',').append(i * 3 % 256).append("\r\n");
        }
        byte[] text = header.toString().getBytes(US_ASCII);
        byte[] raster = new byte[4000];
        new java.util.Random(42).nextBytes(raster);
        byte[] kap = new byte[text.length + 2 + raster.length];
        System.arraycopy(text, 0, kap, 0, text.length);
        kap[text.length] = 0x1A;
        System.arraycopy(raster, 0, kap, text.length + 2, raster.length);
        org.apache.tika.detect.Detector detector =
                new org.apache.tika.detect.DefaultDetector(MIME_TYPES);
        try (TikaInputStream tis = TikaInputStream.get(kap)) {
            assertEquals(MediaType.OCTET_STREAM,
                    detector.detect(tis, new Metadata(), new ParseContext()));
        }
        try (TikaInputStream tis = TikaInputStream.get(text)) {
            assertEquals(MediaType.TEXT_PLAIN,
                    detector.detect(tis, new Metadata(), new ParseContext()));
        }
    }

    private static byte[] hex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    @Test
    public void testXulByContent() throws IOException {
        MediaType xul = MediaType.application("vnd.mozilla.xul+xml");
        String ns = "http://www.mozilla.org/keymaster/gatekeeper/there.is.only.xul";
        byte[] plain = ("<?xml version=\"1.0\"?><window xmlns=\"" + ns + "\"/>").getBytes(UTF_8);
        // the root's attribute entity stops the root-element sniff
        byte[] entityInRoot = ("<?xml version=\"1.0\"?>\n<!DOCTYPE wizard [<!ENTITY % d SYSTEM"
                + " \"chrome://a/b.dtd\"> %d;]>\n<wizard title=\"&wizard.title;\" xmlns=\"" + ns
                + "\"/>").getBytes(UTF_8);
        byte[] noDeclaration = ("<window title=\"t\" xmlns=\"" + ns + "\"/>").getBytes(UTF_8);
        for (byte[] doc : new byte[][]{plain, entityInRoot, noDeclaration}) {
            assertEquals(xul, detect(doc, null));
            assertEquals(xul, detect(doc, "attachment.xml"));
        }
        // mentioning the namespace is not enough
        assertEquals(MediaType.TEXT_PLAIN,
                detect(("document.createElementNS(\"" + ns + "\", \"label\");").getBytes(UTF_8), null));
        assertEquals(MediaType.APPLICATION_XML,
                detect(("<?xml version=\"1.0\"?><bindings xmlns=\"http://www.mozilla.org/xbl\""
                        + " xmlns:xul=\"" + ns + "\"/>").getBytes(UTF_8), null));
    }

    @Test
    public void testRustVersusRlsServices() throws IOException {
        MediaType rust = MediaType.text("x-rust");
        MediaType rls = MediaType.application("rls-services+xml");
        assertEquals(rust, detect("fn main() {}\n".getBytes(UTF_8), "main.rs"));
        assertEquals(rust, detect("// Licensed to the ASF\nfn main() {}\n".getBytes(UTF_8),
                "main.rs"));

        byte[] rlsXml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rls-services xmlns=\"urn:ietf:params:xml:ns:rls-services\">"
                + "<service uri=\"sip:friends@example.com\"/></rls-services>").getBytes(UTF_8);
        assertEquals(rls, detect(rlsXml, "services.rs"));
        assertEquals(rls, detect(rlsXml, null));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};
        assertEquals(MediaType.image("png"), detect(png, "main.rs"));
    }

    @Test
    public void testDetectionWithoutContent() throws IOException {
        testUrlWithoutContent("text/html", "test.html");
        testUrlWithoutContent("text/html", "http://test.com/test.html");
        testUrlWithoutContent("text/plain", "http://test.com/test.txt");

        // In case the url contains a filename referencing a server-side scripting language,
        // it gives us no clue concerning the actual mime type of the response
        testUrlWithoutContent("application/octet-stream", "http://test.com/test.php");
        testUrlWithoutContent("application/octet-stream", "http://test.com/test.cgi");
        testUrlWithoutContent("application/octet-stream", "http://test.com/test.jsp");
        // But in case the protocol is not http or https, the script is probably not interpreted
        testUrlWithoutContent("text/x-php", "ftp://test.com/test.php");
    }

    @Test
    public void testByteOrderMark() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get("\ufefftest".getBytes(UTF_16LE))) {
            assertEquals(MediaType.TEXT_PLAIN, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
        try (TikaInputStream tis = TikaInputStream.get("\ufefftest".getBytes(UTF_16BE))) {
            assertEquals(MediaType.TEXT_PLAIN, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
        try (TikaInputStream tis = TikaInputStream.get("\ufefftest".getBytes(UTF_8))) {
            assertEquals(MediaType.TEXT_PLAIN, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
    }

    @Test
    public void testRFC822WithBOM() throws Exception {
        String header = "From: blah <blah@blah.com>\r\n" + "Received: Friday, January 24, 2020 3:24 PM\r\n" +
                "To: someone@somewhere.com\r\n" + "Cc: someone-else@other.com\r\n" +
                "Subject: Received\r\n";
        MediaType rfc822 = MediaType.parse("message/rfc822");
        try (TikaInputStream tis = TikaInputStream.get(header.getBytes(UTF_8))) {
            assertEquals(rfc822, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }

        int utfLength = ByteOrderMark.UTF_8.length();
        byte[] bytes = new byte[header.getBytes(UTF_8).length + utfLength];
        System.arraycopy(ByteOrderMark.UTF_8.getBytes(), 0, bytes, 0, utfLength);
        System.arraycopy(header.getBytes(UTF_8), 0, bytes, 3, header.getBytes(UTF_8).length);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            assertEquals(rfc822, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
    }

    @Test
    public void testSuperTypes() {
        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("text/something; charset=UTF-8"),
                MediaType.parse("text/something")));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("text/something; charset=UTF-8"),
                MediaType.TEXT_PLAIN));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("text/something; charset=UTF-8"),
                MediaType.OCTET_STREAM));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("text/something"),
                MediaType.TEXT_PLAIN));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("application/something+xml"),
                MediaType.APPLICATION_XML));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("application/something+zip"),
                MediaType.APPLICATION_ZIP));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("application/vnd.apple.iwork"),
                MediaType.APPLICATION_ZIP));

        assertTrue(REGISTRY.isSpecializationOf(MediaType.parse("application/json"),
                MediaType.TEXT_PLAIN));
    }

    @SuppressWarnings("unused")
    private void testUrlOnly(String expected, String url) throws IOException {
        InputStream in = new URL(url).openStream();
        testStream(expected, url, in);
    }

    private void testUrlWithoutContent(String expected, String url) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, url);
        String mime = this.MIME_TYPES.detect(null, metadata, new ParseContext()).toString();
        assertEquals(expected, mime,
                url + " is not properly detected using only resource name");
    }

    private void testUrl(String expected, String url, String file) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(file)) {
            testStream(expected, url, in);
        }
    }

    private void testFile(String expected, String filename) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(filename)) {
            testStream(expected, filename, in);
        }
    }

    private void testStream(String expected, String urlOrFileName, InputStream in)
            throws IOException {
        assertNotNull(in, "Test stream: [" + urlOrFileName + "] is null!");
        try (TikaInputStream tis = TikaInputStream.get(in)) {
            Metadata metadata = new Metadata();
            String mime = this.MIME_TYPES.detect(tis, metadata, new ParseContext()).toString();
            assertEquals(expected, mime,
                    urlOrFileName + " is not properly detected: detected.");

            //Add resource name and test again
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, urlOrFileName);
            mime = this.MIME_TYPES.detect(tis, metadata, new ParseContext()).toString();
            assertEquals(expected, mime,
                    urlOrFileName + " is not properly detected after adding resource name.");
        }
    }

    /**
     * Test for type detection of empty documents.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-483">TIKA-483</a>
     */
    @Test
    public void testEmptyDocument() throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            assertEquals(MediaType.OCTET_STREAM,
                    MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }

        Metadata namehint = new Metadata();
        namehint.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.txt");
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            assertEquals(MediaType.TEXT_PLAIN,
                    MIME_TYPES.detect(tis, namehint, new ParseContext()));
        }

        Metadata typehint = new Metadata();
        typehint.set(HttpHeaders.CONTENT_TYPE, "text/plain");
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            assertEquals(MediaType.TEXT_PLAIN,
                    MIME_TYPES.detect(tis, typehint, new ParseContext()));
        }

    }

    /**
     * Test for things like javascript files whose content is enclosed in XML
     * comment delimiters, but that aren't actually XML.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-426">TIKA-426</a>
     */
    @Test
    public void testNotXML() throws IOException {
        try (TikaInputStream tis = TikaInputStream.get("<!-- test -->".getBytes(UTF_8))) {
            assertEquals(MediaType.TEXT_PLAIN, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
    }

    /**
     * Tests that when we repeatedly test the detection of a document
     * that can be detected with Mime Magic, that we consistently
     * detect it correctly. See TIKA-391 for more details.
     */
    @Test
    public void testMimeMagicStability() throws IOException {
        for (int i = 0; i < 100; i++) {
            testFile("application/vnd.ms-excel", "test.xls");
        }
    }

    /**
     * Tests that when two magic matches both apply, and both
     * have the same priority, we use the name to pick the
     * right one based on the glob, or the first one we
     * come across if not. See TIKA-1292 for more details.
     */
    @Test
    public void testMimeMagicClashSamePriority() throws IOException {
        byte[] helloWorld = "Hello, World!".getBytes(UTF_8);
        MediaType helloType = MediaType.parse("hello/world-file");
        MediaType helloXType = MediaType.parse("hello/x-world-hello");
        Metadata metadata;

        // With a filename, picks the right one
        metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.hello.world");
        try (TikaInputStream tis = TikaInputStream.get(helloWorld)) {
            assertEquals(helloType, MIME_TYPES.detect(tis, metadata, new ParseContext()));
        }

        metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.x-hello-world");
        try (TikaInputStream tis = TikaInputStream.get(helloWorld)) {
            assertEquals(helloXType, MIME_TYPES.detect(tis, metadata, new ParseContext()));
        }

        // Without, goes for the one that sorts last
        metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testingTESTINGtesting");
        try (TikaInputStream tis = TikaInputStream.get(helloWorld)) {
            assertEquals(helloXType, MIME_TYPES.detect(tis, metadata, new ParseContext()));
        }
    }

    /**
     * Test for TIKA-3771.
     */
    @Test
    public void testPNGWithSomeEmlHeaders() throws IOException {
        testFile("image/png", "test-pngNotEml.bin");
    }

    @Test
    public void testLyingDeclaredLengthDoesNotTruncateMagic() throws Exception {
        byte[] pdf = "%PDF-1.4\nsome pdf content".getBytes(UTF_8);
        Metadata metadata = new Metadata();
        // an under-declared Content-Length must not shrink the magic read
        metadata.set(HttpHeaders.CONTENT_LENGTH, "2");
        try (TikaInputStream tis = TikaInputStream.get(
                new java.io.ByteArrayInputStream(pdf),
                new org.apache.tika.io.TemporaryResources(), metadata)) {
            assertEquals(MediaType.application("pdf"),
                    MIME_TYPES.detect(tis, metadata, new ParseContext()));
        }
    }
}
