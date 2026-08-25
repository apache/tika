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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.Locale;

/**
 * Reads a PIPES_TIMING TSV produced by {@link PipesBenchmark} and prints
 * per-stage p50/p95/p99/max plus a status count summary.
 * <p>
 * Run: {@code java org.apache.tika.pipes.bench.PipesTimingAnalyzer &lt;tsv-path&gt;}
 * <p>
 * Negative values (-1) mean the stage was not measured for that row (e.g.,
 * server-side timings on a crash path); these rows are excluded from per-stage
 * percentiles but still counted under "status".
 */
public final class PipesTimingAnalyzer {

    private static final String[] STAGES = {
            "client_wait_us", "init_us", "req_serialize_us", "req_socket_us", "req_write_us",
            "server_wait_us", "server_fetch_us", "server_parse_us", "server_emit_us",
            "server_wall_us", "client_total_us"
    };

    private PipesTimingAnalyzer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: PipesTimingAnalyzer <pipes-timing.tsv>");
            System.exit(1);
        }
        Path tsv = Paths.get(args[0]);
        if (!Files.isRegularFile(tsv)) {
            System.err.println("not a file: " + tsv);
            System.exit(1);
        }

        Map<String, List<Long>> byStage = new LinkedHashMap<>();
        for (String s : STAGES) {
            byStage.put(s, new ArrayList<>());
        }
        Map<String, Integer> statusCounts = new TreeMap<>();
        int totalRows = 0;

        try (Stream<String> lines = Files.lines(tsv)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.contains("PIPES_TIMING")) {
                    continue;
                }
                totalRows++;
                Map<String, String> kv = parseLine(line);
                String status = kv.getOrDefault("status", "UNKNOWN");
                statusCounts.merge(status, 1, Integer::sum);
                for (String s : STAGES) {
                    String v = kv.get(s);
                    if (v == null) {
                        continue;
                    }
                    long n = Long.parseLong(v);
                    if (n >= 0) {
                        byStage.get(s).add(n);
                    }
                }
            }
        }

        System.out.println("rows: " + totalRows);
        System.out.println("status counts:");
        for (Map.Entry<String, Integer> e : statusCounts.entrySet()) {
            System.out.printf(Locale.ROOT, "  %-30s %d%n", e.getKey(), e.getValue());
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "%-20s %10s %10s %10s %10s %10s%n",
                "stage", "n", "p50_us", "p95_us", "p99_us", "max_us");
        for (String s : STAGES) {
            List<Long> values = byStage.get(s);
            if (values.isEmpty()) {
                System.out.printf(Locale.ROOT, "%-20s %10d %10s %10s %10s %10s%n",
                        s, 0, "-", "-", "-", "-");
                continue;
            }
            Collections.sort(values);
            System.out.printf(Locale.ROOT, "%-20s %10d %10d %10d %10d %10d%n",
                    s, values.size(),
                    pct(values, 0.50), pct(values, 0.95), pct(values, 0.99),
                    values.get(values.size() - 1));
        }
    }

    private static long pct(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.min(sorted.size() - 1L, Math.ceil(p * sorted.size()) - 1);
        if (idx < 0) {
            idx = 0;
        }
        return sorted.get(idx);
    }

    private static Map<String, String> parseLine(String line) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String tok : line.split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(tok.substring(0, eq), tok.substring(eq + 1));
        }
        return out;
    }
}
