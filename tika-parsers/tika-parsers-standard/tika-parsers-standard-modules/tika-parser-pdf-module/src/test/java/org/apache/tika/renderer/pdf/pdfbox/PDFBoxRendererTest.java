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
package org.apache.tika.renderer.pdf.pdfbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;

@Isolated // testFailedRenderLeavesNoTempFiles snapshots java.io.tmpdir
public class PDFBoxRendererTest {

    private long renderedPngBytes(ParseContext context) throws Exception {
        PDFBoxRenderer renderer = new PDFBoxRenderer();
        try (InputStream is = getClass().getResourceAsStream("/test-documents/testPDF.pdf");
             TikaInputStream tis = TikaInputStream.get(is)) {
            assertNotNull(is);
            PageBasedRenderResults results = (PageBasedRenderResults) renderer.render(
                    tis, new Metadata(), context, new PageRangeRequest(1, 1));
            RenderResult r = results.getResults().get(0);
            assertEquals(RenderResult.STATUS.SUCCESS, r.getStatus());
            try (TikaInputStream img = r.getInputStream()) {
                byte[] b = img.readAllBytes();
                assertEquals((byte) 0x89, b[0]);
                assertEquals((byte) 'P', b[1]);
                results.close();
                return b.length;
            }
        }
    }

    @Test
    public void testPngCompressedByDefault() throws Exception {
        // letter page, 300 dpi gray: raw raster ~8.5 MB; compressed must be far smaller
        long bytes = renderedPngBytes(new ParseContext());
        assertTrue(bytes < 2_000_000, "default render should be a compressed PNG, got " + bytes);
    }

    @Test
    public void testImageQualityConfigurable() throws Exception {
        // ImageIO's PNG "quality" 1.0 = uncompressed; proves the config reaches the writer
        PDFParserConfig config = new PDFParserConfig();
        config.getOcr().setImageQuality(1.0f);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        long uncompressed = renderedPngBytes(context);
        long compressed = renderedPngBytes(new ParseContext());
        assertTrue(uncompressed > compressed * 4,
                "quality 1.0 should be far larger: " + uncompressed + " vs " + compressed);
    }

    /** An out-of-range page throws past the per-page IOException catch after RENDER_ALL wrote pages. */
    @Test
    public void testFailedRenderLeavesNoTempFiles() throws Exception {
        PDFBoxRenderer renderer = new PDFBoxRenderer();
        Set<String> before = tikaTempEntries();
        try (InputStream is = getClass().getResourceAsStream("/test-documents/testPDF.pdf");
             TikaInputStream tis = TikaInputStream.get(is)) {
            assertThrows(RuntimeException.class, () -> renderer.render(tis, new Metadata(),
                    new ParseContext(), PageRangeRequest.RENDER_ALL, new PageRangeRequest(9999, 9999)));
        }
        Set<String> leaked = tikaTempEntries();
        leaked.removeAll(before);
        assertTrue(leaked.isEmpty(), "temp files left behind: " + leaked);
    }

    private static Set<String> tikaTempEntries() throws IOException {
        Set<String> names = new HashSet<>();
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tmpDir, "tika-pdfbox-rendering-*")) {
            for (Path p : entries) {
                names.add(p.getFileName().toString());
            }
        }
        return names;
    }
}
