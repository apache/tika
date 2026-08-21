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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class OneNoteParserTest extends TikaTest {

    //test recursive parser wrapper for image files

    @Test
    public void testFuzzerRegressionInputsFallBackToLegacyDump() throws Exception {
        // structural failures no longer abort the parse - the legacy string dump runs and
        // the original failure is pinned in the parse-warning metadata
        String[][] resources = {
                {"testOneNote-fuzz1.one", "Missing dependent revision"},
                {"testOneNote-fuzz2.one", "unified property count"},
                {"testOneNote-fuzz3.one", "Invalid GUID string"}
        };
        for (String[] resource : resources) {
            InputStream input = getClass().getResourceAsStream("/test-documents/" + resource[0]);
            assertNotNull(input, resource[0]);
            try (InputStream stream = input;
                 TikaInputStream tis = TikaInputStream.get(stream)) {
                Metadata metadata = new Metadata();
                new OneNoteParser().parse(tis, new ToTextContentHandler(), metadata,
                        new ParseContext());
                assertTrue(Arrays.stream(
                                metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                        .anyMatch(warning -> warning.contains(resource[1])
                                && warning.contains("falling back to legacy text dump")),
                        () -> resource[0] + ": " + Arrays.toString(metadata.getValues(
                                TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)));
            }
        }
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

    /**
     * This is the sample document that is automatically created from onenote 2013.
     */
    @Test
    public void testOneNote2013Doc1() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote1.one", metadata);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertContains("Olya Veselova\u0000", authors);
        assertContains("Microsoft\u0000", authors);
        assertContains("Scott\u0000", authors);
        assertContains("Scott H. W. Snyder\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("Microsoft\u0000", originalAuthors);

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
        assertContains("neat info about totally killin it bro", txt);
        assertContains("Section1TextArea1", txt);
        assertContains("Section1HeaderTitle", txt);
        assertContains("Section1TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertContains("Olya Veselova\u0000", authors);
        assertContains("Microsoft\u0000", authors);
        assertContains("Scott\u0000", authors);
        assertContains("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertContains("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1336059427),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426629000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get(ONE_NOTE_PREFIX + "lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426628),
                Instant.ofEpochSecond(Long.parseLong(metadata.get(TikaCoreProperties.MODIFIED))));
    }

    @Test
    public void testOneNote2013Doc3() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote3.one", metadata);
        assertContains("awesome information about sports or some crap like that.", txt);
        assertContains("Quit doing horrible things to me. Dang you. ", txt);
        assertContains("Section2TextArea1", txt);
        assertContains("Section2HeaderTitle", txt);
        assertContains("Section2TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR));
        assertNotContained("Olya Veselova\u0000", authors);
        assertNotContained("Microsoft\u0000", authors);
        assertNotContained("Scott\u0000", authors);
        assertNotContained("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertNotContained("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertNotContained("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

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
        assertNotContained("Olya Veselova\u0000", authors);
        assertNotContained("Microsoft\u0000", authors);
        assertNotContained("Scott\u0000", authors);
        assertNotContained("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertNotContained("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertNotContained("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

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
        assertContains("nicholas dipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "mostRecentAuthors"));
        assertContains("nicholas dipiazza\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues(ONE_NOTE_PREFIX + "originalAuthors"));
        assertContains("nicholas dipiazza\u0000", originalAuthors);

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
    public void testPropertyValueBudgetIsSharedAcrossCopiesAndResetsPerList(
            @TempDir Path tempDir) throws Exception {
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

            ptr.deserializeFileNodeList(new FileNodeList(), new FileNodePtr());
            reservePropertyCount.invoke(ptr, 1L, "test");
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
            OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteTreeWalkerOptions(),
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
            OneNoteTreeWalker walker = new OneNoteTreeWalker(new OneNoteTreeWalkerOptions(),
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
        // an empty fragment whose next-fragment reference points back at itself would
        // previously loop forever
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
        AtomicInteger asked = new AtomicInteger();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
                asked.incrementAndGet();
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
        assertTrue(asked.get() > 0);
    }

    /**
     * Test a document pulled from Office 365 which stores the MS-ONESTORE document using the MS-FSSHTTPB
     * protocol.
     */
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

    /**
     * Test a document pulled from Office 365 which stores the MS-ONESTORE document using the MS-FSSHTTPB
     * protocol.
     */
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
        //Should not include font names in the text
        assertNotContained("Calibri", txt);
        //Should not include UTF-16 property values that are garbage
        assertNotContained("\u5902", txt);
        assertNotContained("\u83F2", txt);
        assertNotContained("\u432F", txt);
        assertNotContained("\u01E1", txt);
    }

    /**
     * TIKA-3970 - test duplicate text.
     */
    @Test
    public void testDupeText() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("test-tika-3970-dupetext.one", metadata);

        assertEquals(1, StringUtils.countMatches(txt, "Sunday morning"));
    }

    /**
     * TIKA-4303 - test extract Chinese
     */
    @Test
    public void testExtractChinese() throws Exception {
        Metadata metadata = new Metadata();
        XMLResult xml = getXML("test-tika-4303-Chinese-notes.one", metadata);
        assertContains("<p>中文标题</p>", xml.xml);
    }
}
