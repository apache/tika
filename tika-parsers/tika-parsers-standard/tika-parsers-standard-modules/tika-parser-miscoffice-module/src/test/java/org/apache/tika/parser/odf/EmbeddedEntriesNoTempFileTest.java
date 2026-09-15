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
package org.apache.tika.parser.odf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Random;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.RewindRecordingExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.epub.EpubParser;

/**
 * An entry of an ODF or EPUB container is in the zip already. Rewinding the
 * stream handed to the embedded-document extractor -- which digesting does for
 * every embedded document, and which the ODF parser itself does to detect a
 * picture -- must re-open the entry, not cache a copy and spill it to a temp
 * file. The payload is over the 1 MB a cache keeps in memory, so the difference
 * is observable; it is built at test time by rewriting one entry of a fixture.
 */
public class EmbeddedEntriesNoTempFileTest extends TikaTest {

    private static final int PAYLOAD_LENGTH = 2 * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    public void testOdfPictureIsNotSpooled() throws Exception {
        Path odt = withEntryReplaced("testODTEmbeddedImageLink.odt",
                "Pictures/10000201000001240000006457F5B1D1243E0671.png", "picture.odt");
        RewindRecordingExtractor extractor = parse(odt, new OpenDocumentParser());
        extractor.assertSawLength(PAYLOAD_LENGTH);
        extractor.assertNothingSpooled();
    }

    @Test
    public void testEpubResourceIsNotSpooled() throws Exception {
        Path epub = withEntryReplaced("testEPUB.epub", "OPS/CoverDesign.jpg", "cover.epub");
        RewindRecordingExtractor extractor = parse(epub, new EpubParser());
        extractor.assertSawLength(PAYLOAD_LENGTH);
        extractor.assertNothingSpooled();
    }

    private RewindRecordingExtractor parse(Path file, Parser parser) throws Exception {
        RewindRecordingExtractor extractor = new RewindRecordingExtractor();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(file, metadata)) {
            parser.parse(tis, new DefaultHandler(), metadata, context);
        }
        return extractor;
    }

    /**
     * A copy of the fixture with one entry's bytes replaced by the payload; every
     * other entry is copied raw, so the stored {@code mimetype} entry stays stored.
     */
    private Path withEntryReplaced(String fixture, String entryName, String name)
            throws IOException {
        Path original = tempDir.resolve("original-" + name);
        try (InputStream is = getResourceAsStream("/test-documents/" + fixture)) {
            Files.copy(is, original);
        }
        Path copy = tempDir.resolve(name);
        boolean replaced = false;
        try (ZipFile in = ZipFile.builder().setPath(original).get();
                ZipArchiveOutputStream out = new ZipArchiveOutputStream(copy.toFile())) {
            Enumeration<ZipArchiveEntry> entries = in.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.getName().equals(entryName)) {
                    ZipArchiveEntry big = new ZipArchiveEntry(entryName);
                    big.setMethod(ZipArchiveEntry.DEFLATED);
                    out.putArchiveEntry(big);
                    out.write(payload());
                    out.closeArchiveEntry();
                    replaced = true;
                } else {
                    try (InputStream raw = in.getRawInputStream(entry)) {
                        out.addRawArchiveEntry(entry, raw);
                    }
                }
            }
        }
        if (!replaced) {
            throw new IOException(entryName + " is not in " + fixture);
        }
        return copy;
    }

    /** Incompressible filler, so DEFLATE keeps it at full size. */
    private static byte[] payload() {
        byte[] bytes = new byte[PAYLOAD_LENGTH];
        new Random(4878).nextBytes(bytes);
        return bytes;
    }
}
