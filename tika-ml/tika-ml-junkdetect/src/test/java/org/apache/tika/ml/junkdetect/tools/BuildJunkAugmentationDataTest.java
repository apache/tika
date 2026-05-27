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
package org.apache.tika.ml.junkdetect.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;

class BuildJunkAugmentationDataTest {

    @Test
    void chunkSplitsLongLinesAtWhitespace() {
        StringBuilder sb = new StringBuilder();
        // 1200-char line, single paragraph.
        for (int i = 0; i < 200; i++) {
            sb.append("aaaa bbbb ccc ");
        }
        List<String> chunks = BuildJunkAugmentationData.chunk(sb.toString().strip());
        assertTrue(chunks.size() >= 2, "expected multiple chunks, got " + chunks.size());
        for (String c : chunks) {
            assertTrue(c.length() <= BuildJunkAugmentationData.MAX_CHUNK_CHARS,
                    "chunk over MAX_CHUNK_CHARS: " + c.length());
        }
    }

    @Test
    void chunkGreedilyConcatenatesShortLines() {
        // HTML-extracted text typically arrives as many short newline-separated
        // fragments. The chunker should pack them into target-sized chunks
        // instead of emitting each fragment as its own training sample.
        String input = "Hello world.\nSecond paragraph here.\n\nThird.";
        List<String> chunks = BuildJunkAugmentationData.chunk(input);
        // total length 42 chars, well under TARGET_CHUNK_CHARS — single chunk
        assertEquals(1, chunks.size());
        assertEquals("Hello world. Second paragraph here. Third.", chunks.get(0));
    }

    @Test
    void chunkEmitsBufferThenSlicesLongLine() {
        // Short header line, then a long paragraph: the header should flush
        // before the long line is sliced.
        String longLine = "x".repeat(700);
        String input = "header line\n" + longLine + "\ntail line";
        List<String> chunks = BuildJunkAugmentationData.chunk(input);
        // expected: "header line", then 2 slices of the long x-string, then "tail line"
        assertEquals("header line", chunks.get(0));
        assertEquals("tail line", chunks.get(chunks.size() - 1));
        // Long-line slices are bounded by MAX_CHUNK_CHARS.
        for (int i = 1; i < chunks.size() - 1; i++) {
            assertTrue(chunks.get(i).length() <= BuildJunkAugmentationData.MAX_CHUNK_CHARS);
        }
    }

    @Test
    void dominantScriptIdentifiesLatin() {
        String text = "The quick brown fox jumps over the lazy dog. Copyright © 2026.";
        BuildJunkAugmentationData.DocScript ds =
                BuildJunkAugmentationData.dominantScript(text);
        assertEquals(Character.UnicodeScript.LATIN, ds.script);
        assertTrue(ds.dominance >= 0.99, "expected near-100% LATIN, got " + ds.dominance);
    }

    @Test
    void dominantScriptIdentifiesMixedTextAsBelowThreshold() {
        // ~50% Latin, ~50% Han — should fall below the 80% dominance gate.
        String text = "Hello world 这是中文测试内容 testing 测试更多 abc def ghi 中文混合内容更多内容";
        BuildJunkAugmentationData.DocScript ds =
                BuildJunkAugmentationData.dominantScript(text);
        assertTrue(ds.dominance < BuildJunkAugmentationData.MIN_DOC_SCRIPT_DOMINANCE,
                "expected mixed-script to fail dominance gate, got " + ds.dominance);
    }

    @Test
    void dominantScriptReturnsNullOnEmptyContent() {
        BuildJunkAugmentationData.DocScript ds =
                BuildJunkAugmentationData.dominantScript("\t\n   ");
        assertNull(ds.script);
        assertEquals(0.0, ds.dominance);
    }

    @Test
    void scanBaselineLineCountsReadsTrainFilesOnly(@TempDir Path tmp) throws Exception {
        // baseline: latin.train.gz with 3 lines, cyrillic.train.gz with 2,
        // plus a dev split that should be ignored by the scan.
        writeGz(tmp.resolve("latin.train.gz"), List.of("alpha", "beta", "gamma"));
        writeGz(tmp.resolve("cyrillic.train.gz"), List.of("один", "два"));
        writeGz(tmp.resolve("latin.dev.gz"), List.of("dev1", "dev2", "dev3"));

        Map<String, Long> counts =
                BuildJunkAugmentationData.scanBaselineLineCounts(tmp);
        assertEquals(2, counts.size());
        assertEquals(3L, counts.get("latin"));
        assertEquals(2L, counts.get("cyrillic"));
    }

