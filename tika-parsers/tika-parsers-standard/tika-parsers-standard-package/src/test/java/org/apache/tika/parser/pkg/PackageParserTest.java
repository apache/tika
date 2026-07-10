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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class PackageParserTest extends TikaTest {

    @Test
    public void handleNonUnicodeEntryName() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("gbk.zip");
        assertContains("审计压缩", metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void handleEntryNameWithCharsetShiftJIS() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testZipEntryNameCharsetShiftSJIS.zip");
        assertContains("文章", metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("文章", metadataList.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    /**
     * TIKA-4785: PackageParser must hand embedded-document extractors a mark/reset-supporting
     * stream. A .sxc (application/vnd.sun.xml.calc) is a zip that OpenDocumentParser does not
     * claim, so it is handled here; the 3.x ZipFile fast-path (parseZipEntry) regressed by
     * passing the raw, non-markable InflaterInputStream from ZipFile.getInputStream(entry). A
     * file-backed input routes through that fast-path. The default extractor wraps the stream
     * defensively, so we install a custom extractor to observe the stream Tika hands out.
     */
    @Test
    public void embeddedStreamSupportsMarkReset() throws Exception {
        List<Boolean> markSupported = new ArrayList<>();
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler,
                                      Metadata metadata, boolean outputHtml) {
                markSupported.add(stream.markSupported());
            }
        });

        Metadata metadata = new Metadata();
        Path tmpFile = Files.createTempFile("tika4785", ".sxc");
        try {
            try (InputStream is =
                         getResourceAsStream("/test-documents/testStarOffice-6.0-calc.sxc")) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (TikaInputStream tis = TikaInputStream.get(tmpFile)) {
                parser.parse(tis, new DefaultHandler(), metadata, context);
            }
        } finally {
            Files.deleteIfExists(tmpFile);
        }

        assertEquals("application/vnd.sun.xml.calc", metadata.get(Metadata.CONTENT_TYPE));
        assertFalse(markSupported.isEmpty(), "expected at least one embedded entry to be parsed");
        for (boolean supported : markSupported) {
            assertTrue(supported, "embedded stream must support mark/reset (TIKA-4785)");
        }
    }
}
