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
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.io.input.CountingInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The test executables were built with MinGW from an empty {@code main()}
 * respectively an empty DLL plus a resource script that embeds two icon
 * groups: id 1 (from testWindows-icons-app.ico, 16/32 px BMP + 256 px PNG)
 * and the named group DOCICON (from testWindows-icons-doc.ico, 16/48 px BMP).
 * testWindows-x86-64-icons-lang.dll instead carries group 1 twice, in
 * language 1033 (app.ico) and 1031 (doc.ico). The two .ico files were drawn
 * for these tests, a blue circle and a red square.
 * <p>
 * To rebuild them, on Ubuntu 24.04 with gcc-mingw-w64 13.2.0 (13-win32) and
 * binutils-mingw-w64 2.41.90; the result differs from the files here only in
 * the link timestamp (0x88, in the DLLs also 0xc04) and the checksum (0xd8):
 * <pre>
 * main.c:         int main(void) { return 0; }
 * dll.c:          __declspec(dllexport) int tika_answer(void) { return 42; }
 * icons.rc:       1 ICON "testWindows-icons-app.ico"
 *                 DOCICON ICON "testWindows-icons-doc.ico"
 * icons-lang.rc:  LANGUAGE 9, 1
 *                 1 ICON "testWindows-icons-app.ico"
 *                 LANGUAGE 7, 1
 *                 1 ICON "testWindows-icons-doc.ico"
 *
 * i686-w64-mingw32-windres icons.rc icons32.o
 * i686-w64-mingw32-gcc -Os -s -o testWindows-x86-32-icons.exe main.c icons32.o
 * x86_64-w64-mingw32-windres icons.rc icons64.o
 * x86_64-w64-mingw32-gcc -Os -s -shared -nostdlib -o testWindows-x86-64-icons.dll \
 *         dll.c icons64.o
 * x86_64-w64-mingw32-windres icons-lang.rc icons-lang64.o
 * x86_64-w64-mingw32-gcc -Os -s -shared -nostdlib -o testWindows-x86-64-icons-lang.dll \
 *         dll.c icons-lang64.o
 * </pre>
 * The -rebuilt.ico files hold the same images as the .ico files the resources
 * were compiled from, ordered largest first, which is what the extractor writes.
 *
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
    private static final int ICON_DIR_ENTRY_SIZE = 16;
    private static final String APP_ICO_SOURCE = "testWindows-icons-app.ico";
    private static final String APP_ICO = "testWindows-icons-app-rebuilt.ico";
    private static final String DOC_ICO = "testWindows-icons-doc-rebuilt.ico";

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
     * A reader that takes an icon group for a multi-page image hands out the
     * first entry, so the largest image goes there. The app icon is compiled
     * from a 16, a 32 and a 256 pixel image, in that order; a width of 0 in a
     * directory entry stands for 256.
     */
    @Test
    public void testLargestIconComesFirst() throws Exception {
        assertArrayEquals(new int[]{16, 32, 0}, widths(readTestResource(APP_ICO_SOURCE)),
                "the icon the resources were compiled from");

        RecordingExtractor extractor = parse(readTestResource(EXE));
        assertArrayEquals(new int[]{0, 32, 16}, widths(extractor.contents.get(1)));
    }

    /**
     * A side of 0 stands for anything from 256 up, so only the images tell a
     * 1024 pixel PNG from a 256 pixel bitmap, and an entry that claims 16
     * pixels for a 48 pixel bitmap is not believed either. The images differ
     * in length, which is how the result tells them apart.
     */
    @Test
    public void testOrderFollowsTheImagesNotTheDirectory() throws Exception {
        byte[] pe = groupPe(new int[][]{{0, 32}, {0, 32}, {16, 32}, {32, 32}},
                bitmap(41, 256, 256), png(42, 1024, 1024), bitmap(43, 48, 48),
                bitmap(44, 32, 32));
        RecordingExtractor extractor = parse(pe);
        assertEquals(1, extractor.contents.size());
        assertArrayEquals(new int[]{42, 41, 43, 44}, imageLengths(extractor.contents.get(0)));
    }

    /**
     * Among images of one size the deeper one comes first, and those that
     * agree in both keep the order of the group.
     */
    @Test
    public void testColourDepthBreaksTies() throws Exception {
        byte[] pe = groupPe(new int[][]{{32, 4}, {32, 32}, {32, 8}, {32, 32}},
                bitmap(41, 32, 32), bitmap(42, 32, 32), bitmap(43, 32, 32),
                bitmap(44, 32, 32));
        RecordingExtractor extractor = parse(pe);
        assertEquals(1, extractor.contents.size());
        assertArrayEquals(new int[]{42, 44, 43, 41}, imageLengths(extractor.contents.get(0)));
    }

    /**
     * A size no image can have - sides beyond what PNG allows, a negative
     * width, no height at all - is not sorted by, and neither is what follows
     * a PNG signature without an IHDR chunk; the directory entry is. The
     * entries rank the images the other way round than those sizes would.
     */
    @Test
    public void testImplausibleImageSizeFallsBackToTheDirectory() throws Exception {
        byte[] noIhdr = png(45, 1024, 1024);
        noIhdr[12] = 'J';
        byte[] pe = groupPe(new int[][]{{16, 32}, {8, 32}, {48, 32}, {32, 32}, {24, 32}},
                bitmap(44, 16, 16), noIhdr, png(41, -1, -1), bitmap(42, -4096, 4096),
                bitmap(43, 4096, 0));
        RecordingExtractor extractor = parse(pe);
        assertEquals(1, extractor.contents.size());
        assertArrayEquals(new int[]{41, 42, 43, 44, 45},
                imageLengths(extractor.contents.get(0)));
    }

    /** The width of every directory entry of an .ico, in file order. */
    private static int[] widths(byte[] ico) {
        int[] widths = new int[ico[4] & 0xff];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = ico[6 + i * ICON_DIR_ENTRY_SIZE] & 0xff;
        }
        return widths;
    }

    /**
     * The resource compiler copies the images verbatim, so the rebuilt .ico
     * files hold the images of the ones that went in, ordered largest first.
     * Checks the metadata as handed to the extractor, before any re-detection.
     */
    @Test
    public void testReconstructedIcoMatchesTheSource() throws Exception {
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
     * Two ids can name one image: icon 5's data entry (0x3570) is pointed at
     * icon 4's data (0x3560), so DOCICON lists the same bytes twice under
     * different ids. It is dropped like a repeated id.
     */
    @Test
    public void testSharedImageDataInGroupIsRejected() throws Exception {
        byte[] exe = readTestResource(EXE);
        System.arraycopy(exe, 0x3560, exe, 0x3570, 8);
        RecordingExtractor extractor = parse(exe);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1.ico", "14/1/1033", THUMBNAIL);
        assertArrayEquals(readTestResource(APP_ICO), extractor.contents.get(0));
    }

    /**
     * Images may not share bytes at all: icon 5's data entry (0x3570) starts
     * one byte into icon 4's data.
     */
    @Test
    public void testOverlappingImagesInGroupAreRejected() throws Exception {
        byte[] exe = readTestResource(EXE);
        putIntLE(exe, 0x3570, EndianUtils.getIntLE(exe, 0x3560) + 1);
        putIntLE(exe, 0x3574, EndianUtils.getIntLE(exe, 0x3564));
        RecordingExtractor extractor = parse(exe);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1.ico", "14/1/1033", THUMBNAIL);
    }

    /**
     * What goes wrong while an icon is handed on is not a broken resource
     * section: it reaches the caller like any other embedded document's failure.
     */
    @Test
    public void testEmbeddedFailureSurfaces() throws Exception {
        RecordingExtractor extractor = new RecordingExtractor() {
            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) throws IOException {
                throw new EOFException("embedded failed");
            }
        };
        assertThrows(EOFException.class, () -> parse(readTestResource(EXE), extractor));
        assertNull(extractor.parentMetadata.get(
                TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    /**
     * Any number of groups can share one image, each rebuilding it into a file
     * of its own. Together they may not outgrow a small multiple of the
     * section they were read from; extraction stops there with a warning.
     */
    @Test
    public void testOutputBudgetAcrossGroups() throws Exception {
        int groups = 40;
        int imageSize = 4096;
        byte[] pe = sharedImagePe(groups, imageSize);
        RecordingExtractor extractor = parse(pe);
        long emitted = 0;
        for (byte[] ico : extractor.contents) {
            emitted += ico.length;
        }
        assertTrue(emitted > imageSize, "emitted " + emitted);
        assertTrue(emitted <= 4L * (pe.length - SyntheticPE.SECTION_RAW_PTR), "emitted " + emitted);
        assertTrue(extractor.contents.size() < groups);
        assertContains("stopped early",
                extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * The entry budget is spent on the groups first: 12000 icons take more
     * than the 20000 entries allowed, yet the group whose icon was reached
     * before the budget ran out still comes out.
     */
    @Test
    public void testGroupsAreReadBeforeIcons() throws Exception {
        RecordingExtractor extractor = parse(manyIconsPe());
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1.ico", "14/1/1033", THUMBNAIL);
        assertContains("20000 entries",
                extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * A group may list 256 images, not 257.
     */
    @Test
    public void testIconsPerGroupLimit() throws Exception {
        int[] ids = new int[257];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1;
        }
        byte[] tooMany = grpIconDir(ids);
        byte[] allowed = grpIconDir(Arrays.copyOf(ids, 256));
        byte[] data = new byte[tooMany.length + allowed.length + ids.length];
        System.arraycopy(tooMany, 0, data, 0, tooMany.length);
        System.arraycopy(allowed, 0, data, tooMany.length, allowed.length);
        SyntheticResources resources = new SyntheticResources(data);
        resources.group(0, tooMany.length);
        resources.group(tooMany.length, allowed.length);
        for (int i = 0; i < ids.length; i++) {
            resources.icon(tooMany.length + allowed.length + i, 1);
        }
        RecordingExtractor extractor = parse(SyntheticPE.build(resources.build(), 0));
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_2.ico", "14/2/1033", THUMBNAIL);
        assertEquals(6 + 256 * 16 + 256, extractor.contents.get(0).length);
    }

    /**
     * A group whose images add up to more than 64 MB is skipped on its own;
     * the group after it still comes out and nothing is reported. The three
     * 23 MB images lie in the hole of a sparse file, which only a file-backed
     * parse can reach.
     */
    @Test
    public void testOversizedGroupIsSkipped(@TempDir Path tmp) throws Exception {
        int imageSize = 23 * 1024 * 1024;
        byte[] oversized = grpIconDir(1, 2, 3);
        byte[] small = grpIconDir(4);
        int images = oversized.length + small.length;
        byte[] data = new byte[images + 16];
        System.arraycopy(oversized, 0, data, 0, oversized.length);
        System.arraycopy(small, 0, data, oversized.length, small.length);
        SyntheticResources resources = new SyntheticResources(data);
        resources.group(0, oversized.length);
        resources.group(oversized.length, small.length);
        for (int i = 0; i < 3; i++) {
            resources.icon(data.length + i * imageSize, imageSize);
        }
        resources.icon(images, 16);
        byte[] section = resources.build();
        int declared = section.length + 3 * imageSize;
        Path file = Files.write(tmp.resolve("oversized.exe"),
                SyntheticPE.build(section, 0, declared));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.allocate(1), SyntheticPE.SECTION_RAW_PTR + declared - 1);
        }
        RecordingExtractor extractor = parseFile(file);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_2.ico", "14/2/1033", THUMBNAIL);
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * A resource name ends up in a file name and in an id that is separated
     * by slashes: DOCICON's I (0x3528) becomes a slash.
     */
    @Test
    public void testResourceNameIsCleaned() throws Exception {
        byte[] exe = readTestResource(EXE);
        exe[0x3528] = '/';
        RecordingExtractor extractor = parse(exe);
        assertEquals(2, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_DOC_CON.ico", "14/DOC_CON/1033", THUMBNAIL);
    }

    /**
     * A name can spell an id: DOCICON is renamed to "1" (length at 0x3520,
     * characters from 0x3522) next to the group with id 1. Only one of the
     * two can be told apart by name and id, so only one comes out.
     */
    @Test
    public void testNameThatSpellsAnId() throws Exception {
        byte[] exe = readTestResource(EXE);
        exe[0x3520] = 1;
        exe[0x3522] = '1';
        RecordingExtractor extractor = parse(exe);
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_1.ico", "14/1/1033", THUMBNAIL);
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
    }

    /**
     * A stream that starts after the four bytes the caller has already read
     * is all the public entry point promises; the section must still be found.
     */
    @Test
    public void testStreamStartingAfterFirstFourBytes() throws Exception {
        byte[] exe = readTestResource(EXE);
        RecordingExtractor extractor = new RecordingExtractor();
        ParseContext context = extractor.context();
        try (TikaInputStream tis = TikaInputStream.get(Arrays.copyOfRange(exe, 4, exe.length))) {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(new BodyContentHandler(),
                    extractor.parentMetadata, context);
            new ExecutableParser().parsePE(xhtml, extractor.parentMetadata, tis,
                    Arrays.copyOf(exe, 4), context);
        }
        assertEquals(2, extractor.contents.size());
        assertArrayEquals(readTestResource(DOC_ICO), extractor.contents.get(0));
    }

    /**
     * A section larger than what is buffered from a stream still yields the
     * icons within reach; one beyond it is reported. A file has no such limit.
     */
    @Test
    public void testSectionLargerThanBuffer(@TempDir Path tmp) throws Exception {
        int declared = 100 * 1024 * 1024;
        byte[] rsrc = resourceSectionAt(SyntheticPE.SECTION_VA);
        byte[] pe = SyntheticPE.build(rsrc, 0, declared);
        RecordingExtractor extractor = parse(pe);
        assertEquals(2, extractor.contents.size());
        assertNull(extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        extractor = parseFile(Files.write(tmp.resolve("large-section.exe"), pe));
        assertEquals(2, extractor.contents.size());

        // icon 1 (data entry 0x130) now lies 70 MB into the section
        putIntLE(rsrc, 0x130, SyntheticPE.SECTION_VA + 70 * 1024 * 1024);
        extractor = parse(SyntheticPE.build(rsrc, 0, declared));
        assertEquals(1, extractor.contents.size());
        assertIcon(extractor.metadata.get(0), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertContains("64 MB",
                extractor.parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * A file that ends before its resource section is reported the same way
     * whether it is read from a file or from a stream.
     */
    @Test
    public void testFileBackedTruncation(@TempDir Path tmp) throws Exception {
        RecordingExtractor extractor = parseFile(Files.write(tmp.resolve("truncated.exe"),
                Arrays.copyOf(readTestResource(EXE), 0x3000)));
        assertEquals(0, extractor.contents.size());
        assertEquals(ExecutableParser.MACHINE_x86_32,
                extractor.parentMetadata.get(ExecutableParser.MACHINE_TYPE));
        assertContains("EOFException", extractor.parentMetadata.get(
                TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    /**
     * A file and a stream take different paths through the resource section.
     * Whatever the input, they must come to the same icons and the same notes.
     */
    @Test
    public void testFileBackedInputBehavesLikeStream(@TempDir Path tmp) throws Exception {
        byte[] exe = readTestResource(EXE);
        Map<String, byte[]> inputs = new LinkedHashMap<>();
        inputs.put("exe", exe);
        inputs.put("dll", readTestResource(DLL));
        inputs.put("language variants", readTestResource(LANG_DLL));
        inputs.put("cycle", patched(exe, 0x3414, 0x80000000));
        inputs.put("data outside the section", patched(exe, 0x3530, 0x1000));
        inputs.put("images sharing bytes",
                patched(exe, 0x3570, EndianUtils.getIntLE(exe, 0x3560) + 1));
        inputs.put("name with a slash", patched(exe, 0x3528, '/'));
        for (int length : new int[]{0x200, 0x3000, 0x3400, 0x4000, 0x7b20, 0x7b50, 0x7fff}) {
            inputs.put("cut at 0x" + Integer.toHexString(length), Arrays.copyOf(exe, length));
        }
        inputs.put("output budget", sharedImagePe(40, 4096));
        inputs.put("entry budget", manyIconsPe());

        for (Map.Entry<String, byte[]> input : inputs.entrySet()) {
            String label = input.getKey();
            RecordingExtractor stream = parse(input.getValue());
            RecordingExtractor file = parseFile(
                    Files.write(Files.createTempFile(tmp, "pe", ".exe"), input.getValue()));
            assertEquals(stream.contents.size(), file.contents.size(), label);
            for (int i = 0; i < stream.contents.size(); i++) {
                assertArrayEquals(stream.contents.get(i), file.contents.get(i), label);
            }
            assertEquals(stream.metadata.size(), file.metadata.size(), label);
            for (int i = 0; i < stream.metadata.size(); i++) {
                assertEquals(stream.metadata.get(i), file.metadata.get(i), label);
            }
            assertEquals(warning(stream), warning(file), label);
            // the messages differ, what was noticed must not
            assertEquals(stream.parentMetadata.get(
                            TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM) == null,
                    file.parentMetadata.get(
                            TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM) == null,
                    label);
        }
    }

    /**
     * The section buffer follows what was read, not what the header declares:
     * 64 MB declared, 100 bytes there.
     */
    @Test
    public void testSectionBufferFollowsBytesRead() throws Exception {
        int declared = 64 * 1024 * 1024;
        PEIconExtractor.BufferedSection section = new PEIconExtractor.BufferedSection(
                new ByteArrayInputStream(new byte[100]), declared);
        assertNull(section.read(declared - 16, 16));
        assertTrue(section.buf.length <= 64 * 1024, "allocated " + section.buf.length);
        assertEquals(90, section.read(10, 90).length);
    }

    /**
     * A failing source is not a broken resource section: it fails the parse
     * instead of being noted as an embedded problem.
     */
    @Test
    public void testSourceFailureSurfaces() throws Exception {
        InputStream failing = new FilterInputStream(
                new ByteArrayInputStream(readTestResource(EXE))) {
            // gives out before the resource section at 0x3400
            private long remaining = 0x3000;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) {
                    throw new IOException("source failed");
                }
                int n = super.read(b, off, (int) Math.min(len, remaining));
                remaining -= Math.max(n, 0);
                return n;
            }

            @Override
            public long skip(long n) throws IOException {
                if (remaining <= 0) {
                    throw new IOException("source failed");
                }
                long skipped = super.skip(Math.min(n, remaining));
                remaining -= skipped;
                return skipped;
            }
        };
        RecordingExtractor extractor = new RecordingExtractor();
        try (TikaInputStream tis = TikaInputStream.get(failing)) {
            assertThrows(IOException.class, () -> new ExecutableParser().parse(tis,
                    new BodyContentHandler(), extractor.parentMetadata, extractor.context()));
        }
        assertNull(extractor.parentMetadata.get(
                TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
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
            byte[] rsrc = resourceSectionAt(SyntheticPE.SECTION_VA + shift);
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
        ExecutableParser.ExecutableParserConfig noIcons =
                new ExecutableParser.ExecutableParserConfig();
        noIcons.setExtractIcons(false);

        List<Metadata> metadataList = getRecursiveMetadata(EXE, new ExecutableParser(noIcons));
        assertEquals(1, metadataList.size());
        assertEquals(ExecutableParser.MACHINE_x86_32,
                metadataList.get(0).get(ExecutableParser.MACHINE_TYPE));

        // for one parse, through the context
        RecordingExtractor extractor = new RecordingExtractor();
        ParseContext context = extractor.context();
        context.set(ExecutableParser.ExecutableParserConfig.class, noIcons);
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + EXE)) {
            new ExecutableParser().parse(tis, new BodyContentHandler(), new Metadata(), context);
        }
        assertEquals(0, extractor.metadata.size());

        // from a JSON configuration
        Parser loaded = TikaLoader.load(getConfigPath(PEIconExtractorTest.class,
                "tika-config-executable-no-icons.json")).loadParsers();
        assertEquals(1, getRecursiveMetadata(EXE, loaded).size());
        assertEquals(3, getRecursiveMetadata(EXE).size());
    }

    /**
     * A request to tika-server or through pipes carries its configuration as
     * JSON under the parser's name, not as an object under its class.
     */
    @Test
    public void testExtractIconsPerRequestJson() throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        ParseContext context = extractor.context();
        context.setJsonConfig("executable-parser", "{\"extractIcons\": false}");
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + EXE)) {
            new ExecutableParser().parse(tis, new BodyContentHandler(), new Metadata(), context);
        }
        assertEquals(0, extractor.metadata.size());

        // and the other way round, over a parser that was configured without icons
        ExecutableParser.ExecutableParserConfig noIcons =
                new ExecutableParser.ExecutableParserConfig();
        noIcons.setExtractIcons(false);
        extractor = new RecordingExtractor();
        context = extractor.context();
        context.setJsonConfig("executable-parser", "{\"extractIcons\": true}");
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + EXE)) {
            new ExecutableParser(noIcons).parse(tis, new BodyContentHandler(), new Metadata(),
                    context);
        }
        assertEquals(2, extractor.contents.size());
    }

    /**
     * The pre-4.2.0 entry point still yields the metadata, just no icons.
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

    /**
     * @return a GRPICONDIR that lists the icons with the given ids
     */
    private static byte[] grpIconDir(int... ids) {
        ByteBuffer dir = ByteBuffer.allocate(6 + 14 * ids.length).order(ByteOrder.LITTLE_ENDIAN);
        dir.putShort((short) 0).putShort((short) 1).putShort((short) ids.length);
        for (int i = 0; i < ids.length; i++) {
            dir.putShort(6 + 14 * i + 12, (short) ids[i]);
        }
        return dir.array();
    }

    /**
     * @param entries the side and the bit count each directory entry claims
     * @return a file with one group of the given images, in the given order
     */
    private static byte[] groupPe(int[][] entries, byte[]... images) {
        byte[] dir = grpIconDir(IntStream.rangeClosed(1, images.length).toArray());
        int size = dir.length;
        for (byte[] image : images) {
            size += image.length;
        }
        ByteBuffer data = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        SyntheticResources resources = new SyntheticResources(data.array());
        resources.group(0, dir.length);
        data.put(dir);
        for (int i = 0; i < images.length; i++) {
            data.put(6 + 14 * i, (byte) entries[i][0]).put(6 + 14 * i + 1, (byte) entries[i][0]);
            data.putShort(6 + 14 * i + 6, (short) entries[i][1]);
            resources.icon(data.position(), images[i].length);
            data.put(images[i]);
        }
        return SyntheticPE.build(resources.build(), 0);
    }

    /**
     * @return the start of a PNG of the given size, padded to the given length
     */
    private static byte[] png(int length, int width, int height) {
        ByteBuffer png = ByteBuffer.allocate(length);
        png.putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452);
        png.putInt(width).putInt(height);
        return png.array();
    }

    /**
     * @return the BITMAPINFOHEADER of an icon image of the given size, its
     * height doubled for the mask, padded to the given length
     */
    private static byte[] bitmap(int length, int width, int height) {
        ByteBuffer bitmap = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        bitmap.putInt(40).putInt(width).putInt(2 * height);
        return bitmap.array();
    }

    /** The length of every image of an .ico, in file order. */
    private static int[] imageLengths(byte[] ico) {
        int[] lengths = new int[ico[4] & 0xff];
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = EndianUtils.getIntLE(ico, 6 + i * ICON_DIR_ENTRY_SIZE + 8);
        }
        return lengths;
    }

    /**
     * @return a file in which the given number of groups all list one image
     */
    private static byte[] sharedImagePe(int groups, int imageSize) {
        byte[] data = new byte[20 + imageSize];
        System.arraycopy(grpIconDir(1), 0, data, 0, 20);
        SyntheticResources resources = new SyntheticResources(data);
        for (int i = 0; i < groups; i++) {
            resources.group(0, 20);
        }
        resources.icon(20, imageSize);
        return SyntheticPE.build(resources.build(), 0);
    }

    /**
     * @return a file with one group and 12000 icons, more than the entry
     * budget of 20000 lets the walk visit
     */
    private static byte[] manyIconsPe() {
        byte[] data = new byte[20 + 16];
        System.arraycopy(grpIconDir(1), 0, data, 0, 20);
        SyntheticResources resources = new SyntheticResources(data);
        resources.group(0, 20);
        for (int i = 0; i < 12000; i++) {
            resources.icon(20, 16);
        }
        return SyntheticPE.build(resources.build(), 0);
    }

    /**
     * @return the resource section of the test EXE, its seven data entries
     * rebased from RVA 0xa000 to the given one
     */
    private byte[] resourceSectionAt(int rva) throws IOException {
        byte[] rsrc = Arrays.copyOfRange(readTestResource(EXE), 0x3400, 0x7c00);
        for (int entry = 0x130; entry <= 0x190; entry += 16) {
            putIntLE(rsrc, entry, EndianUtils.getIntLE(rsrc, entry) - 0xa000 + rva);
        }
        return rsrc;
    }

    /**
     * @return the warning's message without the stack trace that follows it
     */
    private static String warning(RecordingExtractor extractor) {
        String warning = extractor.parentMetadata.get(
                TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        return warning == null ? null : warning.lines().findFirst().orElse("");
    }

    private static byte[] patched(byte[] data, int offset, int value) {
        byte[] patched = data.clone();
        putIntLE(patched, offset, value);
        return patched;
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

    private static RecordingExtractor parseFile(Path file) throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        try (TikaInputStream tis = TikaInputStream.get(file)) {
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
     * A resource section whose icons and icon groups are numbered from 1 in
     * the order they are added, all in language 1033, their data being ranges
     * of one block that follows the tree.
     */
    private static final class SyntheticResources {
        private static final int DIR_SIZE = 16 + 8;
        private final List<int[]> icons = new ArrayList<>();
        private final List<int[]> groups = new ArrayList<>();
        private final byte[] data;

        SyntheticResources(byte[] data) {
            this.data = data;
        }

        void icon(int offset, int size) {
            icons.add(new int[]{offset, size});
        }

        void group(int offset, int size) {
            groups.add(new int[]{offset, size});
        }

        byte[] build() {
            int iconTypeDir = 16 + 2 * 8;
            int iconLangDirs = iconTypeDir + 16 + 8 * icons.size();
            int groupTypeDir = iconLangDirs + DIR_SIZE * icons.size();
            int groupLangDirs = groupTypeDir + 16 + 8 * groups.size();
            int dataEntries = groupLangDirs + DIR_SIZE * groups.size();
            int block = dataEntries + 16 * (icons.size() + groups.size());
            ByteBuffer rsrc = ByteBuffer.allocate(block + data.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            rsrc.position(12).putShort((short) 0).putShort((short) 2);
            rsrc.putInt(3).putInt(0x80000000 | iconTypeDir);
            rsrc.putInt(14).putInt(0x80000000 | groupTypeDir);
            writeType(rsrc, iconTypeDir, iconLangDirs, dataEntries, icons, block);
            writeType(rsrc, groupTypeDir, groupLangDirs, dataEntries + 16 * icons.size(), groups,
                    block);
            rsrc.position(block);
            rsrc.put(data);
            return rsrc.array();
        }

        private static void writeType(ByteBuffer rsrc, int typeDir, int langDirs, int dataEntries,
                                      List<int[]> resources, int block) {
            rsrc.position(typeDir + 12).putShort((short) 0).putShort((short) resources.size());
            for (int i = 0; i < resources.size(); i++) {
                rsrc.putInt(i + 1).putInt(0x80000000 | (langDirs + i * DIR_SIZE));
            }
            for (int i = 0; i < resources.size(); i++) {
                rsrc.position(langDirs + i * DIR_SIZE + 12).putShort((short) 0).putShort((short) 1);
                rsrc.putInt(1033).putInt(dataEntries + i * 16);
                rsrc.position(dataEntries + i * 16);
                rsrc.putInt(SyntheticPE.SECTION_VA + block + resources.get(i)[0]);
                rsrc.putInt(resources.get(i)[1]);
            }
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
            return build(section, rootOffset, section.length);
        }

        /**
         * @param declaredSize the section size the header claims, whatever the section holds
         */
        static byte[] build(byte[] section, int rootOffset, int declaredSize) {
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
            pe.putInt(opt + 96 + 2 * 8 + 4, declaredSize - rootOffset);
            int sec = opt + OPT_HEADER_SIZE;
            pe.position(sec);
            pe.put(".rsrc\0\0\0".getBytes(StandardCharsets.US_ASCII));
            pe.putInt(declaredSize).putInt(SECTION_VA);
            pe.putInt(declaredSize).putInt(SECTION_RAW_PTR);
            pe.position(SECTION_RAW_PTR);
            pe.put(section);
            return pe.array();
        }
    }
}
