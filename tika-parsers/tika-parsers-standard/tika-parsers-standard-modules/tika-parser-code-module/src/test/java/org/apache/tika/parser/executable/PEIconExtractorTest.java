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
package org.apache.tika.parser.executable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.input.CountingInputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.sax.BodyContentHandler;

/**
 * The test executables were built with MinGW from an empty {@code main()}
 * respectively an empty DLL plus a resource script that embeds two icon
 * groups: id 1 (from testWindows-icons-app.ico, 16/32 px BMP + 256 px PNG)
 * and the named group DOCICON (from testWindows-icons-doc.ico, 16/48 px BMP).
 * testWindows-x86-64-icons-lang.dll instead carries group 1 twice, in
 * language 1033 (app.ico) and 1031 (doc.ico).
 * <p>
 * Layout of testWindows-x86-32-icons.exe (file offsets, from pefile):
 * .rsrc raw 0x3400-0x7c00 at RVA 0xa000; root directory 0x3400 with the
 * type entries at 0x3410 (RT_ICON) and 0x3418 (RT_GROUP_ICON); data entries
 * 0x3530-0x3590 (icons 1-5, DOCICON, group 1); icon data 0x35a0-0x7b18
 * (icon 2 spans 0x3a08-0x4ab0); GRPICONDIR DOCICON 0x7b18-0x7b3a and
 * group 1 0x7b40-0x7b70.
 */
public class PEIconExtractorTest extends TikaTest {

    private static final String EXE = "testWindows-x86-32-icons.exe";
    private static final String DLL = "testWindows-x86-64-icons.dll";
    private static final String LANG_DLL = "testWindows-x86-64-icons-lang.dll";
    private static final String APP_ICO = "testWindows-icons-app.ico";
    private static final String DOC_ICO = "testWindows-icons-doc.ico";

    private static final String THUMBNAIL =
            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString();
    private static final String ATTACHMENT =
            TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString();

    @Test
    public void testIconsFromExe() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata(EXE);
        assertEquals(3, metadataList.size());

        Metadata exe = metadataList.get(0);
        assertEquals("application/x-msdownload", exe.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(ExecutableParser.MACHINE_x86_32, exe.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("32", exe.get(ExecutableParser.ARCHITECTURE_BITS));
        assertNull(exe.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
        assertNull(exe.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        // Named entries precede numeric ids in the resource directory, so the
        // DOCICON group is the first one and therefore the file's own icon
        assertIcon(metadataList.get(1), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertIcon(metadataList.get(2), "icon_1.ico", "14/1/1033", ATTACHMENT);
    }

    @Test
    public void testIconsFromDll() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata(DLL);
        assertEquals(3, metadataList.size());

        Metadata dll = metadataList.get(0);
        assertEquals("application/x-msdownload", dll.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(ExecutableParser.MACHINE_x86_64, dll.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("64", dll.get(ExecutableParser.ARCHITECTURE_BITS));

        assertIcon(metadataList.get(1), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertIcon(metadataList.get(2), "icon_1.ico", "14/1/1033", ATTACHMENT);
    }

    /**
     * The icon's name is Tika's invention, so it must not become text of the file.
     */
    @Test
    public void testIconsAddNoText() throws Exception {
        assertContains("<body />", getXML(EXE).xml);
    }

    /**
     * The resource compiler copies the images verbatim, so the rebuilt
     * .ico files must be identical to the ones that went in. Checks the
     * metadata as handed to the extractor, before any re-detection.
     */
    @Test
    public void testReconstructedIcoIsByteIdentical() throws Exception {
        for (String file : new String[]{EXE, DLL}) {
            RecordingExtractor extractor = parse(readTestResource(file));
            assertEquals(2, extractor.contents.size(), file);
            assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0), file);
            assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(1), file);
            assertIcon(extractor.metadata.get(0), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
            assertIcon(extractor.metadata.get(1), "icon_1.ico", "14/1/1033", ATTACHMENT);
        }
    }

    /**
     * File-backed input is read through a positioned channel rather than by
     * skipping; the result must not differ.
     */
    @Test
    public void testFileBackedInput() throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsFile("/test-documents/" + EXE)
                .toPath())) {
            assertTrue(tis.hasFile());
            new ExecutableParser().parse(tis, new BodyContentHandler(), new Metadata(),
                    extractor.context());
        }
        assertEquals(2, extractor.contents.size());
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
        assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(1));
    }

