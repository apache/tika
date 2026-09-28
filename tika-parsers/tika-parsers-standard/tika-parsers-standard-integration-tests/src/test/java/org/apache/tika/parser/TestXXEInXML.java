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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDXFAResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Drives the document parsers, not XMLReaderUtils, with XXE and entity-expansion payloads
 * injected into every XML-based format Tika reads: bare XML and its dialects, and the XML
 * parts inside zip containers. The oracle for "fetched" is a local socket that must never
 * see a connection, plus the file-not-found that a resolved bogus file URI would raise.
 * XFA and XMP streams inside a synthesized PDF, and XMP packets inside image formats, are
 * covered by injecting into the packet's own padding.
 */
public class TestXXEInXML extends XMLTestBase {

    private static final String[] XML_FILES = {"testXXE.xml", "testWORD_2003ml.xml",
            "testWORD_2006ml.xml", "testSVG.svg", "rsstest_20.rss", "testATOM.atom",
            "testXLIFF12.xlf", "testTMX.tmx", "test.fb2", "testODTMacro.fodt"};

    private static final String[] ZIP_FILES = {"testWORD.docx", "testWORD_macros.docm",
            "testEXCEL_textbox.xlsx", "testEXCEL_macro.xlsm", "testPPT_2imgs.pptx",
            "testPPT_macros.pptm", "testVISIO.vsdx", "testXPS_various.xps", "testEPUB.epub",
            "testODTStyles2.odt", "testFooter.ods", "testMasterFooter.odp",
            "testKeynote2018.key", "testPages2013.pages", "testNumbers2013.numbers"};

    private static final String BOGUS_FILE = "file:///couldnt_possibly_exist/xxe.dtd";
    private static final long MAX_CHARS = 50_000_000L;

    private static ServerSocket oracle;
    private static Thread acceptor;
    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static byte[] xxeHttp;
    private static byte[] xxeFile;

    @BeforeAll
    public static void startOracle() throws IOException {
        oracle = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
        acceptor = new Thread(() -> {
            while (!oracle.isClosed()) {
                try (Socket s = oracle.accept()) {
                    CONNECTIONS.incrementAndGet();
                } catch (IOException e) {
                    return;
                }
            }
        }, "xxe-oracle");
        acceptor.setDaemon(true);
        acceptor.start();
        String base = "http://127.0.0.1:" + oracle.getLocalPort() + "/";
        xxeHttp = ("<!DOCTYPE roottag SYSTEM \"" + base + "xxe.dtd\" [<!ENTITY % p SYSTEM \"" +
                base + "p.dtd\">%p;]>").getBytes(StandardCharsets.UTF_8);
        xxeFile = ("<!DOCTYPE roottag SYSTEM \"" + BOGUS_FILE + "\" [<!ENTITY % p SYSTEM \"" +
                BOGUS_FILE + "\">%p;]>").getBytes(StandardCharsets.UTF_8);
    }

    @AfterAll
    public static void stopOracle() throws IOException {
        oracle.close();
    }

