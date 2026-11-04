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
package org.apache.tika.parser.mobi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Mobi;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * The fixtures are built by a script, not by a reader application: a PDB with record 0
 * (PalmDOC header, MOBI header, EXTH, full name), two PalmDOC-compressed text records with
 * trailing entries (flags 0x3: a 3-byte TBS entry and the multibyte count), a JPEG cover
 * (EXTH 201), a PNG thumbnail (EXTH 202), an inline GIF, then FLIS, FCIS and EOF records.
 * {@code testMOBI.mobi} is a version 6 book in CP1252, {@code testMOBI_kf8.azw3} a
 * version 8 book in UTF-8 with KF8-style skeleton and fragment markup. The HUFF/CDIC variant
 * carries a real canonical Huffman code (lengths 8, 10 and 12, the first-byte table settling
 * the 8-bit codes and the per-length table the longer ones; no code is shorter than a byte,
 * so a byte-aligned padding can never form one) over a phrase dictionary split across CDIC
 * records, one phrase of which is itself a code stream; the encrypted and the
 * unknown-compression variants carry
 * unreadable text records, the unknown-compression one also a NULL cover index (EXTH 201 =
 * 0xFFFFFFFF) with a thumbnail, and the broken HUFF/CDIC one a HUFF index that points at a
 * text record. The hybrid is a version 6 book, the shared images, a BOUNDARY record and a
 * version 8 book, with EXTH 121 of the first header pointing at the second and no EXTH 524,
 * so the language comes from the header's locale. {@code src/test/python/make_mobi_fixtures.py}
 * builds them all.
 */
public class MobiParserTest extends TikaTest {

    private static final String[] FIXTURES = {"testMOBI.mobi", "testMOBI_kf8.azw3",
            "testMOBI_huffcdic.mobi", "testMOBI_huffcdic_broken.mobi", "testMOBI_hybrid.mobi",
            "testMOBI_encrypted.mobi", "testMOBI_unknown_compression.mobi", "testMOBI_stored.mobi"};

    private byte[] fixture(String name) throws IOException {
        try (InputStream stream = getResourceAsStream("/test-documents/" + name)) {
            return stream.readAllBytes();
        }
    }

