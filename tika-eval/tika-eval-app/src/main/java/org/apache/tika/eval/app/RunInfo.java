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
package org.apache.tika.eval.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.Tika;
import org.apache.tika.eval.app.db.ColInfo;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.eval.app.io.IDBWriter;
import org.apache.tika.eval.app.io.PipesReport;

/**
 * Provenance for an eval run: what tika-eval this is, what it was pointed at, and (via
 * {@code --runInfo}) what batch run produced the extracts. Lands in {@code run_info}
 * ({@code run_info_a}/{@code run_info_b} for the two sides of a comparison) as key/value rows.
 */
public class RunInfo {

    private static final Logger LOG = LoggerFactory.getLogger(RunInfo.class);

    public static final TableInfo RUN_INFO_TABLE = new TableInfo("run_info",
            new ColInfo(Cols.RUN_KEY, Types.VARCHAR, 256), new ColInfo(Cols.RUN_VALUE, Types.VARCHAR, 4096));
    public static final TableInfo RUN_INFO_TABLE_A = new TableInfo("run_info_a", RUN_INFO_TABLE.getColInfos());
    public static final TableInfo RUN_INFO_TABLE_B = new TableInfo("run_info_b", RUN_INFO_TABLE.getColInfos());

    public static final String BATCH_PREFIX = "batch.";
    public static final String RUN_ID_KEY = BATCH_PREFIX + "run.id";
    public static final String PIPES_REPORT_PATH_KEY = "pipes_report.path";
    /** Written by run-batch.sh inside the extracts dir; skipped by the crawl and the fingerprint. */
    public static final String RUN_INFO_DIR = ".run-info";
    static final String LEDGER_PREFIX = "crashes-";
    static final String LEDGER_SUFFIX = ".jsonl";

    // secrets in jdbc urls / argv / -D flags; the value after the separator is replaced
    private static final Pattern SECRET_KV = Pattern.compile("(?i)((?:password|passwd|pwd|secret|token|credential)[a-z_.-]*\\s*[=:]\\s*)[^&;,\\s'\"]*");
    private static final Pattern SECRET_URL_USERINFO = Pattern.compile("(//[^/:@\\s]+:)[^@\\s]+(@)");

    private RunInfo() {
    }

    /** One side's batch inputs: the ledger (may be null) and the flattened run-info (may be empty). */
    public record Side(PipesReport pipesReport, Map<String, String> batchInfo) {
        public Path pipesReportPath() {
            return pipesReport == null ? null : pipesReport.getPath();
        }
    }

    /**
     * Resolves and loads one side's ledger and run-info: an explicit path wins, otherwise
     * {@code <extracts>/.run-info/} is searched, and the pair is refused if it is from two runs.
     */
    public static Side loadSide(Path explicitPipesReport, Path explicitRunInfo, Path extracts) throws IOException {
        Path pr = explicitPipesReport != null ? explicitPipesReport : discoverPipesReport(extracts);
        Path ri = explicitRunInfo != null ? explicitRunInfo : discoverRunInfo(extracts);
        PipesReport report = pr == null ? null : PipesReport.load(pr);
        Map<String, String> batch = ri == null ? Map.of() : loadBatch(ri);
        checkRunId(batch, pr);
        return new Side(report, batch);
    }

    /**
     * The batch script leaves its files in {@code <extracts>/.run-info/}. Used when the CLI flags are
     * absent; exactly one run-info there is required, since two means the extracts were written by two runs.
     * @return run-info json path or null if the dir has none
     */
    public static Path discoverRunInfo(Path extracts) throws IOException {
        return discover(extracts, "run-info-", ".json");
    }

    public static Path discoverPipesReport(Path extracts) throws IOException {
        return discover(extracts, LEDGER_PREFIX, LEDGER_SUFFIX);
    }

    private static Path discover(Path extracts, String prefix, String suffix) throws IOException {
        Path dir = extracts.resolve(RUN_INFO_DIR);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> hits;
        try (Stream<Path> s = Files.list(dir)) {
            hits = s.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
        if (hits.size() > 1) {
            throw new IllegalArgumentException(hits.size() + " " + prefix + "*" + suffix + " files in " + dir +
                    ": the extracts were written by more than one run. Pass the one you mean explicitly.");
        }
        return hits.isEmpty() ? null : hits.get(0);
    }

    public static boolean isRunInfoPath(String relativePath) {
        String p = PipesReport.normalize(relativePath);
        return p.equals(RUN_INFO_DIR) || p.startsWith(RUN_INFO_DIR + "/");
    }

    /** Flattens the batch script's run-info json into dotted keys under {@code batch.}. */
    public static Map<String, String> loadBatch(Path json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(json, StandardCharsets.UTF_8));
        Map<String, String> m = new LinkedHashMap<>();
        flatten(BATCH_PREFIX, root, m);
        m.put(BATCH_PREFIX + "run_info.path", json.toAbsolutePath().toString());
        return m;
    }

