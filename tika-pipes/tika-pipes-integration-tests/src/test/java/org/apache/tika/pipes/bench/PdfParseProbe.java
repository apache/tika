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
package org.apache.tika.pipes.bench;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;

/**
 * Self-sampling profile of a single Tika parse. Runs warmup, then parses the
 * target file while a sampler thread polls the parse thread's stack at 100Hz
 * and tallies the topmost {@code org.apache.pdfbox} or {@code org.apache.tika}
 * frame seen on each sample. Prints the top 25 frames sorted by sample count.
 * <p>
 * Run:
 * <pre>
 * ./mvnw test -pl tika-pipes/tika-pipes-integration-tests \
 *     -Dtest=PdfParseProbe -Dpipes.probe.run=true \
 *     -Dpipes.probe.file=&lt;path-to-pdf&gt; \
 *     [-Dpipes.probe.iterations=5]
 * </pre>
 */
public class PdfParseProbe {

    @Test
    @EnabledIfSystemProperty(named = "pipes.probe.run", matches = "true")
    public void probe() throws Exception {
        Path file = Paths.get(System.getProperty("pipes.probe.file"));
        int iterations = Integer.getInteger("pipes.probe.iterations", 5);

        AutoDetectParser parser = new AutoDetectParser();
        PDFParserConfig pdfConfig = buildPdfConfig();
        ParseContext ctxTemplate = new ParseContext();
        ctxTemplate.set(PDFParserConfig.class, pdfConfig);

        System.out.println("PDFParserConfig overrides:" +
                " extractMarkedContent=" + pdfConfig.isExtractMarkedContent() +
                " extractAcroFormContent=" + pdfConfig.isExtractAcroFormContent() +
                " extractAnnotationText=" + pdfConfig.isExtractAnnotationText() +
                " extractBookmarksText=" + pdfConfig.isExtractBookmarksText() +
                " extractActions=" + pdfConfig.isExtractActions() +
                " extractInlineImages=" + pdfConfig.isExtractInlineImages());

        // warmup
        for (int i = 0; i < 2; i++) {
            parseOnce(parser, file, ctxTemplate);
        }

        Thread parseThread = Thread.currentThread();
        Map<String, Integer> counts = new HashMap<>();
        int[] totalSamples = {0};
        Object stopFlag = new Object();
        boolean[] running = {true};

        Thread sampler = new Thread(() -> {
            while (true) {
                synchronized (stopFlag) {
                    if (!running[0]) {
                        return;
                    }
                }
                StackTraceElement[] st = parseThread.getStackTrace();
                totalSamples[0]++;
                for (StackTraceElement frame : st) {
                    String cn = frame.getClassName();
                    if (cn.startsWith("org.apache.pdfbox") || cn.startsWith("org.apache.tika")
                            || cn.startsWith("org.apache.fontbox")) {
                        String key = cn + "." + frame.getMethodName();
                        counts.merge(key, 1, Integer::sum);
                        break;
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "stack-sampler");
        sampler.setDaemon(true);
        sampler.start();

        long t = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            parseOnce(parser, file, ctxTemplate);
        }
        long elapsedMs = (System.nanoTime() - t) / 1_000_000L;

        synchronized (stopFlag) {
            running[0] = false;
        }
        sampler.join(1000);

        System.out.println();
        System.out.println("=== PdfParseProbe ===");
        System.out.println("file:        " + file);
        System.out.println("iterations:  " + iterations);
        System.out.println("wall time:   " + elapsedMs + "ms (" + (elapsedMs / iterations) + "ms/parse)");
        System.out.println("samples:     " + totalSamples[0]);
        System.out.println();
        System.out.println("top 25 hot frames (by sample count):");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(25)
                .forEach(e -> System.out.printf("  %5d  %s%n", e.getValue(), e.getKey()));
    }

    private static void parseOnce(AutoDetectParser parser, Path file, ParseContext ctxTemplate)
            throws Exception {
        ParseContext ctx = new ParseContext();
        PDFParserConfig pc = ctxTemplate.get(PDFParserConfig.class);
        if (pc != null) {
            ctx.set(PDFParserConfig.class, pc);
        }
        try (TikaInputStream is = TikaInputStream.get(file)) {
            parser.parse(is, new DefaultHandler(), new Metadata(), ctx);
        }
    }

    private static PDFParserConfig buildPdfConfig() {
        PDFParserConfig c = new PDFParserConfig();
        applyBool("pipes.probe.pdf.extractMarkedContent", c::setExtractMarkedContent);
        applyBool("pipes.probe.pdf.extractAcroFormContent", c::setExtractAcroFormContent);
        applyBool("pipes.probe.pdf.extractAnnotationText", c::setExtractAnnotationText);
        applyBool("pipes.probe.pdf.extractBookmarksText", c::setExtractBookmarksText);
        applyBool("pipes.probe.pdf.extractActions", c::setExtractActions);
        applyBool("pipes.probe.pdf.extractInlineImages", c::setExtractInlineImages);
        return c;
    }

    private static void applyBool(String prop, java.util.function.Consumer<Boolean> setter) {
        String v = System.getProperty(prop);
        if (v != null) {
            setter.accept(Boolean.parseBoolean(v));
        }
    }
}