    // the oracle must catch a parser that does fetch, or every green test below is vacuous
    @Test
    public void testOracleDetectsFetch() throws Exception {
        byte[] doc = injectXML(bareXml(), xxeHttp);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
        int before = CONNECTIONS.get();
        try {
            factory.newSAXParser().parse(new ByteArrayInputStream(doc), new DefaultHandler());
        } catch (SAXException | IOException e) {
            // the oracle closes the connection without answering; an error is expected
        }
        assertTrue(CONNECTIONS.get() > before, "unsecured parser did not reach the oracle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "file"})
    public void testXmlFiles(String payload) throws Exception {
        for (String fileName : XML_FILES) {
            byte[] injected = injectXML(read(fileName), payload(payload));
            for (int i = 0; i < XMLReaderUtils.getPoolSize() + 1; i++) {
                assertNoFetch(fileName, () -> parseBytes(fileName, injected));
            }
        }
    }

    // every XML plist carries an Apple DTD reference; dd-plist parses it with its own parser
    @Test
    public void testPlistDoctype() throws Exception {
        String plist = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist SYSTEM \"" +
                new String(xxeHttp, StandardCharsets.UTF_8).replaceAll(".*SYSTEM \"([^\"]+)\".*", "$1") +
                "\"><plist version=\"1.0\"><dict><key>k</key><string>v</string></dict></plist>";
        byte[] bytes = plist.getBytes(StandardCharsets.UTF_8);
        assertNoFetch("inline.plist", () -> parseBytes("inline.plist", bytes));
    }

    // an XInclude is a fetch vector only if a parser enables it; none may
    @Test
    public void testXIncludeNotResolved() throws Exception {
        String base = new String(xxeHttp, StandardCharsets.UTF_8).replaceAll(".*SYSTEM \"([^\"]+)/xxe.dtd\".*", "$1");
        byte[] doc = ("<?xml version=\"1.0\"?><r xmlns:xi=\"http://www.w3.org/2001/XInclude\">" +
                "<xi:include href=\"" + base + "/inc.xml\"/><xi:include href=\"" + BOGUS_FILE + "\" parse=\"text\"/></r>")
                .getBytes(StandardCharsets.UTF_8);
        assertNoFetch("xinclude.xml", () -> parseBytes("xinclude.xml", doc));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "file"})
    public void testZipContainers(String payload) throws Exception {
        for (String fileName : ZIP_FILES) {
            Path injected = injectZip(fileName, payload(payload));
            try {
                assertNoFetch(fileName, () -> parsePath(fileName, injected));
            } finally {
                Files.delete(injected);
            }
        }
    }

    @Test
    @Timeout(300)
    public void testExpansionBombInXmlFiles() throws Exception {
        for (String fileName : XML_FILES) {
            byte[] injected = injectXML(read(fileName), ENTITY_EXPANSION_BOMB);
            assertBounded(fileName, () -> parseBytes(fileName, injected));
        }
    }

    @Test
    @Timeout(600)
    public void testExpansionBombInZipContainers() throws Exception {
        for (String fileName : ZIP_FILES) {
            Path injected = injectZip(fileName, ENTITY_EXPANSION_BOMB);
            try {
                assertBounded(fileName, () -> parsePath(fileName, injected));
            } finally {
                Files.delete(injected);
            }
        }
    }

    // CVE-2025-66516: the XFA stream inside a PDF is XML parsed straight out of the file
    @ParameterizedTest
    @ValueSource(strings = {"http", "file"})
    public void testXfaInPdf(String payload) throws Exception {
        byte[] pdf = pdfWithXfa(injectXML(xfaXml(), payload(payload)));
        for (int i = 0; i < XMLReaderUtils.getPoolSize() + 1; i++) {
            Metadata metadata = new Metadata();
            assertNoFetch("xfa.pdf", () -> parseBytes("xfa.pdf", pdf, metadata));
            assertEquals("true", metadata.get(PDF.HAS_XFA), "the XFA stream was not reached");
        }
    }

    @Test
    @Timeout(120)
    public void testExpansionBombInXfa() throws Exception {
        byte[] pdf = pdfWithXfa(injectXML(xfaXml(), ENTITY_EXPANSION_BOMB));
        Metadata metadata = new Metadata();
        assertBounded("xfa.pdf", () -> parseBytes("xfa.pdf", pdf, metadata));
        assertEquals("true", metadata.get(PDF.HAS_XFA), "the XFA stream was not reached");
    }

    private static byte[] pdfWithXfa(byte[] xfa) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDStream stream = new PDStream(doc, new ByteArrayInputStream(xfa));
            form.setXFA(new PDXFAResource(stream.getCOSObject()));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private static byte[] xfaXml() {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\">" +
                "<template xmlns=\"http://www.xfa.org/schema/xfa-template/3.3/\">" +
                "<subform name=\"form1\"><field name=\"n\"><assist><toolTip>Name</toolTip>" +
                "</assist></field></subform></template></xdp:xdp>").getBytes(StandardCharsets.UTF_8);
    }

    // XMP packets inside binary containers: the payload goes in after the xpacket PI and
    // the same number of padding bytes comes out before the closing PI, so no offset moves
    private static final String[] XMP_FILES = {"testJPEG_GEO.jpg", "testTIFF.tif", "testPSD_xmp.psd", "testJXL_ISOBMFF.jxl"};

    @ParameterizedTest
    @ValueSource(strings = {"http", "file"})
    public void testXmpInBinaryFormats(String payload) throws Exception {
        for (String fileName : XMP_FILES) {
            byte[] original = read(fileName);
            Metadata clean = new Metadata();
            parseBytes(fileName, original, clean);
            assertTrue(hasXmpKey(clean), fileName + ": the clean fixture yields no XMP metadata");
            byte[] injected = injectIntoXmpPacket(original, payload(payload));
            assertNoFetch(fileName, () -> parseBytes(fileName, injected));
        }
    }