    private static void flatten(String prefix, JsonNode n, Map<String, String> m) {
        if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                flatten(prefix + e.getKey() + ".", e.getValue(), m);
            }
        } else {
            m.put(prefix.substring(0, prefix.length() - 1), n.isValueNode() ? n.asText() : n.toString());
        }
    }

    /**
     * A pipes report produced by a different batch run than the run-info describes is the
     * failure mode nobody can see after the fact; refuse it. The script names the ledger
     * {@code crashes-<run.id>.jsonl}; a run-info with a blank {@code run.id} cannot vouch for any ledger.
     */
    public static void checkRunId(Map<String, String> batch, Path pipesReport) {
        if (batch == null || pipesReport == null || !batch.containsKey(RUN_ID_KEY)) {
            return;
        }
        String runId = batch.get(RUN_ID_KEY);
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("run-info has a blank run.id; cannot tie it to " + pipesReport);
        }
        String expected = LEDGER_PREFIX + runId + LEDGER_SUFFIX;
        if (!pipesReport.getFileName().toString().equals(expected)) {
            throw new IllegalArgumentException("run.id mismatch: run-info says '" + runId + "' (ledger should be " + expected +
                    ") but the pipes report is " + pipesReport);
        }
    }

    public static Map<String, String> pipesReportInfo(PipesReport report) {
        Map<String, String> m = new LinkedHashMap<>();
        if (report == null) {
            return m;
        }
        m.put(PIPES_REPORT_PATH_KEY, report.getPath().toAbsolutePath().toString());
        m.put("pipes_report.rows", Integer.toString(report.size()));
        m.put("pipes_report.errors", Integer.toString(report.getErrors().size()));
        if (!report.getErrors().isEmpty()) {
            m.put("pipes_report.last_error", report.getErrors().get(report.getErrors().size() - 1));
        }
        return m;
    }

    /**
     * Count and a sha256 over the sorted "relpath size" lines of the extract set.
     * @throws IllegalArgumentException if {@code extracts} is not a directory
     */
    public static Map<String, String> extractsInfo(Path extracts) throws IOException {
        if (!Files.isDirectory(extracts)) {
            throw new IllegalArgumentException("extracts dir does not exist: " + extracts);
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("extracts.path", extracts.toAbsolutePath().toString());
        long start = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        Files.walkFileTree(extracts, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return isRunInfoPath(extracts.relativize(dir).toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    lines.add(PipesReport.normalize(extracts.relativize(file).toString()) + " " + attrs.size() + "\n");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(lines);
        MessageDigest md = sha256();
        for (String line : lines) {
            md.update(line.getBytes(StandardCharsets.UTF_8));
        }
        m.put("extracts.count", Long.toString(lines.size()));
        m.put("extracts.fingerprint", HexFormat.of().formatHex(md.digest()));
        LOG.info("fingerprinted {} extracts under {} in {} ms", lines.size(), extracts, System.currentTimeMillis() - start);
        return m;
    }

    public static Map<String, String> evalInfo(String[] args, EvalConfig config, String jdbcString, Path inputDir) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eval.tika_version", Tika.getString());
        m.put("eval.jar_sha256", ownJarSha256());
        m.put("eval.start", Instant.now().toString());
        m.put("eval.host", hostName());
        m.put("eval.user", System.getProperty("user.name"));
        m.put("eval.java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        m.put("eval.args", redact(String.join(" ", args)));
        m.put("eval.config", redact(config.toString()));
        m.put("db.path", redact(jdbcString));
        m.put("input.path", inputDir.toAbsolutePath().toString());
        return m;
    }

    /** Masks password-like values so the tables can travel with the reports. */
    public static String redact(String s) {
        if (s == null) {
            return null;
        }
        String r = SECRET_URL_USERINFO.matcher(s).replaceAll("$1***$2");
        return SECRET_KV.matcher(r).replaceAll("$1***");
    }

    public static void write(IDBWriter writer, TableInfo table, Map<String, String> info) throws IOException {
        for (Map.Entry<String, String> e : info.entrySet()) {
            Map<Cols, String> row = new HashMap<>();
            row.put(Cols.RUN_KEY, e.getKey());
            row.put(Cols.RUN_VALUE, e.getValue());
            writer.writeRow(table, row);
        }
    }

    /**
     * Records the end time and the join outcome of each ledger, then flushes. Never throws:
     * this runs in the runners' {@code finally}, where an exception would mask the real
     * failure and skip the executor/connection shutdown after it.
     */
    public static void finish(IDBWriter writer, TableInfo evalTable, Map<TableInfo, PipesReport> reportsByTable) {
        try {
            write(writer, evalTable, Map.of("eval.end", Instant.now().toString()));
            for (Map.Entry<TableInfo, PipesReport> e : reportsByTable.entrySet()) {
                PipesReport r = e.getValue();
                if (r == null) {
                    continue;
                }
                write(writer, e.getKey(), Map.of("pipes_report.joined", Long.toString(r.getJoined())));
                if (r.size() > 0 && r.getJoined() == 0) {
                    LOG.warn("no container matched any of the {} rows in {}: wrong ledger for this extracts dir, or the crawl " +
                            "never saw those files (crawling extracts without -i cannot see files that crashed)", r.size(), r.getPath());
                }
            }
            writer.close();
        } catch (IOException | RuntimeException e) {
            LOG.warn("couldn't write run_info end", e);
        }
    }

    private static String ownJarSha256() {
        try {
            CodeSource cs = RunInfo.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return "";
            }
            Path p = Path.of(cs.getLocation().toURI());
            if (!Files.isRegularFile(p)) {
                return ""; // running from target/classes
            }
            MessageDigest md = sha256();
            try (InputStream is = Files.newInputStream(p)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | URISyntaxException | RuntimeException e) {
            LOG.warn("couldn't hash own jar", e);
            return "";
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "";
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
