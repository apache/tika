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
package org.apache.tika.langdetect.charsoup.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks for duplicate sentences in train/dev/test splits.
 * <p>
 * Reports:
 * <ol>
 *   <li>Duplicates within training data</li>
 *   <li>Duplicates within test data</li>
 *   <li>Overlap between train and test (data leakage)</li>
 *   <li>Overlap between train and dev</li>
 * </ol>
 * <p>
 * Uses 64-bit FNV-1a hashes of the full {@code lang\ttext} line to
 * keep memory bounded (~500 MB for 60M sentences). Collision risk
 * with 64-bit hashes over 60M entries is negligible (~1 in 10^8).
 * <p>
 * Usage: {@code DuplicateChecker <prepDir>}
 * <br>Expects {@code train.txt}, {@code dev.txt}, {@code test.txt}
 * in the given directory.
 */
public class DuplicateChecker {

    // FNV-1a 64-bit parameters
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "Usage: DuplicateChecker <prepDir>");
            System.exit(1);
        }

        Path prepDir = Paths.get(args[0]);
        Path trainFile = prepDir.resolve("train.txt");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test.txt");

        System.out.println("=== Duplicate Analysis ===\n");
        System.out.println("Directory: " + prepDir);

        // --- Pass 1: Scan train, collect hashes, count dupes ---
        System.out.println("\n--- Scanning train ---");
        Set<Long> trainHashes = new HashSet<>();
        ScanResult trainResult = scanFile(trainFile, trainHashes,
                null, "train");
        reportScan("Train", trainResult);

        // --- Pass 2: Scan test, check within-test + vs train ---
        System.out.println("\n--- Scanning test ---");
        Set<Long> testHashes = new HashSet<>();
        ScanResult testResult = scanFile(testFile, testHashes,
                trainHashes, "test");
        reportScan("Test", testResult);

        // --- Pass 3: Scan dev, check within-dev + vs train ---
        System.out.println("\n--- Scanning dev ---");
        Set<Long> devHashes = new HashSet<>();
        ScanResult devResult = scanFile(devFile, devHashes,
                trainHashes, "dev");
        reportScan("Dev", devResult);

        // --- Also check dev vs test overlap ---
        System.out.println("\n--- Dev vs Test overlap ---");
        int devTestOverlap = 0;
        for (Long h : devHashes) {
            if (testHashes.contains(h)) {
                devTestOverlap++;
            }
        }
        System.out.printf(Locale.US,
                "Dev/Test shared hashes: %,d%n", devTestOverlap);

        // --- Summary ---
        System.out.println("\n=== Summary ===");
        System.out.printf(Locale.US,
                "Train: %,d total, %,d unique, %,d dupes (%.2f%%)%n",
                trainResult.total, trainResult.unique,
                trainResult.withinDupes,
                pct(trainResult.withinDupes, trainResult.total));
        System.out.printf(Locale.US,
                "Test:  %,d total, %,d unique, %,d dupes (%.2f%%)"
                        + ", %,d overlap with train (%.2f%%)%n",
                testResult.total, testResult.unique,
                testResult.withinDupes,
                pct(testResult.withinDupes, testResult.total),
                testResult.crossDupes,
                pct(testResult.crossDupes, testResult.total));
        System.out.printf(Locale.US,
                "Dev:   %,d total, %,d unique, %,d dupes (%.2f%%)"
                        + ", %,d overlap with train (%.2f%%)%n",
                devResult.total, devResult.unique,
                devResult.withinDupes,
                pct(devResult.withinDupes, devResult.total),
                devResult.crossDupes,
                pct(devResult.crossDupes, devResult.total));
    }

    /**
     * Scan a file, collecting hashes into {@code ownHashes} and
     * optionally checking for overlap with {@code otherHashes}.
     * <p>
     * Each line is hashed as {@code lang\ttext} (the full line).
     * Also hashes just the text portion to catch same sentence
     * with different labels (which shouldn't happen but let's check).
     */
    static ScanResult scanFile(Path file, Set<Long> ownHashes,
                               Set<Long> otherHashes,
                               String label) throws IOException {
        ScanResult result = new ScanResult();
        Map<String, int[]> perLangDupes = new TreeMap<>();
        // [0] = within dupes, [1] = cross dupes, [2] = total
        Map<String, int[]> perLangCross = new TreeMap<>();

        // Also check text-only duplicates (same text, any label)
        Set<Long> textOnlyHashes = new HashSet<>();
        int textOnlyDupes = 0;

        int progressInterval = 5_000_000;

        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.total++;

                if (result.total % progressInterval == 0) {
                    System.out.printf(Locale.US,
                            "  %s: %,d lines scanned...%n",
                            label, result.total);
                }

                // Hash the full line (lang + text)
                long hash = fnv1a64(line);

                // Extract lang for per-language stats
                int tab = line.indexOf('\t');
                String lang = tab > 0
                        ? line.substring(0, tab) : "?";

                int[] langCounts = perLangDupes.computeIfAbsent(
                        lang, k -> new int[3]);
                langCounts[2]++;

                // Check text-only duplicate
                if (tab > 0) {
                    long textHash = fnv1a64(
                            line.substring(tab + 1));
                    if (!textOnlyHashes.add(textHash)) {
                        textOnlyDupes++;
                    }
                }

                // Check within-set duplicate
                if (!ownHashes.add(hash)) {
                    result.withinDupes++;
                    langCounts[0]++;
                } else {
                    result.unique++;
                }

                // Check cross-set overlap
                if (otherHashes != null
                        && otherHashes.contains(hash)) {
                    result.crossDupes++;
                    langCounts[1]++;
                }
            }
        }

        result.textOnlyDupes = textOnlyDupes;

        // Report top languages by within-dupes
        System.out.printf(Locale.US,
                "  Text-only dupes (same text, any label): %,d%n",
                textOnlyDupes);

        // Top 15 langs by within-dupe count
        perLangDupes.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .sorted((a, b) -> Integer.compare(
                        b.getValue()[0], a.getValue()[0]))
                .limit(15)
                .forEach(e -> {
                    int[] c = e.getValue();
                    System.out.printf(Locale.US,
                            "    %-10s %,6d within-dupes"
                                    + " / %,d total (%.1f%%)",
                            e.getKey(), c[0], c[2],
                            100.0 * c[0] / c[2]);
                    if (c[1] > 0) {
                        System.out.printf(Locale.US,
                                "  +%,d cross", c[1]);
                    }
                    System.out.println();
                });

        return result;
    }

    static void reportScan(String label, ScanResult r) {
        System.out.printf(Locale.US,
                "%s: %,d total, %,d unique, %,d within-dupes"
                        + " (%.2f%%)%n",
                label, r.total, r.unique,
                r.withinDupes, pct(r.withinDupes, r.total));
        if (r.crossDupes > 0) {
            System.out.printf(Locale.US,
                    "%s: %,d cross-set overlaps (%.2f%%)%n",
                    label, r.crossDupes,
                    pct(r.crossDupes, r.total));
        }
    }

    /** FNV-1a 64-bit hash of a string's UTF-8 bytes. */
    static long fnv1a64(String s) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                hash ^= c;
                hash *= FNV_PRIME;
            } else if (c < 0x800) {
                hash ^= (0xC0 | (c >> 6));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (c & 0x3F));
                hash *= FNV_PRIME;
            } else if (Character.isHighSurrogate(c)
                    && i + 1 < s.length()) {
                int cp = Character.toCodePoint(c, s.charAt(++i));
                hash ^= (0xF0 | (cp >> 18));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((cp >> 12) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((cp >> 6) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (cp & 0x3F));
                hash *= FNV_PRIME;
            } else {
                hash ^= (0xE0 | (c >> 12));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((c >> 6) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (c & 0x3F));
                hash *= FNV_PRIME;
            }
        }
        return hash;
    }

    private static double pct(int num, int denom) {
        return denom > 0 ? 100.0 * num / denom : 0.0;
    }

    static class ScanResult {
        int total;
        int unique;
        int withinDupes;
        int crossDupes;
        int textOnlyDupes;
    }
}
