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
package org.apache.tika.detect.encoding.tools;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.encoding.Icu4jEncodingDetector;
import org.apache.tika.detect.encoding.MlEncodingDetector;
import org.apache.tika.detect.encoding.UniversalEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Micro-benchmark comparing charset detector throughput.
 *
 * <p>Not a JMH benchmark — intended for quick apples-to-apples comparison on
 * a warm JVM.  Runs each detector over a fixed corpus of byte samples, reports
 * throughput in MB/s and mean latency in µs per call.</p>
 *
 * <p>Usage:
 * <pre>
 *   java BenchmarkCharsetDetectors --data /path/to/test-dir [--warmup 3] [--rounds 5]
 * </pre>
 */
public class BenchmarkCharsetDetectors {

    private static final int DEFAULT_WARMUP_ROUNDS = 3;
    private static final int DEFAULT_TIMED_ROUNDS  = 5;
    private static final int MAX_SAMPLES_PER_FILE  = 2000;

    public static void main(String[] args) throws Exception {
        Path dataDir = null;
        int warmupRounds = DEFAULT_WARMUP_ROUNDS;
        int timedRounds  = DEFAULT_TIMED_ROUNDS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--warmup":
                    warmupRounds = Integer.parseInt(args[++i]);
                    break;
                case "--rounds":
                    timedRounds = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }
        if (dataDir == null) {
            System.err.println("Usage: BenchmarkCharsetDetectors --data <dir> [--warmup N] [--rounds N]");
            System.exit(1);
        }

        // Load all samples upfront — benchmarks should not include I/O
        System.out.println("Loading samples from " + dataDir + " ...");
        List<byte[]> samples = loadAllSamples(dataDir, MAX_SAMPLES_PER_FILE);
        long totalBytes = samples.stream().mapToLong(b -> b.length).sum();
        System.out.printf(Locale.ROOT, "  %,d samples  |  %,d total bytes  |  mean %.0f bytes/sample%n%n",
                samples.size(), totalBytes, (double) totalBytes / samples.size());

        EncodingDetector[] detectors = {
            new MlEncodingDetector(),
            new Icu4jEncodingDetector(),
            new UniversalEncodingDetector(),
        };
        String[] names = {"MlEncodingDetector", "Icu4jEncodingDetector", "UniversalEncodingDetector"};

        System.out.printf(Locale.ROOT, "%-28s  %8s  %10s  %10s%n",
                "Detector", "Rounds", "MB/s", "µs/call");
        System.out.println("-".repeat(62));

        for (int di = 0; di < detectors.length; di++) {
            EncodingDetector det = detectors[di];

            // Warmup — not measured
            for (int r = 0; r < warmupRounds; r++) {
                runRound(det, samples);
            }

            // Timed rounds
            long[] roundNs = new long[timedRounds];
            for (int r = 0; r < timedRounds; r++) {
                long t0 = System.nanoTime();
                runRound(det, samples);
                roundNs[r] = System.nanoTime() - t0;
            }

            // Report median round
            java.util.Arrays.sort(roundNs);
            long medianNs  = roundNs[timedRounds / 2];
            double mbPerSec = (totalBytes / 1_048_576.0) / (medianNs / 1e9);
            double usPerCall = (medianNs / 1000.0) / samples.size();

            System.out.printf(Locale.ROOT, "%-28s  %8d  %10.1f  %10.2f%n",
                    names[di], timedRounds, mbPerSec, usPerCall);
        }
    }

    private static void runRound(EncodingDetector det, List<byte[]> samples) throws IOException {
        ParseContext ctx = new ParseContext();
        Metadata meta = new Metadata();
        for (byte[] sample : samples) {
            try (TikaInputStream tis = TikaInputStream.get(sample)) {
                det.detect(tis, meta, ctx);
            }
        }
    }

    private static List<byte[]> loadAllSamples(Path dataDir, int maxPerFile) throws IOException {
        List<byte[]> out = new ArrayList<>();
        List<Path> files = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .sorted()
                .collect(Collectors.toList());
        for (Path f : files) {
            int count = 0;
            try (FileInputStream fis = new FileInputStream(f.toFile());
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 DataInputStream dis = new DataInputStream(gis)) {
                while (count < maxPerFile) {
                    int len;
                    try {
                        len = dis.readUnsignedShort();
                    } catch (java.io.EOFException e) {
                        break;
                    }
                    byte[] chunk = new byte[len];
                    dis.readFully(chunk);
                    out.add(chunk);
                    count++;
                }
            }
        }
        return out;
    }
}
