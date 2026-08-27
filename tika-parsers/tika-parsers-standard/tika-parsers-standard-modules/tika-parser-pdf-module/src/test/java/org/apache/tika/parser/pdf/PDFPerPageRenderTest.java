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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Per-page rendering re-opens the document once per page. Each open must start from byte 0
 * regardless of where the stream was left; before PDFRandomAccess the renderer read the
 * TikaInputStream from its current position, so page 1 drained it and every later page
 * rendered nothing, silently.
 */
public class PDFPerPageRenderTest extends TikaTest {

    private static final String TWO_PAGES = "/test-documents/testPDF_bookmarks.pdf";

    @TempDir
    Path tempDir;

    private static ParseContext renderAtPageEnd() {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        return context;
    }

    private static void assertEveryPageRendered(List<Metadata> metadataList) {
        Metadata container = metadataList.get(0);
        assertNull(container.get(TikaCoreProperties.EMBEDDED_EXCEPTION),
                "a page render failure is recorded on the container, not thrown");
        int pages = container.getInt(PagedText.N_PAGES);
        assertEquals(2, pages, "fixture must be multi-page for this test to mean anything");
        long rendered = metadataList.stream()
                .filter(m -> TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                        .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)))
                .count();
        assertEquals(pages, rendered, "one rendering per page");
    }

    @Test
    public void testStreamBackedRendersEveryPage() throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream(TWO_PAGES)) {
            bytes = is.readAllBytes();
        }
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), new Metadata())) {
            assertEveryPageRendered(getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    renderAtPageEnd(), true));
        }
    }

    @Test
    public void testFileBackedRendersEveryPage() throws Exception {
        Path pdf = tempDir.resolve("two-pages.pdf");
        try (InputStream is = getResourceAsStream(TWO_PAGES)) {
            Files.copy(is, pdf);
        }
        try (TikaInputStream tis = TikaInputStream.get(pdf)) {
            assertEveryPageRendered(getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    renderAtPageEnd(), true));
        }
    }
}