    @Test
    @Timeout(300)
    public void testExpansionBombInXmp() throws Exception {
        for (String fileName : XMP_FILES) {
            byte[] injected = injectIntoXmpPacket(read(fileName), ENTITY_EXPANSION_BOMB);
            assertBounded(fileName, () -> parseBytes(fileName, injected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "file"})
    public void testXmpInPdf(String payload) throws Exception {
        byte[] pdf = pdfWithXmp(injectXML(xmpPacket(), payload(payload)));
        Metadata metadata = new Metadata();
        assertNoFetch("xmp.pdf", () -> parseBytes("xmp.pdf", pdf, metadata));
        assertEquals("true", metadata.get(PDF.HAS_XMP), "the XMP stream was not reached");
    }

    private static boolean hasXmpKey(Metadata metadata) {
        for (String name : metadata.names()) {
            if (name.startsWith("xmp") || name.startsWith("dc:")) {
                return true;
            }
        }
        return false;
    }

    static byte[] injectIntoXmpPacket(byte[] file, byte[] payload) {
        String s = new String(file, StandardCharsets.ISO_8859_1);
        int begin = s.indexOf("<?xpacket begin");
        assertTrue(begin >= 0, "no XMP packet");
        int insert = s.indexOf("?>", begin) + 2;
        int end = s.indexOf("<?xpacket end", insert);
        int pad = 0;
        while (pad < end - insert && Character.isWhitespace(s.charAt(end - 1 - pad))) {
            pad++;
        }
        assertTrue(pad >= payload.length, "packet padding " + pad + " < payload " + payload.length);
        byte[] out = new byte[file.length];
        System.arraycopy(file, 0, out, 0, insert);
        System.arraycopy(payload, 0, out, insert, payload.length);
        System.arraycopy(file, insert, out, insert + payload.length, end - payload.length - insert);
        System.arraycopy(file, end, out, end, file.length - end);
        return out;
    }

    private static byte[] xmpPacket() {
        return ("<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
                "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
                "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">t</rdf:li></rdf:Alt></dc:title>" +
                "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pdfWithXmp(byte[] xmp) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.getDocumentCatalog().setMetadata(new PDMetadata(doc, new ByteArrayInputStream(xmp)));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private interface Parse {
        long run() throws Exception;
    }

    private static void assertNoFetch(String fileName, Parse parse) {
        int before = CONNECTIONS.get();
        try {
            parse.run();
        } catch (Exception e) {
            // injection may well corrupt the document; only a fetch is a failure
            assertNotAFetch(fileName, e);
        }
        assertEquals(before, CONNECTIONS.get(), fileName + ": parser connected to the oracle");
    }

    private static void assertBounded(String fileName, Parse parse) {
        try {
            long chars = parse.run();
            assertTrue(chars < MAX_CHARS, fileName + ": expanded " + chars + " chars");
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t.getMessage() != null && t.getMessage().startsWith("harness:")) {
                    fail(fileName + ": " + t.getMessage());
                }
                t = t.getCause();
            }
        }
    }

    private static void assertNotAFetch(String fileName, Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof FileNotFoundException || t instanceof ConnectException) {
                fail(fileName + ": parser tried to fetch an external resource", e);
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("couldnt_possibly_exist") ||
                    msg.contains("No such file") || msg.contains("Connection refused"))) {
                fail(fileName + ": parser tried to fetch an external resource", e);
            }
            t = t.getCause();
        }
    }

    private static byte[] payload(String name) {
        return "http".equals(name) ? xxeHttp : xxeFile;
    }

    private static byte[] bareXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>"
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] read(String fileName) throws IOException {
        try (InputStream is = TestXXEInXML.class.getResourceAsStream("/test-documents/" + fileName)) {
            assertTrue(is != null, "missing fixture " + fileName);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(is, bos);
            return bos.toByteArray();
        }
    }

    private static Path injectZip(String fileName, byte[] payload) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(
                TestXXEInXML.class.getResourceAsStream("/test-documents/" + fileName))) {
            return injectZippedXMLs(tis.getPath(), payload);
        }
    }

    private static long parseBytes(String fileName, byte[] bytes) throws Exception {
        return parseBytes(fileName, bytes, new Metadata());
    }

    private static long parseBytes(String fileName, byte[] bytes, Metadata metadata)
            throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            return parseCounting(fileName, tis, metadata);
        }
    }

    private static long parsePath(String fileName, Path path) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(path)) {
            return parseCounting(fileName, tis, new Metadata());
        }
    }

    private static long parseCounting(String fileName, TikaInputStream tis, Metadata metadata)
            throws Exception {
        CountingHandler handler = new CountingHandler();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        AUTO_DETECT_PARSER.parse(tis, handler, metadata, new ParseContext());
        return handler.count;
    }

    // stops a runaway expansion instead of waiting for the timeout
    private static class CountingHandler extends DefaultHandler {
        long count = 0;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            count += length;
            if (count > MAX_CHARS) {
                throw new SAXException("harness: more than " + MAX_CHARS + " characters expanded");
            }
        }
    }
}
