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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * {@code maxRenderedPages} bounds the rendering without bounding the text:
 * a thumbnail wants the first page rendered and the whole document read.
 */
public class PDFMaxRenderedPagesTest extends TikaTest {

    private static final String TWO_PAGES = "testPDF_bookmarks.pdf";

    @ParameterizedTest
    @EnumSource(value = PDFParserConfig.IMAGE_STRATEGY.class,
            names = {"RENDER_PAGES_BEFORE_PARSE", "RENDER_PAGES_AT_PAGE_END"})
    public void testOnlyTheFirstPageIsRendered(PDFParserConfig.IMAGE_STRATEGY strategy)
            throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(strategy);
        config.setMaxRenderedPages(1);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata(TWO_PAGES, context);
        assertEquals(2, (int) metadataList.get(0).getInt(PagedText.N_PAGES));
        assertEquals(1, renderings(metadataList), "one rendering, the first page");
    }

    @Test
    public void testJsonConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser",
                "{\"imageStrategy\": \"RENDER_PAGES_AT_PAGE_END\", \"maxRenderedPages\": 1}");
        assertEquals(1, renderings(getRecursiveMetadata(TWO_PAGES, context)));
    }

    @Test
    public void testZeroIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PDFParserConfig().setMaxRenderedPages(0));
    }

    private static long renderings(List<Metadata> metadataList) {
        return metadataList.stream()
                .filter(m -> TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                        .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)))
                .count();
    }
}