    @Test
    void rewriteTrainWithAppendPreservesOriginalAndAddsLines(@TempDir Path tmp)
            throws Exception {
        Path src = tmp.resolve("src.train.gz");
        Path dst = tmp.resolve("dst.train.gz");
        writeGz(src, List.of("one", "two", "three"));

        BuildJunkAugmentationData.rewriteTrainWithAppend(src, dst, List.of("FOUR", "FIVE"));

        List<String> lines = readGz(dst);
        assertEquals(List.of("one", "two", "three", "FOUR", "FIVE"), lines);
    }

    @Test
    void endToEndAugmentsLatinAndSkipsBelowGate(@TempDir Path tmp) throws Exception {
        // -- baseline --
        Path baseline = tmp.resolve("baseline");
        Files.createDirectories(baseline);
        // 100 baseline LATIN lines → 10% cap = 10
        List<String> baselineLatin = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            baselineLatin.add("baseline-latin-" + i);
        }
        writeGz(baseline.resolve("latin.train.gz"), baselineLatin);
        writeGz(baseline.resolve("latin.dev.gz"), List.of("latin-dev"));
        writeGz(baseline.resolve("latin.test.gz"), List.of("latin-test"));
        // HAN baseline present, but extracts won't reach the doc gate.
        writeGz(baseline.resolve("han.train.gz"), List.of("基线汉字一", "基线汉字二"));

