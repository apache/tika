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
package org.apache.tika.parser.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * A PDF attachment's bytes are in the document already. Rewinding the stream
 * handed to the embedded-document extractor -- which digesting does for every
 * embedded document -- must re-open (re-decode) the attachment from the
 * document, not cache a copy of it and spill that copy to a temp file.
 * <p>
 * The payload is over the 1 MB a cache keeps in memory, so a cached stream has
 * to spill to rewind and the difference is observable. The assertion is on the
 * stream the extractor is handed: the parser owns the child's
 * {@code TemporaryResources}, so a watched directory would pass either way.
 */
public class PDFEmbeddedFileNoTempFileTest extends TikaTest {

    private static final int PAYLOAD_LENGTH = 2 * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    public void testAttachmentIsNotSpooled() throws Exception {
        Path pdf = tempDir.resolve("attachment.pdf");
        byte[] payload = payload();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            //Flate-encoded, as real attachments are: a rewind has to re-decode
            PDEmbeddedFile file = new PDEmbeddedFile(doc, new ByteArrayInputStream(payload),
                    COSName.FLATE_DECODE);
            file.setSize(payload.length);
            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile("attachment.bin");
            spec.setEmbeddedFile(file);
            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(Map.of("attachment.bin", spec));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            doc.getDocumentCatalog().setNames(names);
            doc.save(pdf.toFile());
        }

        RecordingExtractor extractor = new RecordingExtractor();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(pdf, metadata)) {
            new PDFParser().parse(tis, new DefaultHandler(), metadata, context);
        }

        assertTrue(extractor.lengths.contains(PAYLOAD_LENGTH),
                "the attachment reached the extractor in full; saw " + extractor.lengths);
        for (int i = 0; i < extractor.spooled.size(); i++) {
            assertEquals(false, extractor.spooled.get(i), "embedded stream " + i + " ("
                    + extractor.lengths.get(i)
                    + " bytes) was spooled to disk to rewind instead of re-opened");
        }
    }

    /** Incompressible filler, so Flate keeps it at full size. */
    private static byte[] payload() {
        byte[] bytes = new byte[PAYLOAD_LENGTH];
        new Random(4878).nextBytes(bytes);
        return bytes;
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
    }
}
