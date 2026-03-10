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
package org.apache.tika.eval.app.reports;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a markdown summary of a tika-eval comparison run.
 * <p>
 * This is designed to be read by both humans and LLMs for fast
 * regression detection. It queries the same temp tables that the
 * xlsx report pipeline creates (exceptions_compared,
 * token_counts_compared, parse_time_compared) so it must be called
 * after the "before" SQL has executed.
 */
public class MarkdownSummaryWriter {

    private static final Logger LOG = LoggerFactory.getLogger(MarkdownSummaryWriter.class);

    private static final int TOP_N = 20;

    public static void write(Connection c, Path reportsDir) throws IOException, SQLException {
        if (!isComparisonDb(c)) {
            LOG.info("Not a comparison database; skipping markdown summary.");
            return;
        }
        Path summaryPath = reportsDir.resolve("summary.md");
        Files.createDirectories(reportsDir);

        try (BufferedWriter w = Files.newBufferedWriter(summaryPath)) {
            w.write("# Tika Eval Comparison Summary\n\n");

            writeOverview(c, w);
            writeExtractExceptionSummary(c, w);
            writeExceptionSummary(c, w);
            writeContentQualitySummary(c, w);
            writeOovComparison(c, w);
            writeLanguageChanges(c, w);
            writeContentLengthRatio(c, w);
            writeEmbeddedCountChanges(c, w);
            writeTokenCountSummary(c, w);
            writeParseTimeSummary(c, w);
            writeMimeChanges(c, w);
            writeTopRegressions(c, w);
            writeTopImprovements(c, w);
            writeContentLost(c, w);
            writeContentGained(c, w);
            writeMissingExtracts(c, w);
        }
        LOG.info("Wrote markdown summary to {}", summaryPath);
    }