        // -- extracts --
        Path extracts = tmp.resolve("extracts");
        Files.createDirectories(extracts);
        // 6 latin docs, each carrying enough chunks to easily exceed cap.
        for (int i = 0; i < 6; i++) {
            StringBuilder content = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                content.append("This is web content line number ")
                        .append(j)
                        .append(" inside document ")
                        .append(i)
                        .append(", with copyright © 2026 and other symbols ® ™ £ €.\n");
            }
            writeExtract(extracts.resolve("latin-" + i + ".json"), content.toString());
        }
        // 1 HAN doc — well below MIN_DOCS gate, should not augment HAN.
        StringBuilder hanContent = new StringBuilder();
        for (int j = 0; j < 30; j++) {
            hanContent.append("这是一段中文测试内容用于检查脚本检测和分块功能是否能够正确识别汉字主导的文档并通过质量过滤器")
                    .append("\n");
        }
        writeExtract(extracts.resolve("han-1.json"), hanContent.toString());

        // -- run --
        Path output = tmp.resolve("output");
        BuildJunkAugmentationData.main(new String[]{
                "--extracts", extracts.toString(),
                "--baseline", baseline.toString(),
                "--output", output.toString(),
                "--min-docs", "3",          // lower so 6 latin docs pass
                "--hard-cap", "1000",       // do not constrain via hard cap
                "--baseline-frac-cap", "0.10",
                "--seed", "1"
        });

        // -- assertions --
        // latin appended at most 10 (10% of 100 baseline lines).
        List<String> latinOut = readGz(output.resolve("latin.train.gz"));
        assertEquals(110, latinOut.size(),
                "latin: 100 baseline + 10 appended (10% cap)");
        // baseline lines preserved verbatim and come first
        assertEquals(baselineLatin, latinOut.subList(0, 100));
        // appended chunks all derive from extracts and are non-empty
        for (int i = 100; i < 110; i++) {
            assertFalse(latinOut.get(i).isEmpty());
        }

        // HAN copied unchanged (single doc < min-docs gate).
        List<String> hanOut = readGz(output.resolve("han.train.gz"));
        assertEquals(List.of("基线汉字一", "基线汉字二"), hanOut);

        // dev and test split copied verbatim.
        assertEquals(List.of("latin-dev"), readGz(output.resolve("latin.dev.gz")));
        assertEquals(List.of("latin-test"), readGz(output.resolve("latin.test.gz")));

        // Manifest present
        Path manifest = output.resolve("augmentation_manifest.tsv");
        assertTrue(Files.exists(manifest));
        String manifestText = Files.readString(manifest);
        assertTrue(manifestText.contains("LATIN"), "manifest should report LATIN row");
    }

    @Test
    void structuralFilterDropsUtf8AsWin1252Mojibake() {
        // Real mojibake samples from our augmentation analysis.
        String mojiGerman  = "Die EASA in BrÃ¼ssel hat aufgrund "
                + "der europaweit festzustellenden Beschwerden";
        String mojiItalian = "Mi Ã¨ appena nato un pulso dalle uova dentro il nido";
        String cleanGerman = "Die EASA in Brüssel hat aufgrund "
                + "der europaweit festzustellenden Beschwerden";
        String cleanItalian = "Mi è appena nato un pulso dalle uova dentro il nido";
        // Mojibake should produce ≥1 of the structural bigrams; clean Latin none.
        assertTrue(BuildJunkAugmentationData.countUtf8AsWin1252Bigrams(mojiGerman) >= 1,
                "expected mojibake-shape bigrams in German sample");
        assertTrue(BuildJunkAugmentationData.countUtf8AsWin1252Bigrams(mojiItalian) >= 1,
                "expected mojibake-shape bigrams in Italian sample");
        assertEquals(0, BuildJunkAugmentationData.countUtf8AsWin1252Bigrams(cleanGerman),
                "clean German should not match the mojibake structural shape");
        assertEquals(0, BuildJunkAugmentationData.countUtf8AsWin1252Bigrams(cleanItalian),
                "clean Italian should not match the mojibake structural shape");
    }

    @Test
    void profileCsvFiltersByOovAndLangness(@TempDir Path tmp) throws Exception {
        // -- baseline --
        Path baseline = tmp.resolve("baseline");
        Files.createDirectories(baseline);
        List<String> baselineLatin = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            baselineLatin.add("baseline-latin-" + i);
        }
        writeGz(baseline.resolve("latin.train.gz"), baselineLatin);

        // -- extracts: 4 latin docs with distinguishable content --
        Path extracts = tmp.resolve("extracts");
        Path sub = extracts.resolve("aa");
        Files.createDirectories(sub);
        for (int i = 0; i < 4; i++) {
            StringBuilder content = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                content.append("Quality web text paragraph number ")
                        .append(j)
                        .append(" inside document ")
                        .append(i)
                        .append(", with copyright © 2026 and other markings.\n");
            }
            writeExtract(sub.resolve("doc" + i + ".json"), content.toString());
        }

        // -- profile CSV: only docs 0 and 1 pass (low OOV, positive langness) --
        Path csv = tmp.resolve("profile.csv");
        try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write("\"FILE_PATH\",\"OOV\",\"LANGNESS\",\"LANG\"\n");
            w.write("\"aa/doc0\",\"0.3\",\"0.5\",\"eng\"\n");      // pass
            w.write("\"aa/doc1\",\"0.4\",\"0.1\",\"eng\"\n");      // pass
            w.write("\"aa/doc2\",\"0.7\",\"0.5\",\"eng\"\n");      // fail OOV
            w.write("\"aa/doc3\",\"0.2\",\"-0.5\",\"eng\"\n");     // fail langness
            // doc with no profile row is also dropped (covered separately)
        }

        Path output = tmp.resolve("output");
        BuildJunkAugmentationData.main(new String[]{
                "--extracts", extracts.toString(),
                "--baseline", baseline.toString(),
                "--output", output.toString(),
                "--profile-csv", csv.toString(),
                "--max-oov", "0.5",
                "--min-langness", "0.0",
                "--min-docs", "1",
                "--hard-cap", "1000",
                "--baseline-frac-cap", "1.0",
                "--seed", "1"
        });

        List<String> out = readGz(output.resolve("latin.train.gz"));
        // 100 baseline lines + chunks from only 2 docs that pass profile filter
        // each doc has 12 short lines, each long enough to pass min-bytes filter
        assertTrue(out.size() > 100, "expected augmentation, got " + out.size());
        int added = out.size() - 100;
        assertTrue(added > 0 && added <= 24,
                "expected appended lines from 2 docs (<=24 chunks), got " + added);
    }

    @Test
    void containsTargetSymbolDetectsStarvedSymbols() {
        assertTrue(BuildJunkAugmentationData.containsTargetSymbol("Copyright © 2024 GmbH"));
        assertTrue(BuildJunkAugmentationData.containsTargetSymbol("Marke ® Produkt"));
        assertTrue(BuildJunkAugmentationData.containsTargetSymbol("Preis £ 19.99"));
        assertFalse(BuildJunkAugmentationData.containsTargetSymbol(
                "Für Anfänger empfehlen wir den Grundkurs"));
        // Š (the mojibake reading) is NOT a target — we boost the win-1252 source symbols.
        assertFalse(BuildJunkAugmentationData.containsTargetSymbol("Škoda Praha"));
    }

    @Test
    void symbolBoostReservesQuotaForSymbolChunks(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve("baseline");
        Files.createDirectories(baseline);
        // 100 baseline LATIN lines → 10% cap = 10 (then we set hard-cap=10).
        List<String> base = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            base.add("baseline-latin-" + i);
        }
        writeGz(baseline.resolve("latin.train.gz"), base);

        // Extracts: many symbol-free docs + a few symbol-bearing ones.
        Path extracts = tmp.resolve("extracts");
        Path sub = extracts.resolve("aa");
        Files.createDirectories(sub);
        for (int i = 0; i < 20; i++) {
            StringBuilder c = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                c.append("Plain German prose paragraph number ").append(j)
                        .append(" in document ").append(i)
                        .append(" with enough words to pass filters here.\n");
            }
            writeExtract(sub.resolve("plain" + i + ".json"), c.toString());
        }
        for (int i = 0; i < 6; i++) {
            StringBuilder c = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                c.append("Impressum line ").append(j).append(" Copyright © 2024 Müller GmbH ")
                        .append("Marke ® registriert, Preis £ 49 in document ").append(i).append(".\n");
            }
            writeExtract(sub.resolve("symbol" + i + ".json"), c.toString());
        }

        Path output = tmp.resolve("output");
        BuildJunkAugmentationData.main(new String[]{
                "--extracts", extracts.toString(),
                "--baseline", baseline.toString(),
                "--output", output.toString(),
                "--min-docs", "1",
                "--hard-cap", "10",
                "--baseline-frac-cap", "1.0",
                "--symbol-boost", "0.5",
                "--seed", "1"
        });

        List<String> out = readGz(output.resolve("latin.train.gz"));
        List<String> appended = out.subList(100, out.size());
        long symbolBearing = appended.stream()
                .filter(BuildJunkAugmentationData::containsTargetSymbol).count();
        // quota = floor(10 * 0.5) = 5; symbol pool has ≥5 chunks, so ≥5 appended
        // lines should be symbol-bearing.
        assertTrue(symbolBearing >= 5,
                "expected >=5 symbol-bearing lines with 0.5 boost, got " + symbolBearing);
    }

    @Test
    void profileCsvParserHandlesQuotedFields() {
        String header = "\"FILE_PATH\",\"OOV\",\"LANGNESS\",\"LANG\"";
        String[] cols = BuildJunkAugmentationData.parseCsvLine(header);
        assertEquals(4, cols.length);
        assertEquals("FILE_PATH", cols[0]);
        assertEquals("OOV", cols[1]);
        String row = "\"aa/foo\",\"0.42\",\"0.1\",\"eng\"";
        String[] f = BuildJunkAugmentationData.parseCsvLine(row);
        assertEquals(4, f.length);
        assertEquals("aa/foo", f[0]);
        assertEquals("0.42", f[1]);
    }

    @Test
    void profileKeyMatchesExtractPath(@TempDir Path tmp) {
        Path extracts = tmp.resolve("extracts");
        Path file = extracts.resolve("0F").resolve("ABCD1234.json");
        assertEquals("0F/ABCD1234", BuildJunkAugmentationData.profileKey(extracts, file));
    }

    // ---------------------------------------------------------------------------

    private static void writeGz(Path path, List<String> lines) throws Exception {
        try (Writer w = new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(path)),
                StandardCharsets.UTF_8)) {
            for (String s : lines) {
                w.write(s);
                w.write('\n');
            }
        }
    }

    private static List<String> readGz(Path path) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static void writeExtract(Path path, String content) throws Exception {
        Metadata md = new Metadata();
        md.set(TikaCoreProperties.TIKA_CONTENT, content);
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(path),
                        StandardCharsets.UTF_8))) {
            JsonMetadataList.toJson(List.of(md), w);
        }
        assertNotNull(Files.size(path));
    }
}