    @Test
    public void testMetadataAndText() throws Exception {
        XMLResult result = getXML("testMOBI.mobi");
        Metadata metadata = result.metadata;
        assertEquals("application/x-mobipocket-ebook", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("The Test Book", metadata.get(TikaCoreProperties.TITLE));
        assertEquals(Arrays.asList("Ada Example", "Bob Example"),
                Arrays.asList(metadata.getValues(TikaCoreProperties.CREATOR)));
        assertEquals("Tika Press", metadata.get(TikaCoreProperties.PUBLISHER));
        assertEquals("<p>A small Mobipocket book that exercises the parser.</p>",
                metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals(Arrays.asList("9780000000002", "B000TEST01"),
                Arrays.asList(metadata.getValues(TikaCoreProperties.IDENTIFIER)));
        assertEquals(Arrays.asList("Testing", "Fixtures"),
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
        assertEquals("generator script", metadata.get(TikaCoreProperties.CONTRIBUTOR));
        assertEquals("Apache License 2.0", metadata.get(TikaCoreProperties.RIGHTS));
        assertEquals("en", metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("2021-03-04", metadata.get(TikaCoreProperties.CREATED),
                "a date-only EXTH 106 stays a date, no midnight is invented");
        assertEquals("windows-1252", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("6", metadata.get(Mobi.VERSION));
        assertEquals("palmdoc", metadata.get(Mobi.COMPRESSION));
        assertEquals("none", metadata.get(Mobi.ENCRYPTION));
        assertEquals("false", metadata.get(Mobi.HYBRID));
        assertNull(metadata.get(Mobi.CDE_TYPE));

        String xml = result.xml;
        assertContains("<h1>Chapter One</h1>", xml);
        assertContains("The café opened at dawn.", xml);
        assertContains("<h1>Chapter Two</h1>", xml);
        assertContains("Second chapter, second record.", xml);
        assertNotContained("filepos", xml);
        // the trailing entries are not text
        assertNotContained("ª»", xml);
        assertContainsCount("<body", xml, 1);
    }

    @Test
    public void testImages() throws Exception {
        List<Metadata> list = getRecursiveMetadata("testMOBI.mobi");
        assertEquals(4, list.size());
        assertEquals("cover.jpeg", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image/jpeg", list.get(1).get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("thumbnail.png", list.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                list.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("image5.gif", list.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image/gif", list.get(3).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testKf8() throws Exception {
        List<Metadata> list = getRecursiveMetadata("testMOBI_kf8.azw3");
        Metadata metadata = list.get(0);
        assertEquals("application/vnd.amazon.mobi8-ebook", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("The Test Book", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("UTF-8", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("8", metadata.get(Mobi.VERSION));
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Erstes Kapitel", content);
        assertContains("Zwölf Bögen, grüne Türen.", content);
        assertContains("äöü umlauts", content);
        assertNotContained("Cover", content);
        // the stylesheet flow after the FDST's first is not text
        assertNotContained("calibre", content);
        assertNotContained("margin-top", content);
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("image/jpeg", list.get(1).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testHybridYieldsTheBookOnce() throws Exception {
        List<Metadata> list = getRecursiveMetadata("testMOBI_hybrid.mobi");
        assertEquals(3, list.size(), "the document, its cover and thumbnail; no second copy");
        Metadata old = list.get(0);
        assertEquals("application/x-mobipocket-ebook", old.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("The Hybrid Book", old.get(TikaCoreProperties.TITLE));
        assertEquals("true", old.get(Mobi.HYBRID));
        assertEquals("de", old.get(TikaCoreProperties.LANGUAGE));
        assertContains("The old half, café and all.", old.get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("Erstes Kapitel", old.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("cover.jpeg", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("thumbnail.png", list.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    /**
     * With {@code extractKf8}, the KF8 half of a hybrid is an embedded document of its own.
     * Its header counts records from its own record 0, as real hybrids have it; the text is
     * cut at the FDST record found that way, so the stylesheet stays out.
     */
    @Test
    public void testHybridKf8HalfOnRequest() throws Exception {
        MobiParser parser = new MobiParser();
        parser.setExtractKf8(true);
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(fixture("testMOBI_hybrid.mobi"))) {
            list = getRecursiveMetadata(tis, parser, new Metadata(), new ParseContext(), false);
        }
        assertEquals(4, list.size());
        Metadata kf8 = list.get(3);
        assertEquals("kf8.azw3", kf8.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("application/vnd.amazon.mobi8-ebook", kf8.get(HttpHeaders.CONTENT_TYPE));
        assertNull(kf8.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("false", kf8.get(Mobi.HYBRID));
        assertEquals("The Hybrid Book", kf8.get(TikaCoreProperties.TITLE));
        assertEquals("de", kf8.get(TikaCoreProperties.LANGUAGE));
        String content = kf8.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Erstes Kapitel", content);
        assertContains("Die neue Hälfte.", content);
        assertNotContained("The old half", content);
        assertNotContained("calibre", content);
        assertNull(kf8.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testEncryptedBookKeepsMetadataAndCover() throws Exception {
        List<Metadata> list = getRecursiveMetadata("testMOBI_encrypted.mobi");
        Metadata metadata = list.get(0);
        assertEquals("The Test Book", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Ada Example", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("mobipocket", metadata.get(Mobi.ENCRYPTION));
        assertEquals("true", metadata.get(TikaCoreProperties.IS_ENCRYPTED));
        assertNull(metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
        String warning = metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertContains(EncryptedDocumentException.class.getName(), warning);
        assertContains("metadata and images only", warning);
        assertNotContained("Chapter", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(4, list.size());
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testHuffCdic() throws Exception {
        XMLResult result = getXML("testMOBI_huffcdic.mobi");
        assertEquals("The Test Book", result.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("huffcdic", result.metadata.get(Mobi.COMPRESSION));
        assertNull(result.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("<h1>Chapter One</h1>", result.xml);
        assertContains("The café opened at dawn and the words of the first chapter", result.xml);
        assertContains("<h1>Chapter Two</h1>", result.xml);
        assertContains("quick brown fox quick brown fox", result.xml);
        assertContainsCount("the dawn the words", result.xml, 300);
    }

    @Test
    public void testUnknownCompressionLeavesAWarning() throws Exception {
        XMLResult result = getXML("testMOBI_unknown_compression.mobi");
        assertEquals("The Test Book", result.metadata.get(TikaCoreProperties.TITLE));
        String warning = result.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertNotNull(warning);
        assertContains("Unknown text compression", warning);
        assertNotContained("Chapter", result.xml);
        assertEquals("unknown", result.metadata.get(Mobi.COMPRESSION));

        // EXTH 201 is the NULL index here, so the EXTH 202 thumbnail is the representative image
        List<Metadata> list = getRecursiveMetadata("testMOBI_unknown_compression.mobi");
        assertEquals(4, list.size());
        assertEquals("image2.jpeg", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("thumbnail.png", list.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testUnreadableHuffTablesLeaveAWarning() throws Exception {
        XMLResult result = getXML("testMOBI_huffcdic_broken.mobi");
        assertEquals("The Test Book", result.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("huffcdic", result.metadata.get(Mobi.COMPRESSION));
        assertContains("HUFF/CDIC tables unreadable",
                result.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertNotContained("Chapter", result.xml);
    }

    @Test
    public void testTruncatedFile() throws Exception {
        byte[] whole = fixture("testMOBI.mobi");
        int record0End = PalmDatabase.read(whole).slice(1).offset();
        for (int length : new int[]{0, 10, 77, 78, 100, 300, 700, record0End, whole.length - 1}) {
            byte[] cut = Arrays.copyOf(whole, length);
            Metadata metadata = new Metadata();
            try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(cut))) {
                new MobiParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
            } catch (TikaException e) {
                // the only way out, and only while record 0 is incomplete
                assertTrue(length < record0End, length + ": " + e);
                continue;
            }
            if (length >= record0End) {
                assertEquals("The Test Book", metadata.get(TikaCoreProperties.TITLE),
                        "length " + length);
            }
        }
    }

    /**
     * A hybrid cut right after its cover, as a download that stopped leaves it: the table
     * still lists every record, the file holds the first four. Title and cover come out,
     * the KF8 half and the text records behind the cut do not, and the metadata say so.
     */
    @Test
    public void testTruncatedFileKeepsWhatIsThere() throws Exception {
        byte[] whole = fixture("testMOBI_hybrid.mobi");
        int cut = PalmDatabase.read(whole).slice(4).offset();
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(
                Arrays.copyOf(whole, cut)))) {
            list = getRecursiveMetadata(tis, true);
        }
        Metadata metadata = list.get(0);
        assertNull(metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
        assertEquals("The Hybrid Book", metadata.get(TikaCoreProperties.TITLE));
        assertContains("The old half", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("false", metadata.get(Mobi.HYBRID));
        assertContains("The file is truncated: 4 of 11 records",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertEquals(3, list.size());
        assertEquals("cover.jpeg", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * Random byte flips over every fixture: the parser may refuse the file with a
     * TikaException or make the best of it, but it may not fail with anything else.
     */
    @Test
    @Timeout(120)
    public void testMutatedFilesFailCleanly() throws Exception {
        Random random = new Random(5026);
        MobiParser parser = new MobiParser();
        // the KF8 half of the hybrid is parsed too, mutations behind the boundary included
        parser.setExtractKf8(true);
        for (String fixture : FIXTURES) {
            byte[] original = fixture(fixture);
            for (int round = 0; round < 200; round++) {
                byte[] mutated = original.clone();
                int flips = 1 + random.nextInt(8);
                for (int i = 0; i < flips; i++) {
                    mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
                }
                try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(mutated))) {
                    getRecursiveMetadata(tis, parser, new Metadata(), new ParseContext(), false);
                } catch (TikaException e) {
                    // refused or stopped, both fine
                } catch (RuntimeException | StackOverflowError e) {
                    throw new AssertionError(fixture + " round " + round + ": " + e, e);
                }
            }
        }
    }

    /** Not even a Palm Database, as a Topaz or KFX book named .azw: nothing and a warning. */
    @Test
    public void testNotAPalmDatabaseYieldsNothing() throws Exception {
        Metadata metadata = new Metadata();
        try (InputStream stream = getResourceAsStream("/test-documents/testEPUB.epub");
                TikaInputStream tis = TikaInputStream.get(stream)) {
            new MobiParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertContains("Not a Mobipocket book",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testStoredTextTitleFallbackLanguageAndCdeType() throws Exception {
        XMLResult result = getXML("testMOBI_stored.mobi");
        Metadata metadata = result.metadata;
        assertEquals("none", metadata.get(Mobi.COMPRESSION));
        assertEquals("A Test Book for Apache Tika", metadata.get(TikaCoreProperties.TITLE),
                "without EXTH 503 the title is the header's full name");
        assertEquals("fr", metadata.get(TikaCoreProperties.LANGUAGE),
                "EXTH 524 wins over the header's locale (German)");
        assertEquals("EBOK", metadata.get(Mobi.CDE_TYPE));
        assertContains("The café opened at dawn.", result.xml);
        assertContains("Second chapter, second record.", result.xml);
    }

    @Test
    public void testDates() throws Exception {
        assertEquals("1993-10-02T00:00:00Z", created("1993-10-02T00:00:00+00:00"));
        assertEquals("1993-10-02T00:00:00", created("1993-10-02T00:00:00"));
        assertEquals("2014-11-04", created("4 November 2014"), "not ISO, but a date");
        assertNull(created("November 2014"), "no day, no creation date");
    }

    /** The CREATED value of a book whose only EXTH record is a 106 with the given date. */
    private String created(String date) throws Exception {
        List<Object[]> exth = new ArrayList<>();
        exth.add(new Object[]{106, date});
        byte[] record0 = record0WithExth(exth);
        byte[] book = palmDatabase(fixture("testMOBI.mobi"), List.of(record0, new byte[]{0}));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(book)) {
            new MobiParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata.get(TikaCoreProperties.CREATED);
    }

    @Test
    public void testUnknownCodepageIsWindows1252() throws Exception {
        byte[] file = fixture("testMOBI.mobi");
        int recordZero = PalmDatabase.read(file).slice(0).offset();
        ByteBuffer.wrap(file).putInt(recordZero + 28, 0);
        Metadata metadata = new Metadata();
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            xml = getXML(tis, new MobiParser(), metadata).xml;
        }
        assertEquals("windows-1252", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertContains("The café opened at dawn.", xml);
    }

    @Test
    public void testHuffCdicKeepsTheTextBeforeABadRecord() throws Exception {
        byte[] file = fixture("testMOBI_huffcdic.mobi");
        PalmDatabase pdb = PalmDatabase.read(file);
        // the last text record, its trailing entries kept: codes the table does not hold
        int last = MobiHeader.read(pdb).textRecordCount();
        PalmDatabase.Slice record = pdb.slice(last);
        Arrays.fill(file, record.offset(), record.offset() + record.length() - 4, (byte) 0);
        Metadata metadata = new Metadata();
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            xml = getXML(tis, new MobiParser(), metadata).xml;
        }
        assertContains("Chapter One", xml);
        assertContains("Text record " + last + " cannot be decoded",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testLargerThanMaxFileSizeIsReadAsFarAsItGoes() throws Exception {
        byte[] file = fixture("testMOBI.mobi");
        MobiParser parser = new MobiParser();
        parser.setMaxFileSize(PalmDatabase.read(file).slice(2).offset());
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertEquals("The Test Book", metadata.get(TikaCoreProperties.TITLE));
        String warning = String.join(" ",
                metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("larger than", warning);
        assertContains("maxFileSize", warning);
        assertThrows(IllegalArgumentException.class, () -> parser.setMaxFileSize(0));
    }

    /** A .prc that is a PalmDOC text, not a Mobipocket book: nothing, a warning, no failure. */
    @Test
    public void testOtherPalmDatabaseYieldsNothing() throws Exception {
        byte[] file = new byte[PalmDatabase.HEADER_SIZE + 8 + 2 + 16];
        System.arraycopy("TEXtREAd".getBytes(StandardCharsets.US_ASCII), 0, file, 60, 8);
        ByteBuffer.wrap(file).putShort(76, (short) 1).putInt(78, PalmDatabase.HEADER_SIZE + 10);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new MobiParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertContains("TEXtREAd", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testExthIsBounded() throws Exception {
        List<String> creators = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            creators.add("Creator " + i);
        }
        List<Object[]> exth = new ArrayList<>();
        for (String creator : creators) {
            exth.add(new Object[]{100, creator});
        }
        MobiHeader header = MobiHeader.read(record0WithExth(exth));
        assertEquals(MobiHeader.MAX_VALUES_PER_TYPE,
                header.exthStrings(MobiHeader.EXTH_CREATOR).size());
    }

    /**
     * A version 6 record 0 without text: a minimal MOBI header and the given EXTH records,
     * each a type and a string value.
     */
    private static byte[] record0WithExth(List<Object[]> records) {
        int headerLength = 232;
        List<byte[]> exth = new ArrayList<>();
        int exthLength = 12;
        for (Object[] record : records) {
            byte[] value = ((String) record[1]).getBytes(StandardCharsets.US_ASCII);
            exth.add(ByteBuffer.allocate(8 + value.length).putInt((Integer) record[0])
                    .putInt(8 + value.length).put(value).array());
            exthLength += 8 + value.length;
        }
        ByteBuffer r = ByteBuffer.allocate(16 + headerLength + exthLength);
        r.position(16);
        // MOBI, header length, book type, codepage, unique id, file version
        r.put("MOBI".getBytes(StandardCharsets.US_ASCII)).putInt(headerLength).putInt(2)
                .putInt(65001).putInt(0).putInt(6);
        // the field offsets count from record 0, the MOBI header starts at 16
        r.putInt(128, 0x40);
        r.position(16 + headerLength);
        r.put("EXTH".getBytes(StandardCharsets.US_ASCII)).putInt(exthLength).putInt(records.size());
        for (byte[] record : exth) {
            r.put(record);
        }
        return r.array();
    }

    @Test
    public void testDetection() throws Exception {
        byte[] file = fixture("testMOBI.mobi");
        DefaultDetector detector = new DefaultDetector();
        for (String[] c : new String[][]{{"book.mobi", "application/x-mobipocket-ebook"},
                {"book.azw", "application/vnd.amazon.ebook"},
                {"book.azw3", "application/vnd.amazon.mobi8-ebook"},
                {"book.prc", "application/x-mobipocket-ebook"}}) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, c[0]);
            try (TikaInputStream tis = TikaInputStream.get(file)) {
                assertEquals(c[1], detector.detect(tis, metadata, new ParseContext()).toString(), c[0]);
            }
        }
    }

    @Test
    public void testTrailingEntries() {
        byte[] record = {'a', 'b', 'c', 0x00, (byte) 0xaa, (byte) 0xbb, (byte) 0x83};
        PalmDatabase.Slice slice = new PalmDatabase.Slice(record, 0, record.length);
        assertEquals(4, PalmDoc.trailingEntriesLength(slice, 0x3));
        assertEquals(3, PalmDoc.trailingEntriesLength(slice, 0x2));
        assertEquals(0, PalmDoc.trailingEntriesLength(slice, 0));
        byte[] multibyte = {'a', (byte) 0xc3, (byte) 0xa4, 0x02};
        assertEquals(3, PalmDoc.trailingEntriesLength(
                new PalmDatabase.Slice(multibyte, 0, multibyte.length), 0x1));
        assertEquals(0, PalmDoc.trailingEntriesLength(new PalmDatabase.Slice(new byte[0], 0, 0), 0x3));
    }

    @Test
    public void testLanguageOfLocale() {
        assertEquals("de", MobiHeader.languageOf(0x0407));
        assertEquals("en", MobiHeader.languageOf(0x0809));
        assertNull(MobiHeader.languageOf(0));
    }

    @Test
    public void testTextIsCutAtTheConfiguredLength() throws Exception {
        MobiParser parser = new MobiParser();
        parser.setMaxTextLength(600);
        Metadata metadata = new Metadata();
        String xml = getXML("testMOBI.mobi", parser, metadata).xml;
        assertContains("The text is cut at 600 of 6489 bytes",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("Chapter One", xml);
        assertNotContained("Chapter Two", xml);
    }

    @Test
    public void testMaxTextLengthMustBePositive() {
        MobiParser parser = new MobiParser();
        assertThrows(IllegalArgumentException.class, () -> parser.setMaxTextLength(0));
        assertThrows(IllegalArgumentException.class, () -> parser.setMaxTextLength(-1));
        parser.setMaxTextLength(1);
        assertEquals(1, parser.getMaxTextLength());
    }

    /**
     * HUFF tables built by hand: every code is one byte, all 256 first-byte entries terminal
     * with length 8 and index base 255, so the byte b selects phrase 255 - b and 0xFF
     * selects phrase 0.
     */
    private static byte[] huffTables() {
        ByteBuffer huff = ByteBuffer.allocate(24 + 256 * 4 + 32 * 8);
        huff.put("HUFF".getBytes(StandardCharsets.ISO_8859_1)).putInt(24).putInt(24)
                .putInt(24 + 256 * 4).putInt(0).putInt(0);
        for (int i = 0; i < 256; i++) {
            huff.putInt(8 | 0x80 | (255 << 8));
        }
        return huff.array();
    }

    /** A CDIC record of the phrases; those before {@code firstLiteral} are code streams. */
    private static byte[] cdicRecord(int firstLiteral, byte[]... phrases) {
        int size = 16 + phrases.length * 2;
        for (byte[] phrase : phrases) {
            size += 2 + phrase.length;
        }
        ByteBuffer cdic = ByteBuffer.allocate(size);
        cdic.put("CDIC".getBytes(StandardCharsets.ISO_8859_1)).putInt(16).putInt(phrases.length)
                .putInt(16);
        int offset = phrases.length * 2;
        for (byte[] phrase : phrases) {
            cdic.putShort((short) offset);
            offset += 2 + phrase.length;
        }
        for (int i = 0; i < phrases.length; i++) {
            cdic.putShort((short) (phrases[i].length | (i >= firstLiteral ? 0x8000 : 0)))
                    .put(phrases[i]);
        }
        return cdic.array();
    }

    @Test
    public void testPhraseDefinedByItselfIsRefused() throws Exception {
        HuffCdic huffCdic = new HuffCdic(huffTables(), List.of(cdicRecord(1,
                new byte[]{(byte) 0xff}, "x".getBytes(StandardCharsets.UTF_8))));
        byte[] text = {(byte) 0xff};
        TikaException ex = assertThrows(TikaException.class,
                () -> huffCdic.decode(new PalmDatabase.Slice(text, 0, 1), 4096));
        assertContains("in terms of itself", ex.getMessage());
    }

    @Test
    public void testPhraseNestingIsBounded() throws Exception {
        // phrase 0 is the code stream 0xFE (phrase 1), phrase 1 the stream 0xFD (phrase 2), ...
        byte[][] phrases = new byte[HuffCdic.MAX_DEPTH + 3][];
        for (int i = 0; i < phrases.length - 1; i++) {
            phrases[i] = new byte[]{(byte) (0xff - (i + 1))};
        }
        phrases[phrases.length - 1] = "end".getBytes(StandardCharsets.UTF_8);
        HuffCdic huffCdic = new HuffCdic(huffTables(),
                List.of(cdicRecord(phrases.length - 1, phrases)));
        byte[] text = {(byte) 0xff};
        TikaException ex = assertThrows(TikaException.class,
                () -> huffCdic.decode(new PalmDatabase.Slice(text, 0, 1), 4096));
        assertContains("nested deeper than", ex.getMessage());
    }

    @Test
    public void testOversizedPhraseIsRefused() throws Exception {
        // phrase 0 is three codes for phrase 1, a literal of 32 KB: 96 KB, over the 64 KB cap
        byte[] big = new byte[32767];
        HuffCdic huffCdic = new HuffCdic(huffTables(), List.of(cdicRecord(1,
                new byte[]{(byte) 0xfe, (byte) 0xfe, (byte) 0xfe}, big)));
        byte[] text = {(byte) 0xff};
        TikaException ex = assertThrows(TikaException.class,
                () -> huffCdic.decode(new PalmDatabase.Slice(text, 0, 1), 1 << 20));
        assertContains("decodes to more than", ex.getMessage());
    }

    @Test
    public void testDictionaryCountIsBounded() throws Exception {
        // 65536 offsets that all point at byte 16, where the table's own first bytes read as
        // a zero-length phrase: no phrase bytes, but 65536 phrases per record
        int count = 65536;
        ByteBuffer cdic = ByteBuffer.allocate(16 + count * 2);
        cdic.put("CDIC".getBytes(StandardCharsets.ISO_8859_1)).putInt(16).putInt(Integer.MAX_VALUE)
                .putInt(16);
        for (int i = 0; i < count; i++) {
            cdic.putShort((short) 0);
        }
        List<byte[]> records = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            records.add(cdic.array());
        }
        TikaException ex = assertThrows(TikaException.class,
                () -> new HuffCdic(huffTables(), records));
        assertContains("larger than", ex.getMessage());
    }

    @Test
    public void testDictionaryIsBounded() throws Exception {
        // 16384 offsets (the most a 16-bit offset table can hold next to a phrase) that all
        // point at the same 32 KB phrase would make a 512 MB dictionary
        int count = 16384;
        byte[] phrase = new byte[32767];
        ByteBuffer cdic = ByteBuffer.allocate(16 + count * 2 + 2 + phrase.length);
        cdic.put("CDIC".getBytes(StandardCharsets.ISO_8859_1)).putInt(16).putInt(count).putInt(14);
        for (int i = 0; i < count; i++) {
            cdic.putShort((short) (count * 2));
        }
        cdic.putShort((short) (phrase.length | 0x8000)).put(phrase);
        TikaException ex = assertThrows(TikaException.class,
                () -> new HuffCdic(huffTables(), List.of(cdic.array())));
        assertContains("larger than", ex.getMessage());
    }

    @Test
    public void testHybridWithAnythingButKf8BehindTheBoundaryIsNotSplit() throws Exception {
        // EXTH 121 pointing at a text record, an image, the KF8 text, FDST, FLIS, FCIS or
        // the 4-byte EOF record: none of them is a KF8 half, none may fail
        byte[] original = fixture("testMOBI_hybrid.mobi");
        for (int target : new int[]{1, 2, 3, 6, 7, 8, 9, 10}) {
            byte[] mutated = original.clone();
            int exth121 = indexOf(mutated, new byte[]{0, 0, 0, 121, 0, 0, 0, 12});
            ByteBuffer.wrap(mutated).putInt(exth121 + 8, target);
            assertNotAHybrid(mutated);
        }
    }

    /**
     * A version 6 header behind the boundary, pointing at itself again: the chain a
     * hybrid check that trusted EXTH 121 would follow forever.
     */
    @Test
    public void testVersion6HeaderBehindTheBoundaryIsNotSplit() throws Exception {
        byte[] file = fixture("testMOBI_hybrid.mobi");
        PalmDatabase pdb = PalmDatabase.read(file);
        List<byte[]> records = new ArrayList<>();
        for (int i = 0; i < pdb.recordCount(); i++) {
            records.add(i == 5 ? pdb.record(0) : pdb.record(i));
        }
        assertNotAHybrid(palmDatabase(file, records));
    }

    private void assertNotAHybrid(byte[] file) throws Exception {
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(file))) {
            list = getRecursiveMetadata(tis, true);
        }
        Metadata metadata = list.get(0);
        assertEquals("The Hybrid Book", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("false", metadata.get(Mobi.HYBRID));
        assertContains("no KF8 header there",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        // the bogus boundary must not cut the images either: cover and thumbnail, no half
        assertEquals(3, list.size());
        assertEquals("cover.jpeg", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("thumbnail.png", list.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    /** A Palm Database with the header of {@code file} and the given records. */
    private static byte[] palmDatabase(byte[] file, List<byte[]> records) {
        int tableEnd = PalmDatabase.HEADER_SIZE + 8 * records.size() + 2;
        int size = tableEnd;
        for (byte[] record : records) {
            size += record.length;
        }
        ByteBuffer out = ByteBuffer.allocate(size);
        out.put(file, 0, PalmDatabase.HEADER_SIZE);
        out.putShort(76, (short) records.size());
        int offset = tableEnd;
        for (int i = 0; i < records.size(); i++) {
            out.putInt(PalmDatabase.HEADER_SIZE + 8 * i, offset).putInt(PalmDatabase.HEADER_SIZE + 8 * i + 4, 2 * i);
            out.put(offset, records.get(i));
            offset += records.get(i).length;
        }
        return out.array();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("needle not found");
    }
}
