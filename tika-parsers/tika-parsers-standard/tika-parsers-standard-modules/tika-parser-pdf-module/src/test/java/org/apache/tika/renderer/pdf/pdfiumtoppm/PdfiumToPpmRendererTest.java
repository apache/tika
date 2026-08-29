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
package org.apache.tika.renderer.pdf.pdfiumtoppm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.utils.ProcessUtils;

public class PdfiumToPpmRendererTest {

    private static boolean hasPdfiumToPpm;

    @BeforeAll
    static void checkPdfiumToPpm() {
        hasPdfiumToPpm = ProcessUtils.checkCommand(new String[]{"pdfiumtoppm", "-v"});
    }

    @Test
    void testCommandLineDefaults() {
        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        String[] args = renderer.createCommandLine(Path.of("/in/a.pdf"), Path.of("/out"),
                PageRangeRequest.RENDER_ALL);
        assertArrayEquals(new String[]{"pdfiumtoppm", "-png", "-r", "300",
                "-max-pixels", "16777216", "-gray", "/in/a.pdf", "/out/tika-pdfium"}, args);
    }

    @Test
    void testCommandLineAllOptions() {
        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        renderer.setPdfiumToPpmPath("/opt/pdfiumtoppm/pdfiumtoppm");
        renderer.setPdfiumLibraryDir("/opt/pdfiumtoppm");
        renderer.setDpi(72);
        renderer.setGray(false);
        renderer.setMaxScaleTo(2048);
        renderer.setMaxPixels(-1);
        renderer.setMaxMemoryMb(1024);
        String[] args = renderer.createCommandLine(Path.of("/in/a.pdf"), Path.of("/out"),
                new PageRangeRequest(2, 3));
        assertArrayEquals(new String[]{"/opt/pdfiumtoppm/pdfiumtoppm", "-pdfium",
                "/opt/pdfiumtoppm", "-png", "-r", "72", "-scale-to", "2048", "-max-memory",
                "1024", "-f", "2", "-l", "3", "/in/a.pdf", "/out/tika-pdfium"}, args);
    }

    @Test
    void testPpmOutput() throws Exception {
        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        renderer.setPng(false);
        List<String> args = List.of(renderer.createCommandLine(Path.of("/in/a.pdf"),
                Path.of("/out"), PageRangeRequest.RENDER_ALL));
        assertTrue(!args.contains("-png"), args.toString());

        assumeTrue(hasPdfiumToPpm, "pdfiumtoppm not available");
        try (InputStream is = getClass().getResourceAsStream("/test-documents/testPDF_bookmarks.pdf");
             TikaInputStream tis = TikaInputStream.get(is)) {
            PageBasedRenderResults results = (PageBasedRenderResults) renderer.render(
                    tis, new Metadata(), new ParseContext(), new PageRangeRequest(1, 1));
            assertEquals(1, results.getResults().size());
            try (TikaInputStream img = results.getResults().get(0).getInputStream()) {
                byte[] head = img.readNBytes(2);
                assertEquals("P5", new String(head, java.nio.charset.StandardCharsets.US_ASCII), "gray -> PGM");
            }
            results.close();
        }
    }

    @Test
    void testMemoryZeroDisablesLimit() {
        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        renderer.setMaxMemoryMb(0);
        List<String> args = List.of(renderer.createCommandLine(Path.of("/in/a.pdf"),
                Path.of("/out"), PageRangeRequest.RENDER_ALL));
        assertEquals("0", args.get(args.indexOf("-max-memory") + 1));
    }

    @Test
    void testSetterValidation() {
        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        assertThrows(IllegalArgumentException.class, () -> renderer.setDpi(0));
        assertThrows(IllegalArgumentException.class, () -> renderer.setMaxScaleTo(0));
        assertThrows(IllegalArgumentException.class, () -> renderer.setMaxPixels(0));
        assertThrows(IllegalArgumentException.class, () -> renderer.setMaxMemoryMb(-2));
    }

    @Test
    void testRenderAllPages() throws Exception {
        assumeTrue(hasPdfiumToPpm, "pdfiumtoppm not available");

        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();

        try (InputStream is = getClass().getResourceAsStream(
                "/test-documents/testPDF_bookmarks.pdf")) {
            assertNotNull(is, "test PDF not found");

            try (TikaInputStream tis = TikaInputStream.get(is)) {
                PageBasedRenderResults results =
                        (PageBasedRenderResults) renderer.render(
                                tis, new Metadata(), new ParseContext(),
                                PageRangeRequest.RENDER_ALL);

                List<RenderResult> allResults = results.getResults();
                assertEquals(2, allResults.size(),
                        "testPDF_bookmarks.pdf has 2 pages");

                Set<Integer> pageNumbers = new HashSet<>();
                for (RenderResult rr : allResults) {
                    assertEquals(RenderResult.STATUS.SUCCESS, rr.getStatus());

                    Metadata rm = rr.getMetadata();
                    Integer page = rm.getInt(TikaPagedText.PAGE_NUMBER);
                    assertNotNull(page, "page number should be set");
                    pageNumbers.add(page);

                    assertEquals(
                            TikaCoreProperties.EmbeddedResourceType.RENDERING
                                    .name(),
                            rm.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));

                    try (TikaInputStream imageTis = rr.getInputStream()) {
                        byte[] imageBytes = imageTis.readAllBytes();
                        assertTrue(imageBytes.length > 100,
                                "rendered page should be a non-trivial PNG");
                        assertEquals((byte) 0x89, imageBytes[0]);
                        assertEquals((byte) 'P', imageBytes[1]);
                        assertEquals((byte) 'N', imageBytes[2]);
                        assertEquals((byte) 'G', imageBytes[3]);
                    }
                }

                assertEquals(Set.of(1, 2), pageNumbers,
                        "should have pages 1 and 2");

                results.close();
            }
        }
    }

    @Test
    void testRenderPageRange() throws Exception {
        assumeTrue(hasPdfiumToPpm, "pdfiumtoppm not available");

        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();

        try (InputStream is = getClass().getResourceAsStream(
                "/test-documents/testPDF_bookmarks.pdf")) {
            assertNotNull(is, "test PDF not found");

            try (TikaInputStream tis = TikaInputStream.get(is)) {
                PageBasedRenderResults results =
                        (PageBasedRenderResults) renderer.render(
                                tis, new Metadata(), new ParseContext(),
                                new PageRangeRequest(2, 2));

                List<RenderResult> allResults = results.getResults();
                assertEquals(1, allResults.size(),
                        "should render exactly 1 page");

                assertEquals(2,
                        allResults.get(0).getMetadata()
                                .getInt(TikaPagedText.PAGE_NUMBER));

                results.close();
            }
        }
    }

    @Test
    void testMemoryLimitHit() throws Exception {
        assumeTrue(hasPdfiumToPpm, "pdfiumtoppm not available");

        PdfiumToPpmRenderer renderer = new PdfiumToPpmRenderer();
        // 300 dpi letter needs ~8 MB of bitmap on top of the ~64 MB baseline
        renderer.setMaxMemoryMb(32);

        try (InputStream is = getClass().getResourceAsStream(
                "/test-documents/testPDF_bookmarks.pdf")) {
            assertNotNull(is, "test PDF not found");
            try (TikaInputStream tis = TikaInputStream.get(is)) {
                assertThrows(TikaMemoryLimitException.class, () -> renderer.render(
                        tis, new Metadata(), new ParseContext(),
                        PageRangeRequest.RENDER_ALL));
            }
        }
    }
}
