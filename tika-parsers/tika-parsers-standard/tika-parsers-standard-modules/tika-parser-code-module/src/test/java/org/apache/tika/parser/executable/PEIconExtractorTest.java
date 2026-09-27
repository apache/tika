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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * The test executables were built with MinGW from an empty {@code main()}
 * respectively an empty DLL plus a resource script that embeds two icon
 * groups: id 1 (from testWindows-icons-app.ico, 16/32 px BMP + 256 px PNG)
 * and the named group DOCICON (from testWindows-icons-doc.ico, 16/48 px BMP).
 */
public class PEIconExtractorTest extends TikaTest {

    private static final String THUMBNAIL =
            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString();
    private static final String ATTACHMENT =
            TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString();

    @Test
    public void testIconsFromExe() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWindows-x86-32-icons.exe");
        assertEquals(3, metadataList.size());

        Metadata exe = metadataList.get(0);
        assertEquals("application/x-msdownload", exe.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(ExecutableParser.MACHINE_x86_32, exe.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("32", exe.get(ExecutableParser.ARCHITECTURE_BITS));
        assertNull(exe.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));

        // Named entries precede numeric ids in the resource directory, so the
        // DOCICON group is the first one and therefore the file's own icon
        assertIcon(metadataList.get(1), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertIcon(metadataList.get(2), "icon_1.ico", "14/1/1033", ATTACHMENT);
    }

    @Test
    public void testIconsFromDll() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWindows-x86-64-icons.dll");
        assertEquals(3, metadataList.size());

        Metadata dll = metadataList.get(0);
        assertEquals("application/x-msdownload", dll.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(ExecutableParser.MACHINE_x86_64, dll.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("64", dll.get(ExecutableParser.ARCHITECTURE_BITS));

        assertIcon(metadataList.get(1), "icon_DOCICON.ico", "14/DOCICON/1033", THUMBNAIL);
        assertIcon(metadataList.get(2), "icon_1.ico", "14/1/1033", ATTACHMENT);
    }

    /**
     * The resource compiler copies the images verbatim, so the rebuilt
     * .ico files must be identical to the ones that went in.
     */
    @Test
    public void testReconstructedIcoIsByteIdentical() throws Exception {
        for (String file : new String[]{"testWindows-x86-32-icons.exe",
                "testWindows-x86-64-icons.dll"}) {
            RecordingExtractor extractor = new RecordingExtractor();
            ParseContext context = new ParseContext();
            context.set(EmbeddedDocumentExtractor.class, extractor);
            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + file)) {
                AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(), new Metadata(), context);
            }
            assertEquals(2, extractor.contents.size(), file);
            assertArrayEquals(readTestResource("testWindows-icons-doc.ico"),
                    extractor.contents.get(0), file);
            assertArrayEquals(readTestResource("testWindows-icons-app.ico"),
                    extractor.contents.get(1), file);
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
        List<Metadata> metadataList = getRecursiveMetadata("testWindows-x86-32-icons.exe", parser);
        assertEquals(1, metadataList.size());
        assertEquals(ExecutableParser.MACHINE_x86_32,
                metadataList.get(0).get(ExecutableParser.MACHINE_TYPE));
    }

    /**
     * Cutting the file in the middle of the resource section must neither
     * throw nor lose the header metadata.
     */
    @Test
    public void testTruncatedResourceSection() throws Exception {
        byte[] full = readTestResource("testWindows-x86-32-icons.exe");
        for (int length : new int[]{0x200, 0xa000, 0xb000, 0xe720, full.length - 1}) {
            byte[] truncated = Arrays.copyOf(full, length);
            RecordingExtractor extractor = new RecordingExtractor();
            ParseContext context = new ParseContext();
            context.set(EmbeddedDocumentExtractor.class, extractor);
            Metadata metadata = new Metadata();
            try (TikaInputStream tis = TikaInputStream.get(truncated)) {
                new ExecutableParser().parse(tis, new BodyContentHandler(), metadata, context);
            }
            assertEquals(ExecutableParser.MACHINE_x86_32,
                    metadata.get(ExecutableParser.MACHINE_TYPE), "length " + length);
            if (length >= 0xa000) {
                // cut inside the resource section: not an error, just fewer icons
                assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM),
                        "length " + length);
            }
            for (byte[] ico : extractor.contents) {
                // whatever survives must still be a complete icon file
                assertEquals(0, ico[0] | ico[1], "length " + length);
                assertEquals(1, ico[2] | ico[3], "length " + length);
            }
        }
    }

    private static void assertIcon(Metadata metadata, String name, String relationshipId,
                                   String resourceType) {
        assertEquals(name, metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(PEIconExtractor.ICON_MIME_TYPE, metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(relationshipId, metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID));
        assertEquals(resourceType, metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private byte[] readTestResource(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test-documents/" + name)) {
            return is.readAllBytes();
        }
    }

    private static class RecordingExtractor implements EmbeddedDocumentExtractor {
        private final List<byte[]> contents = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext context) {
            return true;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext context, boolean outputHtml)
                throws IOException {
            contents.add(stream.readAllBytes());
        }
    }
}
