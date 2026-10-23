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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pages.PagesConfig;

/**
 * {@code pages.emit.maxPages} bounds the emission without bounding the text:
 * a thumbnail wants the first page rendered and the whole document read.
 */
public class PDFMaxRenderedPagesTest extends TikaTest {

    private static final String TWO_PAGES = "testPDF_bookmarks.pdf";

    @Test
    public void testOnlyTheFirstPageIsRendered() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.pages().emit().setEnabled(true);
        config.pages().emit().setMaxPages(1);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata(TWO_PAGES, context);
        assertEquals(2, (int) metadataList.get(0).getInt(PagedText.N_PAGES));
        // page 2 is still read
        assertContains("Denmark", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(1, renderings(metadataList), "one rendering, the first page");
    }

    @Test
    public void testLimitAbovePageCountRendersEveryPage() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.pages().emit().setEnabled(true);
        config.pages().emit().setMaxPages(5);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata(TWO_PAGES, context);
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING),
                "a limit above the page count is not an error");
        assertEquals(2, renderings(metadataList), "both pages, the limit is not reached");
    }

    @Test
    public void testJsonConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser",
                "{\"pages\": {\"emit\": {\"enabled\": true, \"maxPages\": 1}}}");
        assertEquals(1, renderings(getRecursiveMetadata(TWO_PAGES, context)));
    }

    /** The parse budget bounds emission: a page that is not read is not rendered. */
    @Test
    public void testParseBudgetBoundsEmission() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.pages().emit().setEnabled(true);
        config.setMaxPages(1);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        assertEquals(1, renderings(getRecursiveMetadata(TWO_PAGES, context)));
    }

    @Test
    public void testZeroIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PagesConfig.Emit().setMaxPages(0));
    }

    private static long renderings(List<Metadata> metadataList) {
        return metadataList.stream()
                .filter(m -> TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                        .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)))
                .count();
    }
}
