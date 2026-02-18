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
package org.apache.tika.renderer.pdf.poppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;

public class PopplerRendererTest {

    private static boolean hasPoppler;

    @BeforeAll
    static void checkPoppler() {
        hasPoppler = ExternalParser.check(new String[]{"pdftoppm", "-v"});
    }

    @Test
    void testRenderAllPages() throws Exception {
        assumeTrue(hasPoppler, "pdftoppm not available");

        PopplerRenderer renderer = new PopplerRenderer();

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

                    // Verify we can actually read the rendered image bytes
                    try (TikaInputStream imageTis = rr.getInputStream()) {
                        byte[] imageBytes = imageTis.readAllBytes();
                        assertTrue(imageBytes.length > 100,
                                "rendered page should be a non-trivial PNG");
                        // PNG magic bytes
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
        assumeTrue(hasPoppler, "pdftoppm not available");

        PopplerRenderer renderer = new PopplerRenderer();

        try (InputStream is = getClass().getResourceAsStream(
                "/test-documents/testPDF_bookmarks.pdf")) {
            assertNotNull(is, "test PDF not found");

            try (TikaInputStream tis = TikaInputStream.get(is)) {
                // Render only page 2
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
    void testCustomDpi() throws Exception {
        assumeTrue(hasPoppler, "pdftoppm not available");

        PopplerRenderer renderer = new PopplerRenderer();
        renderer.setDpi(72);
        renderer.setGray(false);

        try (InputStream is = getClass().getResourceAsStream(
                "/test-documents/testPDF_bookmarks.pdf")) {
            assertNotNull(is, "test PDF not found");

            try (TikaInputStream tis = TikaInputStream.get(is)) {
                PageBasedRenderResults results =
                        (PageBasedRenderResults) renderer.render(
                                tis, new Metadata(), new ParseContext(),
                                PageRangeRequest.RENDER_ALL);

                assertEquals(2, results.getResults().size());

                // 72 DPI should produce smaller images than 300 DPI
                try (TikaInputStream imageTis =
                             results.getResults().get(0).getInputStream()) {
                    byte[] imageBytes = imageTis.readAllBytes();
                    assertTrue(imageBytes.length > 0);
                }

                results.close();
            }
        }
    }
}