    @Test
    public void testLanguageVariants() throws Exception {
        RecordingExtractor extractor = parse(readTestResource(LANG_DLL));
        assertEquals(2, extractor.contents.size());
        // 1031 sorts before 1033 in the language directory
        assertIcon(extractor.metadata.get(0), "icon_1_1031.ico", "14/1/1031", THUMBNAIL);
        assertIcon(extractor.metadata.get(1), "icon_1_1033.ico", "14/1/1033", ATTACHMENT);
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
        assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(1));
    }

    /**
     * A group whose language has no matching icons falls back to whatever
     * language the icons come in: icons 4 and 5 are relabelled from 1031 to
     * 1033 (their language entries at 0x10b0 and 0x10c8), the 1031 group
     * must still find them.
     */
    @Test
    public void testLanguageFallback() throws Exception {
        byte[] dll = readTestResource(LANG_DLL);
        putIntLE(dll, 0x10b0, 1033);
        putIntLE(dll, 0x10c8, 1033);
        RecordingExtractor extractor = parse(dll);
        assertEquals(2, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1_1031.ico", "14/1/1031", THUMBNAIL);
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
    }

    /**
     * The GRPICONDIR's BytesInRes is not trusted; the icon resource's own
     * size is. Corrupting BytesInRes of DOCICON's first entry (0x7b18 + 6 + 8)
     * must not change the output.
     */
    @Test
    public void testGroupSizeFieldIsIgnored() throws Exception {
        byte[] exe = readTestResource(EXE);
        putIntLE(exe, 0x7b26, 0x00ffffff);
        RecordingExtractor extractor = parse(exe);
        assertEquals(2, extractor.contents.size());
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
    }

    /**
     * A group that lists the same icon twice could inflate to 256 times the
     * icon; it is dropped and the next group takes the thumbnail slot.
     */
    @Test
    public void testDuplicateIconIdInGroupIsRejected() throws Exception {
        byte[] exe = readTestResource(EXE);
        // nID of DOCICON's second entry (0x7b18 + 6 + 14 + 12) := id of the first (4)
        exe[0x7b38] = 4;
        exe[0x7b39] = 0;
        RecordingExtractor extractor = parse(exe);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1.ico", "14/1/1033", THUMBNAIL);
        assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(0));
    }

    /**
     * Icon 1's data entry (0x3530) is pointed outside the resource section;
     * group 1 loses an image and is dropped, DOCICON is unaffected.
     */
    @Test
    public void testDataOutsideSectionIsSkipped() throws Exception {
        byte[] exe = readTestResource(EXE);
        putIntLE(exe, 0x3530, 0x1000);
        RecordingExtractor extractor = parse(exe);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    /**
     * The RT_ICON type entry (0x3414) is pointed back at the root directory.
     */
    @Test
    public void testCycleInResourceTree() throws Exception {
        byte[] exe = readTestResource(EXE);
        putIntLE(exe, 0x3414, 0x80000000);
        RecordingExtractor extractor = parse(exe);
        assertEquals(0, extractor.contents.size());
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
        assertEquals(ExecutableParser.MACHINE_x86_32,
                extractor.parentMetadata.get(ExecutableParser.MACHINE_TYPE));
    }

    /**
     * Resource tree offsets are relative to the tree's root, which does not
     * have to be the start of the section. The real .rsrc is moved 0x300
     * bytes into a synthetic section.
     */
    @Test
    public void testResourceRootNotAtSectionStart() throws Exception {
        for (int shift : new int[]{0, 0x300}) {
            byte[] exe = readTestResource(EXE);
            byte[] rsrc = Arrays.copyOfRange(exe, 0x3400, 0x7c00);
            // the seven data entries carry RVAs; rebase them from RVA 0xa000 to the new place
            for (int entry = 0x130; entry <= 0x190; entry += 16) {
                int rva = EndianUtils.getIntLE(rsrc, entry);
                putIntLE(rsrc, entry, rva - 0xa000 + SyntheticPE.SECTION_VA + shift);
            }
            byte[] section = new byte[shift + rsrc.length];
            System.arraycopy(rsrc, 0, section, shift, rsrc.length);
            RecordingExtractor extractor = parse(SyntheticPE.build(section, shift));
            assertEquals(2, extractor.contents.size(), "shift " + shift);
            assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
            assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(1));
        }
    }

    /**
     * A directory with more entries than the budget allows stops the walk
     * with a warning instead of an exception.
     */
    @Test
    public void testDirectoryEntryBudget() throws Exception {
        int entries = 30000;
        ByteBuffer rsrc = ByteBuffer.allocate(16 + 8 + 16 + 8 * entries)
                .order(ByteOrder.LITTLE_ENDIAN);
        // root: one id entry, type RT_ICON, pointing at the subdirectory at 24
        rsrc.position(12).putShort((short) 0).putShort((short) 1);
        rsrc.putInt(3).putInt(0x80000000 | 24);
        // subdirectory with too many entries, none of them leading anywhere
        rsrc.position(24 + 12).putShort((short) 0).putShort((short) entries);
        for (int i = 0; i < entries; i++) {
            rsrc.putInt(i).putInt(0);
        }
        RecordingExtractor extractor = parse(SyntheticPE.build(rsrc.array(), 0));
        assertEquals(0, extractor.contents.size());
        assertContains("20000 entries",
                extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * Subdirectories below the language level are malformed and must stop the
     * walk there: a chain of 25000 nested directories under one language entry
     * would otherwise be followed to the end and exhaust the entry budget.
     */
    @Test
    public void testDepthCapBelowLanguageLevel() throws Exception {
        int chain = 25000;
        int dirSize = 16 + 8;
        ByteBuffer rsrc = ByteBuffer.allocate(dirSize * (3 + chain)).order(ByteOrder.LITTLE_ENDIAN);
        // root -> type RT_ICON -> name 1 -> language dir, then the chain
        for (int i = 0; i < 3 + chain; i++) {
            int dir = i * dirSize;
            rsrc.position(dir + 12).putShort((short) 0).putShort((short) 1);
            rsrc.putInt(i == 0 ? 3 : 1).putInt(0x80000000 | (dir + dirSize));
        }
        RecordingExtractor extractor = parse(SyntheticPE.build(rsrc.array(), 0));
        assertEquals(0, extractor.contents.size());
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    /**
     * A file without icon types must not have its resource section read; the
     * synthetic section declares 1 MB behind a directory that only lists bitmaps.
     */
    @Test
    public void testSectionIsReadLazily() throws Exception {
        ByteBuffer rsrc = ByteBuffer.allocate(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        rsrc.position(12).putShort((short) 0).putShort((short) 1);
        rsrc.putInt(2).putInt(0x80000000 | 24); // RT_BITMAP
        byte[] pe = SyntheticPE.build(rsrc.array(), 0);
        CountingInputStream counting = new CountingInputStream(new ByteArrayInputStream(pe));
        RecordingExtractor extractor = new RecordingExtractor();
        try (TikaInputStream tis = TikaInputStream.get(counting)) {
            new ExecutableParser().parse(tis, new BodyContentHandler(), new Metadata(),
                    extractor.context());
        }
        assertEquals(0, extractor.contents.size());
        // TikaInputStream buffers in 8 KB blocks; the point is that the megabyte stays unread
        assertTrue(counting.getByteCount() < SyntheticPE.SECTION_RAW_PTR + 64 * 1024,
                "read " + counting.getByteCount() + " bytes");
    }

    @Test
    public void testExtractorMayDecline() throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        extractor.accept = false;
        parse(readTestResource(EXE), extractor);
        assertEquals(2, extractor.metadata.size(), "asked about both groups");
        assertEquals(0, extractor.contents.size());
    }

    /**
     * Limits configured by the caller must surface, not be swallowed as a
     * broken resource section.
     */
    @Test
    public void testEmbeddedLimitSurfaces() throws Exception {
        ParseContext context = new ParseContext();
        context.set(EmbeddedLimits.class, new EmbeddedLimits(-1, false, 1, true));
        context.set(ParseRecord.class, ParseRecord.newInstance(context));
        context.set(ParseTimeout.class, ParseTimeout.start(new TimeoutLimits(60_000, 60_000)));
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + EXE)) {
            assertThrows(EmbeddedLimitReachedException.class, () -> AUTO_DETECT_PARSER
                    .parse(tis, new BodyContentHandler(), new Metadata(), context));
        }
    }

    @Test
    public void testExeWithoutIcons() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWindows-x86-32.exe");
        assertEquals(1, metadataList.size());
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    @Test
    public void testExtractIconsDisabled() throws Exception {
        ExecutableParser parser = new ExecutableParser();
        parser.setExtractIcons(false);
        List<Metadata> metadataList = getRecursiveMetadata(EXE, parser);
        assertEquals(1, metadataList.size());
        assertEquals(ExecutableParser.MACHINE_x86_32,
                metadataList.get(0).get(ExecutableParser.MACHINE_TYPE));
    }

    /**
     * The pre-4.1.1 entry point still yields the metadata, just no icons.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyParsePE() throws Exception {
        Metadata metadata = new Metadata();
        byte[] exe = readTestResource(EXE);
        try (ByteArrayInputStream is = new ByteArrayInputStream(exe, 4, exe.length - 4)) {
            new ExecutableParser().parsePE(null, metadata, is, Arrays.copyOf(exe, 4));
        }
        assertEquals(ExecutableParser.MACHINE_x86_32, metadata.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("Windows", metadata.get(ExecutableParser.PLATFORM));
    }

    /**
     * Cutting the file inside the resource section yields the icons that are
     * still complete, silently; cutting before it is reported, but never
     * costs the header metadata.
     */
    @Test
    public void testTruncatedFile() throws Exception {
        byte[] full = readTestResource(EXE);
        byte[] docIco = readTestResource(DOC_ICO);
        // length -> expected number of icons
        int[][] cases = {
                {0x200, 0},   // inside the section table
                {0x3400, 0},  // resource section entirely missing
                {0x4000, 0},  // inside icon 2, no group is complete
                {0x7b20, 0},  // inside DOCICON's GRPICONDIR
                {0x7b50, 1},  // inside group 1's GRPICONDIR, DOCICON is complete
                {0x7fff, 2},  // inside .reloc, after the resource section
        };
        for (int[] c : cases) {
            int length = c[0];
            RecordingExtractor extractor = parse(Arrays.copyOf(full, length));
            String msg = "length 0x" + Integer.toHexString(length);
            assertEquals(c[1], extractor.contents.size(), msg);
            assertEquals(ExecutableParser.MACHINE_x86_32,
                    extractor.parentMetadata.get(ExecutableParser.MACHINE_TYPE), msg);
            String recorded = extractor.parentMetadata.get(
                    TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
            if (length < 0x3400) {
                assertNotNull(recorded, msg);
                assertContains("EOFException", recorded);
            } else {
                assertNull(recorded, msg);
            }
            if (c[1] >= 1) {
                assertArrayEquals(docIco, extractor.contents.get(0), msg);
                assertIcon(extractor.metadata.get(0), "icon_DOCICON.ico", "14/DOCICON/1033",
                        THUMBNAIL);
            }
        }
    }

    private static void assertIcon(Metadata metadata, String name, String relationshipId,
                                   String resourceType) {
        assertEquals(name, metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("true", metadata.get(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED));
        assertEquals(PEIconExtractor.ICON_MIME_TYPE, metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(relationshipId, metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID));
        assertEquals(resourceType, metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private static void putIntLE(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    private byte[] readTestResource(String name) throws IOException {
        try (TikaInputStream is = getResourceAsStream("/test-documents/" + name)) {
            return is.readAllBytes();
        }
    }

    private static RecordingExtractor parse(byte[] pe) throws Exception {
        return parse(pe, new RecordingExtractor());
    }

    private static RecordingExtractor parse(byte[] pe, RecordingExtractor extractor)
            throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(pe)) {
            new ExecutableParser().parse(tis, new BodyContentHandler(), extractor.parentMetadata,
                    extractor.context());
        }
        return extractor;
    }

    /**
     * Records what the parser hands over, before any embedded parsing.
     */
    private static class RecordingExtractor implements EmbeddedDocumentExtractor {
        final List<byte[]> contents = new ArrayList<>();
        final List<Metadata> metadata = new ArrayList<>();
        final Metadata parentMetadata = new Metadata();
        boolean accept = true;

        ParseContext context() {
            ParseContext context = new ParseContext();
            context.set(EmbeddedDocumentExtractor.class, this);
            return context;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext context) {
            this.metadata.add(metadata);
            return accept;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext context, boolean outputHtml)
                throws IOException {
            contents.add(stream.readAllBytes());
        }
    }

    /**
     * The smallest PE32 file that gets a resource section past ExecutableParser:
     * DOS stub, PE signature, COFF header, full optional header, one .rsrc
     * section header, and the section itself at {@link #SECTION_RAW_PTR}.
     */
    private static final class SyntheticPE {
        static final int SECTION_VA = 0x1000;
        static final int SECTION_RAW_PTR = 0x200;
        private static final int PE_OFFSET = 0x40;
        private static final int OPT_HEADER_SIZE = 224;

        static byte[] build(byte[] section, int rootOffset) {
            ByteBuffer pe = ByteBuffer.allocate(SECTION_RAW_PTR + section.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            pe.put((byte) 'M').put((byte) 'Z');
            pe.putInt(0x3c, PE_OFFSET);
            pe.position(PE_OFFSET);
            pe.put("PE\0\0".getBytes(StandardCharsets.US_ASCII));
            pe.putShort((short) 0x14c).putShort((short) 1); // machine, one section
            pe.putInt(0).putInt(0).putInt(0);               // timestamp, symbols
            pe.putShort((short) OPT_HEADER_SIZE).putShort((short) 0x102);
            int opt = pe.position();
            pe.putShort((short) 0x10b);                     // PE32
            pe.putInt(opt + 92, 16);                        // NumberOfRvaAndSizes
            pe.putInt(opt + 96 + 2 * 8, SECTION_VA + rootOffset);
            pe.putInt(opt + 96 + 2 * 8 + 4, section.length - rootOffset);
            int sec = opt + OPT_HEADER_SIZE;
            pe.position(sec);
            pe.put(".rsrc\0\0\0".getBytes(StandardCharsets.US_ASCII));
            pe.putInt(section.length).putInt(SECTION_VA);
            pe.putInt(section.length).putInt(SECTION_RAW_PTR);
            pe.position(SECTION_RAW_PTR);
            pe.put(section);
            return pe.array();
        }
    }
}
