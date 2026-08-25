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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;

/**
 * The incremental-update xref scan used to spool in-memory PDFs to a file; it must scan
 * from memory like the main parse does.
 */
public class PDFParserNoTempFileTest extends TikaTest {

    @TempDir
    Path tempDir;

    @Test
    public void testXRefScanNoTempFileForInMemoryInput() throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/testPDF_incrementalUpdates.pdf")) {
            bytes = is.readAllBytes();
        }
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractIncrementalUpdateInfo(true);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            new PDFParser().parse(tis, new DefaultHandler(), metadata, context);
            try (Stream<Path> files = Files.list(tempDir)) {
                assertEquals(0, files.count(), "xref scan spooled an in-memory PDF to disk");
            }
        }
        assertNotNull(metadata.get(PDF.PDF_INCREMENTAL_UPDATE_COUNT), "incremental update info was extracted");
    }
}
