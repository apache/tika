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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;

/**
 * An embedded object's bytes are already in its container. Rewinding the
 * stream handed to the embedded-document extractor -- which digesting does for
 * every embedded document -- must re-open the object from the container, not
 * cache a copy of it and spill that copy to a temp file.
 * <p>
 * Each payload is over the 1 MB a cache keeps in memory, so a cached stream has
 * to spill to rewind and the difference is observable. The assertion is on the
 * stream the extractor is handed: the parser owns each child's
 * {@code TemporaryResources}, so a watched directory would pass either way.
 * Fixtures are built at test time by rewriting one part of a small document.
 */
public class EmbeddedObjectsNoTempFileTest extends TikaTest {

    private static final int PAYLOAD_LENGTH = 2 * 1024 * 1024;

    @TempDir
    Path tempDir;

    /** An OOXML part reached through {@code handleEmbeddedFile}: every picture, media
     *  file and attachment in a docx, pptx or xlsx. */
    @Test
    public void testOoxmlPartIsNotSpooled() throws Exception {
        Path docx = copyOf("EmbeddedPDF.docx", "picture.docx");
        overwritePart(docx, "/word/media/image1.emf", payload());
        RecordingExtractor extractor = parse(docx, new OOXMLParser());
        extractor.assertPayloadReadWithoutSpooling();
    }

    /** An OLE 2.0 package inside an OOXML part, reached through {@code handleEmbeddedOLE}:
     *  the container's {@code Package} entry is re-opened from the {@code POIFSFileSystem}. */
    @Test
    public void testOoxmlOlePackageIsNotSpooled() throws Exception {
        Path docx = copyOf("EmbeddedPDF.docx", "ole.docx");
        overwritePart(docx, "/word/embeddings/oleObject1.bin",
                ole2(new String[]{"Ole", "Package"}, new byte[][]{new byte[20], payload()}));
        RecordingExtractor extractor = parse(docx, new OOXMLParser());
        extractor.assertPayloadReadWithoutSpooling();
    }

    /** An OLE 2.0 object in a binary Office file, reached through {@code handleCompObj}:
     *  the {@code CONTENTS} entry is re-opened from its {@code DirectoryEntry}. */
    @Test
    public void testOle2ContentsIsNotSpooled() throws Exception {
        Path xls = tempDir.resolve("contents.xls");
        try (InputStream is = getResourceAsStream("/test-documents/testExcel_embeddedPDF.xls");
                POIFSFileSystem fs = new POIFSFileSystem(is);
                OutputStream out = Files.newOutputStream(xls)) {
            DirectoryEntry object = directoryWith(fs.getRoot(), "CONTENTS");
            object.getEntry("CONTENTS").delete();
            object.createDocument("CONTENTS", new ByteArrayInputStream(payload()));
            fs.writeFilesystem(out);
        }
        RecordingExtractor extractor = parse(xls, new OfficeParser());
        extractor.assertPayloadReadWithoutSpooling();
    }

    private Path copyOf(String fixture, String name) throws IOException {
        Path copy = tempDir.resolve(name);
        try (InputStream is = getResourceAsStream("/test-documents/" + fixture)) {
            Files.copy(is, copy);
        }
        return copy;
    }

    /** Replaces one part's bytes in place; its name, content type and relationships stay. */
    private static void overwritePart(Path docx, String partName, byte[] bytes) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(docx.toFile())) {
            PackagePart part = pkg.getPart(PackagingURIHelper.createPartName(partName));
            try (OutputStream out = part.getOutputStream()) {
                out.write(bytes);
            }
        }
    }

    private RecordingExtractor parse(Path file, Parser parser) throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(file, metadata)) {
            parser.parse(tis, new DefaultHandler(), metadata, context);
        }
        return extractor;
    }

    /** Looks like a PDF to detection and is otherwise filler. */
    private static byte[] payload() {
        byte[] bytes = new byte[PAYLOAD_LENGTH];
        new Random(4878).nextBytes(bytes);
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    private static byte[] ole2(String[] names, byte[][] contents) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            for (int i = 0; i < names.length; i++) {
                fs.getRoot().createDocument(names[i], new ByteArrayInputStream(contents[i]));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    private static DirectoryEntry directoryWith(DirectoryEntry dir, String entryName)
            throws IOException {
        for (Entry entry : dir) {
            if (entry instanceof DirectoryEntry child) {
                if (child.hasEntry(entryName)) {
                    return child;
                }
                try {
                    return directoryWith(child, entryName);
                } catch (IOException notHere) {
                    // keep looking in the siblings
                }
            }
        }
        throw new IOException("no directory holding " + entryName + " under " + dir.getName());
    }

    /**
     * Rewinds each embedded stream the way a digester does, then records whether
     * that left it backed by a temp file and how many bytes it still yields.
     */
    private static class RecordingExtractor implements EmbeddedDocumentExtractor {
        private final List<Boolean> spooled = new ArrayList<>();
        private final List<Integer> lengths = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext context) {
            return true;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext context, boolean outputHtml)
                throws IOException {
            stream.enableRewind();
            stream.readAllBytes();
            stream.rewind();
            spooled.add(stream.hasFile());
            lengths.add(stream.readAllBytes().length);
        }

        void assertPayloadReadWithoutSpooling() {
            assertTrue(lengths.contains(PAYLOAD_LENGTH),
                    "the payload reached the extractor in full; saw " + lengths);
            for (int i = 0; i < spooled.size(); i++) {
                assertEquals(false, spooled.get(i), "embedded stream " + i + " (" + lengths.get(i)
                        + " bytes) was spooled to disk to rewind instead of re-opened");
            }
        }
    }
}
