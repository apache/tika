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
package org.apache.tika.parser.microsoft.onenote;


import static org.apache.tika.parser.microsoft.onenote.OneNoteParser.ONE_NOTE_PREFIX;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStorePackage;
import org.apache.tika.sax.ToMarkdownContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class OneNoteParserTest extends TikaTest {

    private static final String[][] FUZZ_RESOURCES = {
            {"testOneNote-fuzz1.one", "Missing dependent revision"},
            {"testOneNote-fuzz2.one", "unitMagic must always be"},
            {"testOneNote-fuzz3.one", "Invalid GUID string"}
    };

    private static Metadata parseFuzzResource(String resource, ParseContext context)
            throws Exception {
        InputStream input = OneNoteParserTest.class
                .getResourceAsStream("/test-documents/" + resource);
        assertNotNull(input, resource);
        Metadata metadata = new Metadata();
        try (InputStream stream = input; TikaInputStream tis = TikaInputStream.get(stream)) {
            new OneNoteParser().parse(tis, new ToTextContentHandler(), metadata, context);
        }
        return metadata;
    }

    @Test
    public void testSectionGuidIsExtractedFromClassicHeader() throws Exception {
        Metadata metadata = new Metadata();
        try (InputStream input = getClass().getResourceAsStream("/test-documents/testOneNote1.one");
             TikaInputStream tis = TikaInputStream.get(input)) {
            new OneNoteParser().parse(tis, new ToTextContentHandler(), metadata,
                    new ParseContext());
        }
        String guid = metadata.get(OneNote.SECTION_GUID);
        assertNotNull(guid);
        assertTrue(guid.matches("\\{[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-"
                + "[0-9A-F]{4}-[0-9A-F]{12}\\}"));
    }

    @Test
    public void testClassicGuidValues() throws Exception {
        Metadata metadata = new Metadata();
        getText("testOneNote2.one", metadata);
        assertEquals("{D03D94F3-AFB4-484F-A5ED-B93EBA2806B9}",
                metadata.get(OneNote.SECTION_GUID));
        assertArrayEquals(new String[] {"{0AD2F2F8-F7C0-4301-82DF-064544DD31E5}",
                "{E1FDD004-97A6-49B8-A9C9-BB781D0C5423}"},
                metadata.getValues(OneNote.PAGE_GUIDS));
        assertArrayEquals(new String[] {"{135E9FB6-B9E5-42B9-B185-2A72495D0A05}",
                "{815B61E9-5C57-43CF-BCDD-9D57A0FEDADB}"},
                metadata.getValues(OneNote.PAGE_SERIES_GUIDS));
        assertArrayEquals(new String[] {"{52ADC330-8420-4DDC-A381-C8C30ECD8739}",
                "{EE2E0DFD-9B11-4E22-AB62-53C667EF58D1}"},
                metadata.getValues(OneNote.PAGE_NODE_GUIDS));
    }

    @Test
    public void testClassicGuidsSkipVersionHistoryPages() throws Exception {
        Metadata metadata = new Metadata();
        getText("test-tika-3970-dupetext.one", metadata);
        assertArrayEquals(new String[] {"{70D3B6E3-B023-4A5E-AC96-6FCE9D66F63C}"},
                metadata.getValues(OneNote.PAGE_GUIDS));
        assertArrayEquals(new String[] {"{BB5DE1E3-25FB-4584-A37F-69D332F40BF7}"},
                metadata.getValues(OneNote.PAGE_SERIES_GUIDS));
    }

    @Test
    public void testFsshttpbGuidValuesAreSorted() throws Exception {
        Metadata metadata = new Metadata();
        getText("testOneNoteFromOffice365.one", metadata);
        assertEquals("{54807CE9-568A-4883-B863-BF97F35C844F}",
                metadata.get(OneNote.SECTION_GUID));
        assertArrayEquals(new String[] {"{9376CA69-2A0D-440E-BFC9-A728C597ED93}",
                "{D1962227-01A7-4F73-A1F5-410DA0C519CC}"},
                metadata.getValues(OneNote.PAGE_GUIDS));
        assertArrayEquals(new String[] {"{01B28DC5-49F4-4F05-8E10-131236FAAB82}",
                "{429CB628-77C7-4600-BE0C-83166E392792}"},
                metadata.getValues(OneNote.PAGE_SERIES_GUIDS));
    }

    @Test
    public void testEmitSectionGuidSkipsNullAndNil() {
        Metadata metadata = new Metadata();
        OneNoteParser.emitSectionFileGuid(header(null), metadata);
        OneNoteParser.emitSectionFileGuid(header(GUID.nil()), metadata);
        assertNull(metadata.get(OneNote.SECTION_GUID));

        GUID fileGuid = new GUID(new int[] {
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x08,
                0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00
        });
        OneNoteParser.emitSectionFileGuid(header(fileGuid), metadata);
        assertEquals(fileGuid.toString(), metadata.get(OneNote.SECTION_GUID));
    }

    private static OneNoteHeader header(GUID guidFile) {
        OneNoteHeader header = new OneNoteHeader();
        header.guidFile = guidFile;
        return header;
    }

    @Test
    public void testFuzzerRegressionInputsFallBackToLegacyDump() throws Exception {
        // a structural failure falls back to the legacy string dump; the human warning names
        // no exception text -- the throwable is recorded separately, under the policy
        for (String[] resource : FUZZ_RESOURCES) {
            Metadata metadata = parseFuzzResource(resource[0], new ParseContext());
            String[] warnings =
                    metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
            assertTrue(Arrays.stream(warnings)
                            .anyMatch(w -> w.contains("falling back to legacy text dump")
                                    && !w.contains(resource[1])),
                    () -> resource[0] + ": " + Arrays.toString(warnings));
            assertTrue(Arrays.stream(warnings)
                            .anyMatch(w -> w.contains(resource[1]) && w.contains("\tat ")),
                    () -> resource[0] + ": " + Arrays.toString(warnings));
        }
    }

    @Test
    public void testFallbackWarningHonorsExceptionReporting() throws Exception {
        ParseContext context = new ParseContext();
        context.set(ExceptionReporting.class, new ExceptionReporting(
                ExceptionReporting.Level.REDACTED, ExceptionReporting.UNLIMITED));
        for (String[] resource : FUZZ_RESOURCES) {
            Metadata metadata = parseFuzzResource(resource[0], context);
            String[] warnings =
                    metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
            for (String warning : warnings) {
                assertFalse(warning.contains(resource[1]),
                        () -> resource[0] + ": " + Arrays.toString(warnings));
                assertFalse(warning.contains("\tat "),
                        () -> resource[0] + ": " + Arrays.toString(warnings));
            }
            assertTrue(Arrays.stream(warnings)
                            .anyMatch(w -> w.contains("falling back to legacy text dump")),
                    () -> resource[0] + ": " + Arrays.toString(warnings));
        }
    }

    @Test
    public void testNoContentFsshttpbWalkFallsBackToLegacyDump(@TempDir Path tempDir)
            throws Exception {
        Path file = tempDir.resolve("empty-walk.one");
        // trailing newline: the dump excludes the file's final byte from scanning
        Files.write(file,
                "recoverable legacy dump text\n".getBytes(StandardCharsets.US_ASCII));
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer),
                metadata, new ParseContext());
        xhtml.startDocument();
        try (OneNoteDirectFileResource resource =
                new OneNoteDirectFileResource(file.toFile())) {
            // fresh package = the walk completed without emitting anything
            OneNoteParser.legacyFallbackIfNoContent(new MSOneStorePackage(), metadata, xhtml,
                    resource, new ParseContext());
            assertTrue(writer.toString().contains("recoverable legacy dump text"));
            assertTrue(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                    .contains("produced no content"));

            // pkg == null means the exception path already dumped - no second dump
            StringWriter exceptionPath = new StringWriter();
            XHTMLContentHandler xhtml2 = new XHTMLContentHandler(
                    new ToTextContentHandler(exceptionPath), metadata, new ParseContext());
            xhtml2.startDocument();
            OneNoteParser.legacyFallbackIfNoContent(null, metadata, xhtml2, resource,
                    new ParseContext());
            xhtml2.endDocument();
            assertTrue(exceptionPath.toString().isBlank());
        }
        xhtml.endDocument();
    }

    @Test
    public void testTruncatedFileFallsBackToLegacyDump(@TempDir Path tempDir) throws Exception {
        byte[] full;
        try (InputStream is = getClass()
                .getResourceAsStream("/test-documents/testOneNote1.one")) {
            full = is.readAllBytes();
        }
        // keep the 1024-byte header plus a sliver of content so the root file node list
        // is unreachable
        Path truncated = tempDir.resolve("truncated.one");
        Files.write(truncated, Arrays.copyOf(full, 2048));

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            new OneNoteParser().parse(tis, new ToTextContentHandler(), metadata,
                    new ParseContext());
        }
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("falling back to legacy text dump")));
    }

    @Test
    public void testUncommittedFileNodesAreIgnored(@TempDir Path tempDir) throws Exception {
        byte[] full;
        try (InputStream is = getClass()
                .getResourceAsStream("/test-documents/testOneNote1.one")) {
            full = is.readAllBytes();
        }
        // 4950 is just past the last node the transaction log commits for file node list
        // 0x12; plant a header there with the reserved bit clear, as leftover bytes have
        byte[] planted = full.clone();
        ByteBuffer.wrap(planted, 4950, 4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x123 | (16 << 10));

        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("planted.one"), planted, metadata);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertEquals(parseToText(tempDir.resolve("full.one"), full, new Metadata()), txt);
    }

    @Test
    public void testFileNodeListIdsBelowSpecMinimum(@TempDir Path tempDir) throws Exception {
        byte[] full;
        try (InputStream is = getClass()
                .getResourceAsStream("/test-documents/testOneNote1.one")) {
            full = is.readAllBytes();
        }
        // Aspose and OneNote's .onetoc2 files number lists from 2, below the spec's 0x10
        byte[] renumbered = full.clone();
        ByteBuffer bb = ByteBuffer.wrap(renumbered).order(ByteOrder.LITTLE_ENDIAN);
        int lists = 0;
        for (int i = 0; i + 16 <= renumbered.length; i++) {
            if (bb.getLong(i) == 0xA4567AB1F5F7F4C4L) {
                bb.putInt(i + 8, bb.getInt(i + 8) - 0x0E);
                lists++;
            }
        }
        assertEquals(19, lists);

        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("renumbered.one"), renumbered, metadata);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertEquals(parseToText(tempDir.resolve("full.one"), full, new Metadata()), txt);
    }

    @Test
    public void testOneNote2007ParagraphText(@TempDir Path tempDir) throws Exception {
        byte[] full = readTestDocument("testOneNote4.one");
        // OneNote 2007 stores single-byte paragraph text under 0x1C001C40, not TextExtendedAscii
        byte[] renumbered = full.clone();
        ByteBuffer bb = ByteBuffer.wrap(renumbered).order(ByteOrder.LITTLE_ENDIAN);
        int props = 0;
        for (int i = 0; i + 4 <= renumbered.length; i++) {
            if (bb.getInt(i) == 0x1C003498) {
                bb.putInt(i, 0x1C001C40);
                props++;
            }
        }
        assertTrue(props > 0);

        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("renumbered.one"), renumbered, metadata);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("way too much information about poptarts to handle.", txt);
        assertEquals(parseToText(tempDir.resolve("full.one"), full, new Metadata()), txt);
    }

    @Test
    public void testOneNote2003SectionFallsBackToLegacyDump(@TempDir Path tempDir)
            throws Exception {
        byte[] bytes = readTestDocument("testOneNote4.one");
        // OneNote 2003 sections set guidLegacyFileVersion (offset 32), which 2010+ leaves nil
        bytes[32] = 1;
        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("2003.one"), bytes, metadata);
        assertEquals("OneNote 2003 section; falling back to legacy text dump",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("poptarts", txt);
    }

    @Test
    public void testUnwrittenRevisionManifestList(@TempDir Path tempDir) throws Exception {
        byte[] bytes = readTestDocument("testOneNote4.one");
        // the second object space's RevisionManifestListReferenceFND
        zeroReference(bytes, 5576, 3, 0x10);
        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("unwritten.one"), bytes, metadata);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("Section3HeaderTitle", txt);
        assertNotContained("poptarts", txt);
    }

    @Test
    public void testUnwrittenFileData(@TempDir Path tempDir) throws Exception {
        byte[] bytes = readTestDocument("testOneNoteEmbeddedWordDoc.one");
        zeroReference(bytes, 31176, 3, 0x94);
        Metadata metadata = new Metadata();
        String txt = parseToText(tempDir.resolve("unwritten.one"), bytes, metadata);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("Dude this is a super cool embedded doc.docx", txt);
    }

    /** Sets the compressed reference of the file node at nodeOffset to fcrZero. */
    private static void zeroReference(byte[] bytes, int nodeOffset, int refLength,
                                      int fileNodeId) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(fileNodeId, bb.getInt(nodeOffset) & 0x3FF);
        Arrays.fill(bytes, nodeOffset + 4, nodeOffset + 4 + refLength, (byte) 0);
    }

    private byte[] readTestDocument(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test-documents/" + name)) {
            return is.readAllBytes();
        }
    }

    private static String parseToText(Path path, byte[] bytes, Metadata metadata)
            throws Exception {
        Files.write(path, bytes);
        ToTextContentHandler handler = new ToTextContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(path)) {
            new OneNoteParser().parse(tis, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    /** The sample document OneNote 2013 creates automatically. */
    @Test
    public void testOneNote2013Doc1() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote1.one", metadata);
        assertNoJunk(txt);
        // pictures are <img>, not paragraphs of their file names
        assertNotContained("Untitled picture.png", txt);
        String xml = getXML("testOneNote1.one").xml;
        assertContains("<img src=\"embedded:Untitled picture.png\" alt=\"Play button\" />", xml);
        assertContains("alt=\"Pages view: \nSummer vacation\nCamping", xml);
        // the link text is the run flagged as hyperlink after the field code
        assertContains("<p><a href=\"http://o15.officeredir.microsoft.com/r/" +
                "rlidOneNote15Tutorial3?clid=1033\">Search notes instantly</a></p>", xml);
        ToMarkdownContentHandler md = new ToMarkdownContentHandler();
        try (InputStream input = getClass().getResourceAsStream("/test-documents/testOneNote1.one");
             TikaInputStream tis = TikaInputStream.get(input)) {
            new OneNoteParser().parse(tis, md, new Metadata(), new ParseContext());
        }
        assertContains("![Play button](<embedded:Untitled picture.png>)\n", md.toString());

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertContains("Olya Veselova", authors);
        assertContains("Microsoft", authors);
        assertNotContained("Microsoft\u0000", authors);
        assertContains("Scott", authors);
        assertContains("Scott H. W. Snyder", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        // roles come from the reference an author object is reached through
        assertEquals(List.of("Microsoft"), mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("Microsoft", originalAuthors);
        assertEquals(4, originalAuthors.size());

        assertEquals(Instant.ofEpochSecond(1336059427),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1383613114000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1446572147),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testOneNote2013Doc2() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2.one", metadata);
        assertContains("wow this is neat", txt);
        // the page's current revision; earlier ones held "neat info about totally killin it bro"
        assertContains("tubular", txt);
        assertNotContained("neat info about", txt);
        assertContains("Section1TextArea1", txt);
        assertContains("Section1HeaderTitle", txt);
        assertContains("Section1TextArea2", txt);
        assertNoJunk(txt);

        // metadata describes the current revisions; earlier authors need includePageHistory
        assertArrayEquals(new String[]{"Microsoft", "ndipiazza"},
                metadata.getValues(TikaCoreProperties.CREATOR));

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza", mostRecentAuthors);
        assertContains("Microsoft", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("Microsoft", originalAuthors);
        assertContains("ndipiazza", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1343257661),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426629000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426628),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testPropertyValueLimitScalesWithFileSize() {
        assertEquals(100_000, OneNotePtr.propertyValueLimit(6_000));
        // the densest section seen: 963,456 bytes, 74,498 values; then scaled up 10x
        assertTrue(OneNotePtr.propertyValueLimit(9_634_560) > 744_980);
    }

    @Test
    public void testTruncatedHeader(@TempDir Path tempDir) throws Exception {
        byte[] file;
        try (InputStream input = getResourceAsStream("/test-documents/testOneNote1.one")) {
            file = input.readNBytes(64);
        }
        Path truncated = Files.write(tempDir.resolve("truncated.one"), file);
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            TikaException e = assertThrows(TikaException.class, () -> new OneNoteParser()
                    .parse(tis, new ToTextContentHandler(), new Metadata(), new ParseContext()));
            assertContains("Truncated OneNote file: 64 bytes", e.getMessage());
        }
    }

    @Test
    public void testNoTimestampsWithoutDates() {
        OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                new OneNoteDocument(), null, null, null, new ParseContext(), null);
        Metadata metadata = new Metadata();
        OneNoteParser.setWalkMetadata(walker, metadata);
        assertEquals(0, metadata.size());
    }

    @Test
    public void testOriginalAuthorsAreCreators() {
        OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                new OneNoteDocument(), null, null, null, new ParseContext(), null);
        walker.getAuthors().add("Editor");
        walker.getOriginalAuthors().add("Writer");
        Metadata metadata = new Metadata();
        OneNoteParser.setWalkMetadata(walker, metadata);
        assertArrayEquals(new String[]{"Editor", "Writer"},
                metadata.getValues(TikaCoreProperties.CREATOR));
        assertEquals("Writer", metadata.get(OneNote.ORIGINAL_AUTHORS));
    }

    @Test
    public void testPageHistory() throws Exception {
        ParseContext context = new ParseContext();
        OneNoteParserConfig config = new OneNoteParserConfig();
        config.setIncludePageHistory(true);
        context.set(OneNoteParserConfig.class, config);
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2.one", metadata, context);
        assertContains("tubular", txt);
        assertContains("neat info about totally killin it bro", txt);
        // and the earlier revisions' metadata
        assertArrayEquals(new String[]{"Microsoft", "Olya Veselova", "Scott",
                "Scott H. W. Snyder", "ndipiazza"}, metadata.getValues(TikaCoreProperties.CREATOR));
        assertEquals("1336059427", metadata.get(OneNote.CREATION_TIMESTAMP));
        // only in a saved page version (under a version-history reference)
        assertNotContained("There are", getText("testOneNote3.one"));
        assertContains("There are", getText("testOneNote3.one", new Metadata(), context));

        Parser parser = TikaLoader.load(getConfigPath(OneNoteParserTest.class,
                "tika-config-onenote-page-history.json")).loadAutoDetectParser();
        assertContains("neat info about totally killin it bro",
                getText("testOneNote2.one", parser));
    }

    @Test
    public void testOneNote2013Doc3() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote3.one", metadata);
        // earlier revisions of these paragraphs held "awesome information about sports or some
        // crap like that." and "Quit doing horrible things to me. Dang you."
        assertContains("neat info about totally killin it bro", txt);
        assertContains("Fun", txt);
        assertNotContained("awesome information", txt);
        assertNotContained("Quit doing", txt);
        assertContains("Section2TextArea1", txt);
        assertContains("Section2HeaderTitle", txt);
        assertContains("Section2TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertNotContained("Olya Veselova", authors);
        assertNotContained("Microsoft", authors);
        assertNotContained("Scott", authors);
        assertNotContained("Scott H. W. Snyder", authors);
        assertContains("ndipiazza", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza", mostRecentAuthors);
        assertNotContained("Microsoft", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertNotContained("Microsoft", originalAuthors);
        assertContains("ndipiazza", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1574426349),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426623000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426624),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testOneNote2013Doc4() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote4.one", metadata);

        assertContains("way too much information about poptarts to handle.", txt);
        assertContains("Section3TextArea1", txt);
        assertContains("Section3HeaderTitle", txt);
        assertContains("Section3TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertNotContained("Olya Veselova", authors);
        assertNotContained("Microsoft", authors);
        assertNotContained("Scott", authors);
        assertNotContained("Scott H. W. Snyder", authors);
        assertContains("ndipiazza", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza", mostRecentAuthors);
        assertNotContained("Microsoft", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertNotContained("Microsoft", originalAuthors);
        assertContains("ndipiazza", mostRecentAuthors);

        String[] pageGuids = metadata.getValues(OneNote.PAGE_GUIDS);
        assertNotNull(pageGuids);
        assertTrue(pageGuids.length > 0);
        assertEquals(pageGuids.length, Arrays.stream(pageGuids).distinct().count());
        String[] pageSeriesGuids = metadata.getValues(OneNote.PAGE_SERIES_GUIDS);
        assertNotNull(pageSeriesGuids);
        assertTrue(pageSeriesGuids.length > 0);

        assertEquals(Instant.ofEpochSecond(1574426385),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426548000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426547),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testOneNote2016() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2016.one", metadata);

        assertContains("So good", txt);
        assertContains("This is one note 2016", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertContains("nicholas dipiazza", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("nicholas dipiazza", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("nicholas dipiazza", originalAuthors);

        assertEquals(Instant.ofEpochSecond(1576107472),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1576107481000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1576107480),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testOneNote2007OrEarlier() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2007OrEarlier.one", metadata);

        // utf-16 LE text
        assertContains(
                "One note is the application.  The notebooks are the files within the application" +
                        ".  " +
                        "Each notebook can have an unlimited amount of sections and pages.  To " +
                        "create a new notebook, go to file, new, computer, " +
                        "and name it.  It will go to my documents, oneNote Notebooks folder.  The" +
                        " notebook doesn't close and you don't have to save.  " +
                        "If it closes, you can go back to it and it will open at the same place " +
                        "you left off.  If you are offline and the notebook is " +
                        "being stored on a sharepoint site, you can work on it and it will sync " +
                        "when you go back online.", txt);
        // ascii text
        assertContains("Correlation between Outlook and OneNote", txt);
    }

    @Test
    public void testFsshttpbTablesAndListsRetainXhtmlStructure() throws Exception {
        String xml = getXML("testOneNoteTablesLists.one", new OneNoteParser()).xml;
        assertEquals(1, StringUtils.countMatches(xml, "<table>"), xml);
        assertEquals(2, StringUtils.countMatches(xml, "<tr>"), xml);
        assertEquals(8, StringUtils.countMatches(xml, "<td"), xml);
        assertTrue(xml.matches("(?s).*<table>.*<td><p>Table cell 1a</p>.*" +
                "<td><p>Table cell 4b</p>.*</table>.*"), xml);
        assertEquals(3, StringUtils.countMatches(xml, "<ul>"), xml);
        assertTrue(xml.matches("(?s).*<li><p>Bullet 2</p>\\s*<ul>\\s*<li>" +
                "<p>Indented bullet</p>\\s*<ul>\\s*<li><p>Indented square</p>.*"), xml);
        assertEquals(2, StringUtils.countMatches(xml, "<ol type=\"1\">"), xml);
        assertTrue(xml.matches("(?s).*<li><p>Number 2</p>\\s*<ol type=\"a\">\\s*<li>" +
                "<p>Letter a</p>\\s*<ol type=\"i\">\\s*<li><p>Numeral I</p>.*"), xml);
        assertTrue(xml.matches("(?s).*<ol type=\"1\">\\s*<li><p>Outdented number</p>.*"), xml);
        assertEquals(9, StringUtils.countMatches(xml, "<li>"), xml);
    }

    @Test
    public void testOneNote2007ListStructure() throws Exception {
        String xml = getXML("testOneNote2007OrEarlier.one", new OneNoteParser()).xml;
        assertEquals(2, StringUtils.countMatches(xml, "<ul>"), xml);
        assertEquals(6, StringUtils.countMatches(xml, "<li>"), xml);
    }

    @Test
    public void testOneNoteEmbeddedWordDoc() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOneNoteEmbeddedWordDoc.one");

        assertTrue(metadataList.stream().anyMatch(
                ml -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(
                        ml.get("Content-Type"))));
    }

    @Test
    public void testOneNoteEmbeddedImage() throws Exception {
        List<byte[]> embedded = new ArrayList<>();
        List<String> embeddedTypes = new ArrayList<>();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) throws IOException {
                embedded.add(stream.readAllBytes());
                embeddedTypes.add(metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
            }
        });
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testOneNoteEmbeddedImage.one")) {
            new OneNoteParser().parse(tis, new ToTextContentHandler(), new Metadata(), context);
        }

        assertEquals(1, embedded.size());
        assertTrue(embedded.stream().anyMatch(bytes -> bytes.length > 1000),
                () -> "embedded sizes: " + embedded.stream().map(bytes -> bytes.length).toList());
        assertTrue(embeddedTypes.contains("INLINE"));
    }

    @Test
    public void testOneNoteEmbeddedImageRecursiveMetadata() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOneNoteEmbeddedImage.one");

        assertEquals(2, metadataList.size());
        Metadata embedded = metadataList.get(1);
        assertEquals("INLINE", embedded.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertNotNull(embedded.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID));
    }

    @Test
    public void testHeaderGuidDeserializationRejectsTruncatedFile(@TempDir Path tempDir)
            throws Exception {
        // fewer bytes than one GUID: reading must fail, not silently decode 0xFF filler
        Path shortFile = tempDir.resolve("short.one");
        Files.write(shortFile, new byte[10]);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(shortFile.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            IOException exception = assertThrows(IOException.class, ptr::deserializeHeader);
            assertTrue(exception.getMessage().contains("while reading a GUID"));
        }
    }

    @Test
    public void testPropertyValueBudgetCoversTheWholeDocument(@TempDir Path tempDir)
            throws Exception {
        Path emptyFile = tempDir.resolve("empty");
        Files.write(emptyFile, ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0)
                .putLong(-1).putInt(0).putLong(OneNotePtr.FOOTER_CONST).array());
        Method reservePropertyCount = OneNotePtr.class.getDeclaredMethod(
                "reservePropertyCount", long.class, String.class);
        reservePropertyCount.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(emptyFile.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            OneNotePtr copy = new OneNotePtr(ptr);
            reservePropertyCount.invoke(ptr, 100_000L, "test");
            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> reservePropertyCount.invoke(copy, 1L, "test"));
            assertTrue(exception.getCause() instanceof TikaMemoryLimitException);

            // a new file node list does not refill it
            ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr());
            exception = assertThrows(InvocationTargetException.class,
                    () -> reservePropertyCount.invoke(ptr, 1L, "test"));
            assertTrue(exception.getCause() instanceof TikaMemoryLimitException);
        }
    }

    @Test
    public void testFileNodeCycleIsReportedAndStopsTraversal(@TempDir Path tempDir) throws Exception {
        FileNode fileNode = new FileNode().setGosid(ExtendedGUID.nil());
        fileNode.childFileNodeList.setFileNodeListHeader(new FileNodeListHeader(0,
                FileNodeListHeader.UNIT_MAGIC_CONSTANT, 0x10, 0));
        fileNode.childFileNodeList.children.add(fileNode);
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        Path emptyFile = Files.createFile(tempDir.resolve("empty"));
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(emptyFile.toFile())) {
            OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                    new OneNoteDocument(), dif,
                    new XHTMLContentHandler(new ToTextContentHandler(), metadata, parseContext),
                    metadata, parseContext, null);
            walker.walkFileNode(fileNode, null);
        }
        assertEquals(1, Arrays.stream(
                        metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(warning -> warning.contains("file-node cycle detected"))
                .count());
    }

    @Test
    public void testDeepFileNodeChainIsDepthCapped(@TempDir Path tempDir) throws Exception {
        // a long acyclic chain slips past the cycle guard - the depth cap must stop it
        // before the recursion can overflow the stack
        FileNode root = new FileNode().setGosid(ExtendedGUID.nil());
        FileNode current = root;
        for (int i = 0; i < 600; i++) {
            FileNode child = new FileNode().setGosid(ExtendedGUID.nil());
            current.childFileNodeList.setFileNodeListHeader(new FileNodeListHeader(0,
                    FileNodeListHeader.UNIT_MAGIC_CONSTANT, 0x10, 0));
            current.childFileNodeList.children.add(child);
            current = child;
        }
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        Path emptyFile = Files.createFile(tempDir.resolve("empty"));
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(emptyFile.toFile())) {
            OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                    new OneNoteDocument(), dif,
                    new XHTMLContentHandler(new ToTextContentHandler(), metadata, parseContext),
                    metadata, parseContext, null);
            walker.walkFileNode(root, null);
        }
        assertEquals(1, Arrays.stream(
                        metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(warning -> warning.contains("exceeded depth limit"))
                .count());
    }

    /**
     * Writes one minimal 56-byte file-node-list fragment: header, a single baseType-2 node
     * whose child list is at {childStp, childCb}, a terminator, a nil next-fragment
     * reference and the footer.
     */
    private static void writeFileNodeListBlock(ByteBuffer buf, long childStp, int childCb) {
        // id=0x10, size=16 (node header + 8-byte stp + 4-byte cb), stpFormat=0, cbFormat=0,
        // baseType=2, reserved=1
        int fileNodeHeader = 0x10 | (16 << 10) | (2 << 27) | (1 << 31);
        buf.putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0)
                .putInt(fileNodeHeader).putLong(childStp).putInt(childCb)
                .putInt(0)
                .putLong(-1).putInt(0)
                .putLong(OneNotePtr.FOOTER_CONST);
    }

    @Test
    public void testEncryptionKeyNodeThrowsEncrypted(@TempDir Path tempDir) throws Exception {
        // one fragment holding an ObjectDataEncryptionKeyV2FNDX (baseType 1)
        int fileNodeHeader = 0x7C | (16 << 10) | (1 << 27) | (1 << 31);
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0)
                .putInt(fileNodeHeader).putLong(0).putInt(56)
                .putInt(0)
                .putLong(-1).putInt(0)
                .putLong(OneNotePtr.FOOTER_CONST);
        Path encrypted = tempDir.resolve("encrypted");
        Files.write(encrypted, buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(encrypted.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            assertThrows(EncryptedDocumentException.class,
                    () -> ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr()));
        }
    }

    @Test
    public void testOneNote2007RawDataObjectsAreNotPropertySets(@TempDir Path tempDir)
            throws Exception {
        assertFalse(declaredJcid(tempDir, 0x16).isObjectSpaceObjectPropSet());
        assertFalse(declaredJcid(tempDir, 0x17).isObjectSpaceObjectPropSet());
        assertFalse(declaredJcid(tempDir, 0x2A).isObjectSpaceObjectPropSet());
        assertTrue(declaredJcid(tempDir, 0x0E).isObjectSpaceObjectPropSet());
    }

    @Test
    public void testObjectRevisionReplacesDeclaredProperties(@TempDir Path tempDir)
            throws Exception {
        // ObjectSpaceObjectPropSet: no OIDs or OSIDs, then RichEditTextUnicode "new"
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x80000000).putShort((short) 1).putInt(0x1C001C22).putInt(6);
        buf.put("new".getBytes(StandardCharsets.UTF_16LE));
        Path propSet = Files.write(tempDir.resolve("revision"), buf.array());
        ExtendedGUID declared = new ExtendedGUID(GUID.nil(), 11);
        OneNoteDocument document = new OneNoteDocument();
        JCID richText = new JCID();
        richText.loadFrom32BitIndex(0x0006000E);
        document.objectJcids.put(declared, richText);
        Method postprocess = OneNotePtr.class.getDeclaredMethod("postprocessObjectRevision",
                FileNode.class, FileNodePtr.class);
        postprocess.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(propSet.toFile())) {
            OneNotePtr ptr = new OneNotePtr(document, dif);
            FileNode revision = objectRevision(declared, buf.capacity());
            postprocess.invoke(ptr, revision, new FileNodePtr());
            assertEquals(OneNotePropertyEnum.RichEditTextUnicode,
                    document.propertySet(revision).rgPridsData.get(0).propertyId.propertyEnum);
            assertEquals(0x0E, OneNoteTreeWalker.jcidIndex(revision));
            assertNotNull(document.guidToObject.get(declared));
            // a revision of an object never declared has no JCID to take
            FileNode orphan = objectRevision(new ExtendedGUID(GUID.nil(), 12), buf.capacity());
            postprocess.invoke(ptr, orphan, new FileNodePtr());
            assertNull(document.propertySet(orphan));
        }
    }

    private static FileNode objectRevision(ExtendedGUID oid, int length) {
        FileNode node = new FileNode();
        node.subType.objectRevisionWithRefCountFNDX.oid = new CompactID();
        node.subType.objectRevisionWithRefCountFNDX.oid.guid = oid;
        node.ref = new FileChunkReference(0, length);
        return node;
    }

    @Test
    public void testEmptyGlobalIdTableUsesDependentRevision(@TempDir Path tempDir)
            throws Exception {
        GUID guid = new GUID(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        OneNoteDocument document = new OneNoteDocument();
        ExtendedGUID dependentId = new ExtendedGUID(guid, 1);
        Revision dependent = new Revision();
        dependent.globalId.put(0L, guid);
        document.revisionMap.put(dependentId, dependent);
        // an empty table in between, as a chain of revisions with no new IDs writes
        ExtendedGUID middleId = new ExtendedGUID(guid, 2);
        Revision middle = new Revision();
        middle.dependent = dependentId;
        document.revisionMap.put(middleId, middle);
        Revision current = new Revision();
        current.dependent = middleId;
        document.revisionMap.put(document.currentRevision, current);
        // CompactID n=11, guidIndex=0
        Path compactId = Files.write(tempDir.resolve("compactId"), new byte[]{11, 0, 0, 0});
        Method deserialize = OneNotePtr.class.getDeclaredMethod("deserializeCompactID");
        deserialize.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(compactId.toFile())) {
            CompactID id = (CompactID) deserialize.invoke(new OneNotePtr(document, dif));
            assertEquals(new ExtendedGUID(guid, 11), id.guid);
        }
    }

    @Test
    public void testTransactionLogFragmentWithPadding(@TempDir Path tempDir) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        // fragment 1 (cb 32): two entries, nextFragment, then 4 bytes of padding
        buf.putInt(5).putInt(3).putInt(1).putInt(0).putLong(32).putInt(28).putInt(-1);
        // fragment 2 (cb 28): two entries, nextFragment (nil)
        buf.putInt(7).putInt(4).putInt(1).putInt(0).putLong(-1).putInt(0);
        Path log = Files.write(tempDir.resolve("log"), buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(log.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            assertEquals(Map.of(5L, 3L, 7L, 4L),
                    ptr.deserializeTransactionLog(new FileChunkReference(0, 32), 2));
        }
    }

    @Test
    public void testRevisionRolesHaveNilDefaultContext(@TempDir Path tempDir) throws Exception {
        OneNoteDocument document = new OneNoteDocument();
        // RevisionManifestStart4FND (OneNote 2007): rid, ridDependent, timeCreation,
        // revisionRole, odcsDefault
        ByteBuffer start = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN);
        start.putInt(0x1B | 58 << 10 | 1 << 31).put(new byte[15]).put((byte) 7).putInt(1)
                .put(new byte[20]).putLong(0).putInt(1).putShort((short) 0);
        FileNode manifest = deserializeFileNode(tempDir, "start4", start, document);
        // RevisionRoleDeclarationFND: rid, revisionRole
        ByteBuffer declaration = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        declaration.putInt(0x5C | 28 << 10 | 1 << 31).put(new byte[15]).put((byte) 8).putInt(1)
                .putInt(4);
        FileNode role = deserializeFileNode(tempDir, "declaration", declaration, document);
        List<FileNode> list = List.of(manifest, role);
        assertEquals(document.currentRevision,
                OneNoteTreeWalker.labeledRevision(list, Pair.of(1L, ExtendedGUID.nil())));
        assertEquals(role.gosid,
                OneNoteTreeWalker.labeledRevision(list, Pair.of(4L, ExtendedGUID.nil())));
    }

    @Test
    public void testCurrentRevisionKeepsItsLabelWhenGivenAnother() {
        GUID guid = new GUID(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ExtendedGUID older = new ExtendedGUID(guid, 1);
        ExtendedGUID current = new ExtendedGUID(guid, 2);
        ExtendedGUID context = new ExtendedGUID(guid, 3);
        Pair<Long, ExtendedGUID> label = Pair.of(1L, ExtendedGUID.nil());
        List<FileNode> list = new ArrayList<>(List.of(
                manifestStart(older, 1, ExtendedGUID.nil()),
                manifestStart(current, 1, ExtendedGUID.nil()),
                // the current revision also labeled in another context
                roleDeclaration(current, 1, context)));
        assertEquals(current, OneNoteTreeWalker.labeledRevision(list, label));
        assertEquals(current, OneNoteTreeWalker.labeledRevision(list, Pair.of(1L, context)));
        // a later declaration moves the label back to the older revision
        list.add(roleDeclaration(older, 1, ExtendedGUID.nil()));
        assertEquals(older, OneNoteTreeWalker.labeledRevision(list, label));
        assertNull(OneNoteTreeWalker.labeledRevision(list, Pair.of(2L, ExtendedGUID.nil())));
    }

    private static FileNode manifestStart(ExtendedGUID rid, long role, ExtendedGUID context) {
        FileNode node = new FileNode();
        node.id = FndStructureConstants.RevisionManifestStart7FND;
        node.gosid = rid;
        node.gctxid = context;
        node.subType.revisionManifest.revisionRole = role;
        return node;
    }

    private static FileNode roleDeclaration(ExtendedGUID rid, long role, ExtendedGUID context) {
        FileNode node = new FileNode();
        node.id = FndStructureConstants.RevisionRoleAndContextDeclarationFND;
        node.gosid = rid;
        node.gctxid = context;
        node.subType.revisionRoleDeclaration.revisionRole = role;
        return node;
    }

    private static FileNode deserializeFileNode(Path tempDir, String name, ByteBuffer node,
                                                OneNoteDocument document) throws Exception {
        Path file = Files.write(tempDir.resolve(name), node.array());
        Method deserialize = OneNotePtr.class.getDeclaredMethod("deserializeFileNode",
                FileNode.class, FileNodePtr.class);
        deserialize.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            FileNode fileNode = new FileNode();
            deserialize.invoke(new OneNotePtr(document, dif), fileNode, new FileNodePtr());
            return fileNode;
        }
    }

    @Test
    public void testObjectsResolveWithinTheirRevision() {
        GUID guid = new GUID(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ExtendedGUID base = new ExtendedGUID(guid, 1);
        ExtendedGUID current = new ExtendedGUID(guid, 2);
        ExtendedGUID history = new ExtendedGUID(guid, 3);
        ExtendedGUID paragraph = new ExtendedGUID(guid, 10);
        ExtendedGUID title = new ExtendedGUID(guid, 11);
        OneNoteDocument document = new OneNoteDocument();
        Revision currentRevision = new Revision();
        currentRevision.dependent = base;
        document.revisionMap.put(current, currentRevision);
        FileNodePtr baseTitle = new FileNodePtr();
        FileNodePtr baseParagraph = new FileNodePtr();
        FileNodePtr currentParagraph = new FileNodePtr();
        FileNodePtr historyParagraph = new FileNodePtr();
        document.currentRevision = base;
        document.putObject(title, baseTitle);
        document.putObject(paragraph, baseParagraph);
        document.currentRevision = current;
        document.putObject(paragraph, currentParagraph);
        // a page history snapshot written after the current revision
        document.currentRevision = history;
        document.putObject(paragraph, historyParagraph);

        assertSame(historyParagraph, document.guidToObject.get(paragraph));
        assertSame(currentParagraph, document.objectIn(current, paragraph));
        assertSame(baseTitle, document.objectIn(current, title));
        assertSame(baseParagraph, document.objectIn(base, paragraph));
    }

    @Test
    public void testRevisionWithoutContentRootKeepsItsDependencys() throws Exception {
        GUID guid = new GUID(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ExtendedGUID base = new ExtendedGUID(guid, 1);
        ExtendedGUID edit = new ExtendedGUID(guid, 2);
        ExtendedGUID root = new ExtendedGUID(guid, 10);
        OneNoteDocument document = new OneNoteDocument();
        Revision editRevision = new Revision();
        editRevision.dependent = base;
        document.revisionMap.put(edit, editRevision);
        document.revisionMap.put(base, new Revision());
        ParseContext context = new ParseContext();
        OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                document, null, new XHTMLContentHandler(new ToTextContentHandler(),
                new Metadata(), context), new Metadata(), context,
                Pair.of(1L, ExtendedGUID.nil()));
        Method contentRoots = OneNoteTreeWalker.class.getDeclaredMethod("contentRoots",
                ExtendedGUID.class, Map.class);
        contentRoots.setAccessible(true);
        assertEquals(List.of(root), contentRoots.invoke(walker, edit, Map.of(base, List.of(root))));
        assertEquals(List.of(), contentRoots.invoke(walker, edit, Map.of()));
    }

    @Test
    public void testDeepPropertySetNestingIsDepthCapped(@TempDir Path tempDir) throws Exception {
        // property sets nested inside one node recurse like file nodes do
        FileNode root = new FileNode().setGosid(ExtendedGUID.nil());
        root.propertySet = new PropertySet();
        PropertySet current = root.propertySet;
        for (int i = 0; i < 600; i++) {
            PropertyValue value = new PropertyValue();
            value.propertyId = new OneNotePropertyId(0x11L << 26);
            value.propertySet = new PropertySet();
            value.propertySet.rgPridsData = new ArrayList<>();
            current.rgPridsData = new ArrayList<>(List.of(value));
            current = value.propertySet;
        }
        Metadata metadata = walkWithEmptyFile(tempDir, root);
        assertEquals(1, Arrays.stream(
                        metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(warning -> warning.contains("exceeded depth limit"))
                .count());
    }

    @Test
    public void testObjectStreamEntriesAreChargedPerRead(@TempDir Path tempDir)
            throws Exception {
        // declarations sharing one large stream would each copy it
        int count = 60_000;
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 * count).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(count);
        Path file = Files.write(tempDir.resolve("stream"), buf.array());
        OneNoteDocument document = new OneNoteDocument();
        Revision revision = new Revision();
        revision.globalId.put(0L, GUID.nil());
        document.revisionMap.put(document.currentRevision, revision);
        Method read = OneNotePtr.class.getDeclaredMethod(
                "deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs");
        read.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            OneNotePtr ptr = new OneNotePtr(document, dif);
            ptr.reposition(new FileChunkReference(0, buf.capacity()));
            read.invoke(ptr);
            ptr.reposition(new FileChunkReference(0, buf.capacity()));
            InvocationTargetException e =
                    assertThrows(InvocationTargetException.class, () -> read.invoke(ptr));
            assertTrue(e.getCause() instanceof TikaMemoryLimitException, e.getCause().toString());
        }
    }

    @Test
    public void testOverlappingTransactionLogFragmentsFail(@TempDir Path tempDir)
            throws Exception {
        // fragment 1 (0, 60) holds 6 entries, then points into itself at (8, 52)
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(48);
        buf.putLong(8).putInt(52);
        Path log = Files.write(tempDir.resolve("log"), buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(log.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            assertThrows(TikaException.class,
                    () -> ptr.deserializeTransactionLog(new FileChunkReference(0, 60), 100));
        }
    }

    /** The JCID read from an ObjectDeclarationWithRefCountBody with the given jci. */
    private static JCID declaredJcid(Path tempDir, int jci) throws Exception {
        // CompactID (n=1, guidIndex=0), jci/odcs/flags, reserved
        ByteBuffer buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1).put((byte) 0).putShort((short) 0).putInt(jci).putShort((short) 0);
        Path body = Files.write(tempDir.resolve("body-" + jci), buf.array());
        OneNoteDocument document = new OneNoteDocument();
        Revision revision = new Revision();
        revision.globalId.put(0L, GUID.nil());
        document.revisionMap.put(document.currentRevision, revision);
        Method deserialize = OneNotePtr.class.getDeclaredMethod(
                "deserializeObjectDeclarationWithRefCountBody");
        deserialize.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(body.toFile())) {
            OneNotePtr ptr = new OneNotePtr(document, dif);
            return ((ObjectDeclarationWithRefCountBody) deserialize.invoke(ptr)).jcid;
        }
    }

    @Test
    public void testFileNodeListCycleFailsCleanly(@TempDir Path tempDir) throws Exception {
        // one fragment holding a baseType-2 node whose child list points back at itself
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        writeFileNodeListBlock(buf, 0, 56);
        Path cyclic = tempDir.resolve("cyclic");
        Files.write(cyclic, buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(cyclic.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            TikaException e = assertThrows(TikaException.class,
                    () -> ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr()));
            assertTrue(e.getMessage().contains("cycle"), e.getMessage());
        }
    }

    @Test
    public void testSharedFileNodeListFailsCleanly(@TempDir Path tempDir) throws Exception {
        // each list holds two baseType-2 nodes pointing at the same next list; parsing every
        // reference would cost 2^lists list parses
        int lists = 40;
        int listSize = 72;
        int emptyListSize = 40;
        ByteBuffer buf = ByteBuffer.allocate(listSize * lists + emptyListSize)
                .order(ByteOrder.LITTLE_ENDIAN);
        int nodeHeader = 0x10 | (16 << 10) | (2 << 27) | (1 << 31);
        for (int i = 0; i < lists; i++) {
            long childStp = (long) (i + 1) * listSize;
            int childCb = i + 1 < lists ? listSize : emptyListSize;
            buf.putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0);
            for (int n = 0; n < 2; n++) {
                buf.putInt(nodeHeader).putLong(childStp).putInt(childCb);
            }
            buf.putInt(0).putLong(-1).putInt(0).putLong(OneNotePtr.FOOTER_CONST);
        }
        buf.putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0)
                .putInt(0).putLong(-1).putInt(0).putLong(OneNotePtr.FOOTER_CONST);
        Path shared = tempDir.resolve("shared");
        Files.write(shared, buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(shared.toFile())) {
            OneNoteDocument document = new OneNoteDocument();
            OneNotePtr ptr = new OneNotePtr(document, dif);
            TikaException e = assertTimeoutPreemptively(Duration.ofSeconds(30),
                    () -> assertThrows(TikaException.class,
                            () -> ptr.deserializeFileNodeList(document.root,
                                    new FileNodePtr())));
            assertTrue(e.getMessage().contains("referenced more than once"), e.getMessage());
        }
    }

    @Test
    public void testSharedFileNodesAreWalkedOnce(@TempDir Path tempDir)
            throws Exception {
        // every node lists the next node twice: acyclic, shallow, but 2^60 paths
        FileNode root = new FileNode().setGosid(ExtendedGUID.nil());
        FileNode current = root;
        for (int i = 0; i < 60; i++) {
            FileNode child = new FileNode().setGosid(ExtendedGUID.nil());
            current.childFileNodeList.setFileNodeListHeader(new FileNodeListHeader(0,
                    FileNodeListHeader.UNIT_MAGIC_CONSTANT, 0x10, 0));
            current.childFileNodeList.children.add(child);
            current.childFileNodeList.children.add(child);
            current = child;
        }
        Metadata metadata = walkWithEmptyFile(tempDir, root);
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testDistinctFileNodesAreBoundedByVisitBudget(@TempDir Path tempDir)
            throws Exception {
        FileNode root = new FileNode().setGosid(ExtendedGUID.nil());
        root.childFileNodeList.setFileNodeListHeader(new FileNodeListHeader(0,
                FileNodeListHeader.UNIT_MAGIC_CONSTANT, 0x10, 0));
        // more distinct nodes than the minimum visit budget of an empty document
        for (int i = 0; i < 100_001; i++) {
            root.childFileNodeList.children.add(new FileNode().setGosid(ExtendedGUID.nil()));
        }
        Metadata metadata = walkWithEmptyFile(tempDir, root);
        assertEquals(1, Arrays.stream(
                        metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(warning -> warning.contains("exceeded visit budget"))
                .count());
    }

    private static Metadata walkWithEmptyFile(Path tempDir, FileNode root) throws Exception {
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        Path emptyFile = Files.createFile(tempDir.resolve("empty"));
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(emptyFile.toFile())) {
            OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteParserConfig(),
                    new OneNoteDocument(), dif,
                    new XHTMLContentHandler(new ToTextContentHandler(), metadata, parseContext),
                    metadata, parseContext, null);
            assertTimeoutPreemptively(Duration.ofSeconds(30),
                    () -> walker.walkFileNode(root, null));
        }
        return metadata;
    }

    @Test
    public void testNegativeFileDataStoreLengthIsRejected(@TempDir Path tempDir)
            throws Exception {
        // header GUID, cb = -16, unused, reserved, then room for a footer
        Path file = tempDir.resolve("filedata");
        Files.write(file, ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[16]).putLong(-16).putInt(0).putLong(0).array());
        Method deserialize = OneNotePtr.class.getDeclaredMethod("deserializeFileDataStoreObject");
        deserialize.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            InvocationTargetException e = assertThrows(InvocationTargetException.class,
                    () -> deserialize.invoke(ptr));
            assertTrue(e.getCause() instanceof TikaException, e.getCause().toString());
        }
    }

    @Test
    public void testGlobalIdCopyBudgetIsSharedAcrossCopies(@TempDir Path tempDir)
            throws Exception {
        Path emptyFile = Files.createFile(tempDir.resolve("empty"));
        Method reserve = OneNotePtr.class.getDeclaredMethod("reserveGlobalIdCopies", int.class);
        reserve.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(emptyFile.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            OneNotePtr copy = new OneNotePtr(ptr);
            // an empty file gets the 100,000 floor
            for (int i = 0; i < 100; i++) {
                reserve.invoke(i % 2 == 0 ? ptr : copy, 1_000);
            }
            InvocationTargetException e = assertThrows(InvocationTargetException.class,
                    () -> reserve.invoke(copy, 1));
            assertTrue(e.getCause() instanceof TikaMemoryLimitException,
                    e.getCause().toString());
        }
    }

    @Test
    public void testObjectDeclarationWithSetReservedBitsParses(@TempDir Path tempDir)
            throws Exception {
        // oid (compact ID n=1, index 0), jci 0xd with fHasOidReferences, then the trailing
        // reserved 16 bits as 0xFFFF, as some writers produce
        Path file = tempDir.resolve("objdecl");
        Files.write(file, new byte[]{1, 0, 0, 0, 0x0d, 0, 1, 0, (byte) 0xff, (byte) 0xff});
        OneNoteDocument document = new OneNoteDocument();
        Revision revision = new Revision();
        revision.globalId.put(0L, GUID.nil());
        document.revisionMap.put(document.currentRevision, revision);
        Method deserialize = OneNotePtr.class.getDeclaredMethod(
                "deserializeObjectDeclarationWithRefCountBody");
        deserialize.setAccessible(true);
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            ObjectDeclarationWithRefCountBody body = (ObjectDeclarationWithRefCountBody)
                    deserialize.invoke(new OneNotePtr(document, dif));
            assertEquals(0xd, body.jcid.index);
            assertTrue(body.fHasOidReferences);
        }
    }

    @Test
    public void testTableOfContentsIsRecognized() throws Exception {
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testOneNote-fuzz1.one");
             OneNoteDirectFileResource dif = new OneNoteDirectFileResource(tis.getFile())) {
            OneNoteHeader header = new OneNotePtr(new OneNoteDocument(), dif).deserializeHeader();
            assertTrue(OneNoteParser.isTableOfContents(header));
        }
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testOneNote.one");
             OneNoteDirectFileResource dif = new OneNoteDirectFileResource(tis.getFile())) {
            OneNoteHeader header = new OneNotePtr(new OneNoteDocument(), dif).deserializeHeader();
            assertFalse(OneNoteParser.isTableOfContents(header));
        }
    }

    @Test
    public void testFileNodeListNestingIsDepthCapped(@TempDir Path tempDir) throws Exception {
        // 120 lists, each holding one baseType-2 node pointing at the next list - acyclic,
        // so only the depth cap can stop the recursion
        int lists = 120;
        ByteBuffer buf = ByteBuffer.allocate(56 * lists).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < lists; i++) {
            long childStp = (i + 1 < lists ? i + 1 : i) * 56L;
            writeFileNodeListBlock(buf, childStp, 56);
        }
        Path deep = tempDir.resolve("deep");
        Files.write(deep, buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(deep.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            assertThrows(TikaMemoryLimitException.class,
                    () -> ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr()));
        }
    }

    @Test
    public void testFragmentChainCycleFailsCleanly(@TempDir Path tempDir) throws Exception {
        // an empty fragment whose next-fragment points back at itself: once an infinite loop
        ByteBuffer buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(FileNodeListHeader.UNIT_MAGIC_CONSTANT).putInt(0x10).putInt(0)
                .putLong(0).putInt(36)
                .putLong(OneNotePtr.FOOTER_CONST);
        Path cyclic = tempDir.resolve("cyclic-fragment");
        Files.write(cyclic, buf.array());
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(cyclic.toFile())) {
            OneNotePtr ptr = new OneNotePtr(new OneNoteDocument(), dif);
            TikaException e = assertThrows(TikaException.class,
                    () -> ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr()));
            assertTrue(e.getMessage().contains("fragment cycle"), e.getMessage());
        }
    }

    @Test
    public void testLegacyEmbeddedExtractionHonorsShouldParseEmbedded() throws Exception {
        List<Metadata> offered = new ArrayList<>();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
                offered.add(metadata);
                return false;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) {
                throw new AssertionError("must not parse embedded when shouldParseEmbedded" +
                        " returns false");
            }
        });
        try (TikaInputStream tis =
                getResourceAsStream("/test-documents/testOneNoteEmbeddedWordDoc.one")) {
            new OneNoteParser().parse(tis, new ToTextContentHandler(), new Metadata(), context);
        }
        assertTrue(offered.size() > 0);
        // the legacy path offers every embedded object as an ATTACHMENT; the relationship
        // id is set only when the object carries a non-nil gosid
        for (Metadata offeredMetadata : offered) {
            assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString(),
                    offeredMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        }
    }

    /** From Office 365: MS-ONESTORE carried in MS-FSSHTTPB. */
    @Test
    public void testOneNoteDocumentFromOffice365_1() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNoteFromOffice365.one", metadata);

        // only the authors of the current content count - authors that only appear in
        // older page version snapshots are not reported
        assertEquals(Arrays.asList("Chang Du", "Du Chang"),
                Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR)));
        // both authors are referenced as AuthorMostRecent by current content; one of them
        // is first visited under another role, so its most-recent role must still register
        assertEquals(Arrays.asList("Chang Du", "Du Chang"),
                Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors")));

        assertEquals(Instant.ofEpochSecond(1636621406),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1636621448000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1636621448),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
        assertContains("Section1Page1Content", txt);
        // content from revisions other than each cell's current revision manifest
        assertContains("Section1Page2Content", txt);
        assertTrue(txt.indexOf("Section1Page1Content") < txt.indexOf("Section1Page2Content"));
    }

    /** From Office 365: MS-ONESTORE carried in MS-FSSHTTPB. */
    @Test
    public void testOneNoteDocumentFromOffice365_2() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNoteFromOffice365-2.one", metadata);

        assertEquals(List.of("Robert Lucarini"),
                Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR)));
        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains(
                "Robert Lucarini",
                mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1591712300),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1623597638000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1623597587),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));

        assertContains("Section1Page1Content", txt);
        // content from revisions other than each cell's current revision manifest
        assertContains("Section1Page2Content", txt);
    }

    private void assertNoJunk(String txt) {
        // font name
        assertNotContained("Calibri", txt);
        // garbage UTF-16 property values
        assertNotContained("\u5902", txt);
        assertNotContained("\u83F2", txt);
        assertNotContained("\u432F", txt);
        assertNotContained("\u01E1", txt);
        // NULs and vertical tabs come out as U+FFFD
        assertNotContained("\uFFFD", txt);
    }

    /** TIKA-3970: duplicate text. */
    @Test
    public void testDupeText() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("test-tika-3970-dupetext.one", metadata);

        assertEquals(1, StringUtils.countMatches(txt, "Sunday morning"));
        // page body text under ElementChildNodes references, written once
        assertEquals(1, StringUtils.countMatches(txt, "One more sun comes sliding down the sky"));
        assertNoJunk(txt);
    }

    /** TIKA-4303: Chinese text. */
    @Test
    public void testExtractChinese() throws Exception {
        Metadata metadata = new Metadata();
        XMLResult xml = getXML("test-tika-4303-Chinese-notes.one", metadata);
        assertContains("<p>中文标题</p>", xml.xml);
        assertContains("OneNote 是一款数字笔记本", xml.xml);
    }
}
