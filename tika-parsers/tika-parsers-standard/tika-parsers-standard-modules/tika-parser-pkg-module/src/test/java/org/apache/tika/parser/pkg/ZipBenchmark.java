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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class ZipBenchmark {

    // Toggle this to switch between DefaultHandler and RecursiveParserWrapper
    private static final boolean USE_RECURSIVE_PARSER_WRAPPER = true;

    @Test
    public void benchmarkAutoDetectParser(@TempDir Path tempDir) throws Exception {
        // Enable to run
        assumeTrue(true, "Set to true to run");

        int iterations = 40;
        int warmupIterations = 6;

        Path smallZip = tempDir.resolve("small.zip");
        createBenchmarkZip(smallZip, 10, 1024);
        System.out.println("Small: " + Files.size(smallZip) / 1024 + " KB");

        Path mediumZip = tempDir.resolve("medium.zip");
        createBenchmarkZip(mediumZip, 1000, 100 * 1024);
        System.out.println("Medium: " + Files.size(mediumZip) / (1024 * 1024) + " MB");

        Path largeZip = tempDir.resolve("large.zip");
        createBenchmarkZip(largeZip, 5000, 500 * 1024);
        System.out.println("Large: " + Files.size(largeZip) / (1024 * 1024) + " MB");

        System.out.println("\n=== ZIP Benchmark ===");
        System.out.println("Mode: " + (USE_RECURSIVE_PARSER_WRAPPER ? "RecursiveParserWrapper" : "DefaultHandler"));
        System.out.println();

        System.out.println("Small ZIP (10 entries, 10KB):");
        runBenchmark(smallZip, 10, iterations, warmupIterations);

        System.out.println("\nMedium ZIP (1000 entries, ~100MB):");
        runBenchmark(mediumZip, 1000, 20, 4);

        System.out.println("\nLarge ZIP (5000 entries, ~2.5GB):");
        runBenchmark(largeZip, 5000, 10, 2);
    }

    private void createBenchmarkZip(Path zipPath, int numEntries, int entrySize) throws Exception {
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.setMethod(java.util.zip.ZipOutputStream.STORED);
            java.util.Random random = new java.util.Random(42);
            byte[] content = new byte[entrySize];
            random.nextBytes(content);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(content);
            long crcValue = crc.getValue();

            for (int i = 0; i < numEntries; i++) {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("entry" + i + ".txt");
                entry.setMethod(java.util.zip.ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(crcValue);
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }
        }
    }

    private void runBenchmark(Path zipPath, int numEntries, int iterations, int warmup) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        long sizeKB = Files.size(zipPath) / 1024;
        String sizeStr = sizeKB >= 1024 ? (sizeKB / 1024) + " MB" : sizeKB + " KB";
        System.out.printf(Locale.ROOT, "  Entries: %d, Size: %s%n", numEntries, sizeStr);

        // Warmup
        for (int i = 0; i < warmup; i++) {
            try (TikaInputStream tis = TikaInputStream.get(zipPath)) {
                if (USE_RECURSIVE_PARSER_WRAPPER) {
                    parseWithRecursiveWrapper(parser, tis, context);
                } else {
                    parser.parse(tis, new DefaultHandler(), new Metadata(), context);
                }
            }
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (TikaInputStream tis = TikaInputStream.get(zipPath)) {
                if (USE_RECURSIVE_PARSER_WRAPPER) {
                    parseWithRecursiveWrapper(parser, tis, context);
                } else {
                    parser.parse(tis, new DefaultHandler(), new Metadata(), context);
                }
            }
        }
        long duration = System.nanoTime() - start;

        double avgMs = duration / (double) iterations / 1_000_000.0;
        System.out.printf(Locale.ROOT, "  Average: %.3f ms%n", avgMs);
    }

    private void parseWithRecursiveWrapper(AutoDetectParser parser, TikaInputStream tis,
                                           ParseContext context) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        BasicContentHandlerFactory factory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(factory);
        wrapper.parse(tis, handler, new Metadata(), context);
    }
}
