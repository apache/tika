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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;

/**
 * The tagged writer's flat path is PDF2XHTML: with the gate forcing every page back to the
 * stripper, every test PDF must come out byte for byte the same as with tags off. This is what
 * makes turning tags on by default a bounded risk.
 */
public class PDFMarkedContentIdentityTest extends TikaTest {

    static Stream<Arguments> configurations() {
        List<Arguments> args = new ArrayList<>();
        args.add(Arguments.of("defaults", (Consumer<PDFParserConfig>) c -> { }));
        args.add(Arguments.of("maxPages=3", (Consumer<PDFParserConfig>) c -> c.setMaxPages(3)));
        args.add(Arguments.of("sortByPosition",
                (Consumer<PDFParserConfig>) c -> c.setSortByPosition(true)));
        args.add(Arguments.of("suppressDuplicateOverlappingText",
                (Consumer<PDFParserConfig>) c -> c.setSuppressDuplicateOverlappingText(true)));
        args.add(Arguments.of("autoSpaceOff",
                (Consumer<PDFParserConfig>) c -> c.setEnableAutoSpace(false)));
        return args.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configurations")
    public void testFallbackMatchesStripper(String name, Consumer<PDFParserConfig> tweak)
            throws Exception {
        File dir = getResourceAsFile("/test-documents");
        File[] pdfs = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        Arrays.sort(pdfs);
        assertTrue(pdfs.length > 40);
        int tagged = 0;
        for (File pdf : pdfs) {
            PDFParserConfig none = new PDFParserConfig();
            tweak.accept(none);
            PDFParserConfig forcedFallback = new PDFParserConfig();
            tweak.accept(forcedFallback);
            forcedFallback.getMarkedContent().setStrategy(MarkedContentConfig.Strategy.AUTO);
            // above 1: no page can pass the coverage gate
            forcedFallback.getMarkedContent().setMinCoverage(2f);

            XMLResult a = parse(pdf, none);
            XMLResult b = parse(pdf, forcedFallback);
            if (a == null || b == null) {
                assertTrue(a == null && b == null, pdf.getName() + " failed on one side only");
                continue;
            }
            assertEquals(body(a.xml), body(b.xml), pdf.getName());
            if ("true".equals(b.metadata.get(PDF.HAS_MARKED_CONTENT))) {
                tagged++;
                assertEquals(0, b.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED), pdf.getName());
                assertTrue(b.metadata.getInt(PDF.MARKED_CONTENT_PAGES_FALLBACK) > 0,
                        pdf.getName());
            }
        }
        assertTrue(tagged >= 3, "expected tagged test PDFs, saw " + tagged);
    }

    private XMLResult parse(File pdf, PDFParserConfig config) {
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        try {
            return getXML(pdf.getName(), context);
        } catch (Exception e) {
            return null;
        }
    }

    private static String body(String xml) {
        return xml.substring(xml.indexOf("<body>"));
    }
}