    private static void writeOverview(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Overview\n\n");

        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "select dir_name_a, dir_name_b from pair_names")) {
                if (rs.next()) {
                    w.write("- **A**: " + rs.getString(1) + "\n");
                    w.write("- **B**: " + rs.getString(2) + "\n");
                }
            }

            writeScalar(st, w, "- **Total containers**: ",
                    "select count(1) from containers");
            writeScalar(st, w, "- **Total files (A)**: ",
                    "select count(1) from profiles_a");
            writeScalar(st, w, "- **Total files (B)**: ",
                    "select count(1) from profiles_b");
            writeScalar(st, w, "- **Exceptions (A)**: ",
                    "select count(1) from exceptions_a");
            writeScalar(st, w, "- **Exceptions (B)**: ",
                    "select count(1) from exceptions_b");
        }
        w.write("\n");
    }

    private static void writeExtractExceptionSummary(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Extract File Issues\n\n");
        w.write("Problems reading extract files (before parsing). " +
                "Includes missing files, zero-byte files, oversized files, and bad JSON.\n\n");

        w.write("### Extract A\n\n");
        writeQueryAsTable(c, w,
                "select r.extract_exception_description as TYPE, count(1) as COUNT " +
                "from extract_exceptions_a ee " +
                "join ref_extract_exception_types r " +
                "  on r.extract_exception_id = ee.extract_exception_id " +
                "group by r.extract_exception_description " +
                "order by COUNT desc");

        w.write("\n### Extract B\n\n");
        writeQueryAsTable(c, w,
                "select r.extract_exception_description as TYPE, count(1) as COUNT " +
                "from extract_exceptions_b ee " +
                "join ref_extract_exception_types r " +
                "  on r.extract_exception_id = ee.extract_exception_id " +
                "group by r.extract_exception_description " +
                "order by COUNT desc");
        w.write("\n");
    }

    private static void writeExceptionSummary(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Exception Changes by Mime Type\n\n");
        w.write("Mime types with >100 files where exception rate changed by >5%.\n\n");

        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "ec.total as TOTAL, " +
                "ec.exc_cnt_a as EXC_A, ec.exc_cnt_b as EXC_B, " +
                "round(ec.exc_prcnt_a * 100, 1) as EXC_PCT_A, " +
                "round(ec.exc_prcnt_b * 100, 1) as EXC_PCT_B, " +
                "ec.notes as FLAG " +
                "from exceptions_compared ec " +
                "join mimes ma on ma.mime_id = ec.mime_id_a " +
                "join mimes mb on mb.mime_id = ec.mime_id_b " +
                "where ec.total > 100 " +
                "and abs(ec.exc_prcnt_a - ec.exc_prcnt_b) > 0.05 " +
                "order by abs(ec.exc_prcnt_a - ec.exc_prcnt_b) desc");

        w.write("\n### New Exception Types in B\n\n");
        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "count(1) as COUNT " +
                "from exceptions_b eb " +
                "left join exceptions_a ea on ea.id = eb.id " +
                "join profiles_a pa on pa.id = eb.id " +
                "join profiles_b pb on pb.id = eb.id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "join mimes mb on mb.mime_id = pb.mime_id " +
                "where ea.id is null and eb.parse_exception_id = 0 " +
                "group by ma.mime_string, mb.mime_string " +
                "order by COUNT desc " +
                "limit " + TOP_N);

        w.write("\n### Fixed Exceptions in B\n\n");
        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "count(1) as COUNT " +
                "from exceptions_a ea " +
                "left join exceptions_b eb on ea.id = eb.id " +
                "join profiles_a pa on pa.id = ea.id " +
                "join profiles_b pb on pb.id = pa.id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "join mimes mb on mb.mime_id = pb.mime_id " +
                "where eb.id is null and ea.parse_exception_id = 0 " +
                "group by ma.mime_string, mb.mime_string " +
                "order by COUNT desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeContentQualitySummary(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Content Quality (Dice Coefficient) by Mime Type\n\n");
        w.write("Mean and median dice coefficient per mime type (higher = more similar).\n\n");

        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "count(1) as FILES, " +
                "round(avg(cc.dice_coefficient), 4) as MEAN_DICE, " +
                "round(median(cc.dice_coefficient), 4) as MEDIAN_DICE, " +
                "round(min(cc.dice_coefficient), 4) as MIN_DICE " +
                "from content_comparisons cc " +
                "join profiles_a pa on cc.id = pa.id " +
                "join profiles_b pb on cc.id = pb.id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "join mimes mb on mb.mime_id = pb.mime_id " +
                "group by ma.mime_string, mb.mime_string " +
                "having count(1) > 5 " +
                "order by MEAN_DICE asc");
        w.write("\n");
    }

    private static void writeTokenCountSummary(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Token Count Changes by Mime Type\n\n");

        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "tcc.num_tokens_a as TOKENS_A, tcc.num_tokens_b as TOKENS_B, " +
                "case when tcc.num_tokens_a > 0 " +
                "  then round(100.0 * (tcc.num_tokens_b - tcc.num_tokens_a) / tcc.num_tokens_a, 1) " +
                "  else null end as PCT_CHANGE, " +
                "tcc.num_common_tokens_a as COMMON_A, tcc.num_common_tokens_b as COMMON_B " +
                "from token_counts_compared tcc " +
                "join mimes ma on ma.mime_id = tcc.mime_id_a " +
                "join mimes mb on mb.mime_id = tcc.mime_id_b " +
                "order by abs(tcc.num_tokens_a - tcc.num_tokens_b) desc");
        w.write("\n");
    }

    private static void writeParseTimeSummary(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Parse Time Changes by Mime Type\n\n");

        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, mb.mime_string as MIME_B, " +
                "ptc.total_a as MS_A, ptc.total_b as MS_B, " +
                "round(ptc.prcnt_increase, 1) as B_AS_PCT_OF_A " +
                "from parse_time_compared ptc " +
                "join mimes ma on ma.mime_id = ptc.mime_id_a " +
                "join mimes mb on mb.mime_id = ptc.mime_id_b " +
                "where ptc.total_a > 0 " +
                "order by ptc.prcnt_increase desc");

        w.write("\n### Parse Time Outliers (individual files, B > 10x A, A >= 1s)\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "pa.elapsed_time_millis as MS_A, " +
                "pb.elapsed_time_millis as MS_B, " +
                "round(cast(pb.elapsed_time_millis as double) / " +
                "  cast(pa.elapsed_time_millis as double), 1) as RATIO " +
                "from profiles_a pa " +
                "join profiles_b pb on pa.id = pb.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and pa.elapsed_time_millis >= 1000 " +
                "and pb.elapsed_time_millis > pa.elapsed_time_millis * 10 " +
                "order by RATIO desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeMimeChanges(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Mime Type Changes (A -> B)\n\n");

        writeQueryAsTable(c, w,
                "select concat(ma.mime_string, ' -> ', mb.mime_string) as CHANGE, " +
                "count(1) as COUNT " +
                "from profiles_a a " +
                "join profiles_b b on a.id = b.id " +
                "join mimes ma on ma.mime_id = a.mime_id " +
                "join mimes mb on mb.mime_id = b.mime_id " +
                "where a.mime_id <> b.mime_id " +
                "group by CHANGE " +
                "order by COUNT desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeTopRegressions(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Top " + TOP_N + " Content Regressions (lowest dice)\n\n");
        w.write("Files where content changed the most (excluding perfect matches).\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "round(cc.dice_coefficient, 4) as DICE, " +
                "round(cc.overlap, 4) as OVERLAP, " +
                "cc.top_10_unique_token_diffs_a as ONLY_IN_A, " +
                "cc.top_10_unique_token_diffs_b as ONLY_IN_B " +
                "from content_comparisons cc " +
                "join profiles_a pa on cc.id = pa.id " +
                "join profiles_b pb on cc.id = pb.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where cc.dice_coefficient < 1.0 " +
                "and pa.is_embedded = false " +
                "order by cc.dice_coefficient asc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeTopImprovements(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Top " + TOP_N + " Fixed Exceptions in B (with content gained)\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "cb.num_tokens as TOKENS_B, " +
                "cb.num_common_tokens as COMMON_TOKENS_B, " +
                "cb.lang_id_1 as LANG_B " +
                "from exceptions_a ea " +
                "left join exceptions_b eb on ea.id = eb.id " +
                "join profiles_a pa on pa.id = ea.id " +
                "join profiles_b pb on pb.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "left join contents_b cb on cb.id = ea.id " +
                "where eb.id is null and ea.parse_exception_id = 0 " +
                "and pa.is_embedded = false " +
                "order by cb.num_common_tokens desc nulls last " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeContentLost(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Content Lost (had content in A, empty/missing in B)\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "ca.num_tokens as TOKENS_A, " +
                "ca.num_common_tokens as COMMON_A, " +
                "coalesce(cb.num_tokens, 0) as TOKENS_B " +
                "from contents_a ca " +
                "join profiles_a pa on ca.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "left join contents_b cb on ca.id = cb.id " +
                "where ca.num_tokens > 10 " +
                "and coalesce(cb.num_tokens, 0) = 0 " +
                "and pa.is_embedded = false " +
                "order by ca.num_tokens desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeContentGained(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Content Gained (empty in A, has content in B)\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "mb.mime_string as MIME_B, " +
                "coalesce(ca.num_tokens, 0) as TOKENS_A, " +
                "cb.num_tokens as TOKENS_B, " +
                "cb.num_common_tokens as COMMON_B " +
                "from contents_b cb " +
                "join profiles_b pb on cb.id = pb.id " +
                "join profiles_a pa on cb.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes mb on mb.mime_id = pb.mime_id " +
                "left join contents_a ca on cb.id = ca.id " +
                "where cb.num_tokens > 10 " +
                "and coalesce(ca.num_tokens, 0) = 0 " +
                "and pa.is_embedded = false " +
                "order by cb.num_tokens desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeEmbeddedCountChanges(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Embedded Document Count Changes\n\n");
        w.write("Files where the number of embedded documents changed significantly.\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "pa.num_attachments as EMBEDDED_A, " +
                "pb.num_attachments as EMBEDDED_B, " +
                "(pb.num_attachments - pa.num_attachments) as DELTA " +
                "from profiles_a pa " +
                "join profiles_b pb on pa.id = pb.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and pa.num_attachments <> pb.num_attachments " +
                "and (pa.num_attachments > 0 or pb.num_attachments > 0) " +
                "order by abs(pb.num_attachments - pa.num_attachments) desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeContentLengthRatio(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Content Length Ratio Outliers\n\n");
        w.write("Files where content length changed by more than 2x " +
                "(possible repeated text or truncation).\n\n");

        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "ca.content_length as LEN_A, " +
                "cb.content_length as LEN_B, " +
                "round(cast(cb.content_length as double) / " +
                "  cast(ca.content_length as double), 2) as RATIO_B_TO_A " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and ca.content_length > 100 " +
                "and cb.content_length > 100 " +
                "and (cast(cb.content_length as double) / " +
                "  cast(ca.content_length as double) > 2.0 " +
                "  or cast(cb.content_length as double) / " +
                "  cast(ca.content_length as double) < 0.5) " +
                "order by abs(cast(cb.content_length as double) / " +
                "  cast(ca.content_length as double) - 1.0) desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeLanguageChanges(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Language Detection Changes\n\n");
        w.write("Files where the detected language changed between A and B.\n\n");

        w.write("### By Language Pair (aggregate)\n\n");
        writeQueryAsTable(c, w,
                "select ca.lang_id_1 as LANG_A, cb.lang_id_1 as LANG_B, " +
                "count(1) as COUNT " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "where pa.is_embedded = false " +
                "and ca.lang_id_1 is not null " +
                "and cb.lang_id_1 is not null " +
                "and ca.lang_id_1 <> cb.lang_id_1 " +
                "group by ca.lang_id_1, cb.lang_id_1 " +
                "order by COUNT desc " +
                "limit " + TOP_N);

        w.write("\n### Top " + TOP_N + " Individual Files\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "ca.lang_id_1 as LANG_A, " +
                "round(ca.lang_id_prob_1, 3) as PROB_A, " +
                "cb.lang_id_1 as LANG_B, " +
                "round(cb.lang_id_prob_1, 3) as PROB_B " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and ca.lang_id_1 is not null " +
                "and cb.lang_id_1 is not null " +
                "and ca.lang_id_1 <> cb.lang_id_1 " +
                "order by ca.num_tokens desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeOovComparison(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Out-of-Vocabulary (OOV) and Languageness Changes\n\n");
        w.write("Files where OOV rate increased or languageness z-score " +
                "decreased in B (possible mojibake or encoding regression).\n\n");

        w.write("### By Mime Type (aggregate)\n\n");
        writeQueryAsTable(c, w,
                "select ma.mime_string as MIME_A, " +
                "count(1) as FILES, " +
                "round(avg(ca.oov), 4) as MEAN_OOV_A, " +
                "round(avg(cb.oov), 4) as MEAN_OOV_B, " +
                "round(avg(cb.oov) - avg(ca.oov), 4) as OOV_DELTA, " +
                "round(avg(ca.languageness), 2) as MEAN_LANG_A, " +
                "round(avg(cb.languageness), 2) as MEAN_LANG_B, " +
                "round(avg(cb.languageness) - avg(ca.languageness), 2) as LANG_DELTA " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and ca.oov is not null and cb.oov is not null " +
                "group by ma.mime_string " +
                "having count(1) > 5 " +
                "order by OOV_DELTA desc");

        w.write("\n### Top " + TOP_N + " Individual OOV Increases\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "round(ca.oov, 4) as OOV_A, " +
                "round(cb.oov, 4) as OOV_B, " +
                "round(cb.oov - ca.oov, 4) as OOV_DELTA, " +
                "round(ca.languageness, 2) as LANG_A, " +
                "round(cb.languageness, 2) as LANG_B, " +
                "ca.lang_id_1 as LANG_ID_A, " +
                "cb.lang_id_1 as LANG_ID_B " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and ca.oov is not null and cb.oov is not null " +
                "and ca.num_tokens > 10 " +
                "and (cb.oov - ca.oov) > 0.1 " +
                "order by (cb.oov - ca.oov) desc " +
                "limit " + TOP_N);

        w.write("\n### Top " + TOP_N + " Languageness Decreases\n\n");
        w.write("Files where the languageness z-score dropped the most " +
                "(text became less language-like in B).\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "round(ca.languageness, 2) as LANG_A, " +
                "round(cb.languageness, 2) as LANG_B, " +
                "round(cb.languageness - ca.languageness, 2) as LANG_DELTA, " +
                "round(ca.oov, 4) as OOV_A, " +
                "round(cb.oov, 4) as OOV_B, " +
                "ca.lang_id_1 as LANG_ID_A, " +
                "cb.lang_id_1 as LANG_ID_B " +
                "from contents_a ca " +
                "join contents_b cb on ca.id = cb.id " +
                "join profiles_a pa on ca.id = pa.id " +
                "join containers c on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and ca.languageness > -90 and cb.languageness > -90 " +
                "and ca.num_tokens > 10 " +
                "and (cb.languageness - ca.languageness) < -1.0 " +
                "order by (cb.languageness - ca.languageness) asc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static void writeMissingExtracts(Connection c, BufferedWriter w)
            throws IOException, SQLException {
        w.write("## Missing Extracts\n\n");
        w.write("Files where A had an extract file but B did not (or vice versa).\n\n");

        w.write("### Had extract in A, missing in B\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "ma.mime_string as MIME_A, " +
                "c.extract_file_length_a as EXTRACT_LEN_A, " +
                "c.extract_file_length_b as EXTRACT_LEN_B " +
                "from containers c " +
                "join profiles_a pa on pa.container_id = c.container_id " +
                "join mimes ma on ma.mime_id = pa.mime_id " +
                "where pa.is_embedded = false " +
                "and c.extract_file_length_a > 0 " +
                "and (c.extract_file_length_b is null or c.extract_file_length_b = 0) " +
                "order by c.extract_file_length_a desc " +
                "limit " + TOP_N);

        w.write("\n### Had extract in B, missing in A\n\n");
        writeQueryAsTable(c, w,
                "select c.file_path as FILE, " +
                "mb.mime_string as MIME_B, " +
                "c.extract_file_length_a as EXTRACT_LEN_A, " +
                "c.extract_file_length_b as EXTRACT_LEN_B " +
                "from containers c " +
                "join profiles_b pb on pb.container_id = c.container_id " +
                "join mimes mb on mb.mime_id = pb.mime_id " +
                "where pb.is_embedded = false " +
                "and c.extract_file_length_b > 0 " +
                "and (c.extract_file_length_a is null or c.extract_file_length_a = 0) " +
                "order by c.extract_file_length_b desc " +
                "limit " + TOP_N);
        w.write("\n");
    }

    private static boolean isComparisonDb(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getTables(null, null, "%", null)) {
            while (rs.next()) {
                if ("CONTENT_COMPARISONS".equalsIgnoreCase(rs.getString(3))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writeScalar(Statement st, BufferedWriter w,
                                     String prefix, String sql)
            throws IOException, SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                w.write(prefix + rs.getString(1) + "\n");
            }
        }
    }

    private static void writeQueryAsTable(Connection c, BufferedWriter w,
                                           String sql)
            throws IOException, SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            if (!rs.isBeforeFirst()) {
                w.write("_No data._\n\n");
                return;
            }

            // Header
            w.write("|");
            for (int i = 1; i <= cols; i++) {
                w.write(" " + meta.getColumnLabel(i) + " |");
            }
            w.write("\n|");
            for (int i = 1; i <= cols; i++) {
                w.write(" --- |");
            }
            w.write("\n");

            // Rows
            while (rs.next()) {
                w.write("|");
                for (int i = 1; i <= cols; i++) {
                    String val = rs.getString(i);
                    if (val == null) {
                        val = "";
                    }
                    // Escape pipes in values
                    val = val.replace("|", "\\|");
                    w.write(" " + val + " |");
                }
                w.write("\n");
            }
        }
    }
}
