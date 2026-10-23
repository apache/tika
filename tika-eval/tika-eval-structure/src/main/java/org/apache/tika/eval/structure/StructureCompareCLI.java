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
package org.apache.tika.eval.structure;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * Compares the structure of two sets of tika-app JSON extracts (XHTML content) file by
 * file: block order, segmentation, word boundaries, artifact and untagged placement.
 * Writes {@code per-file.tsv} and {@code summary.md} to the output directory.
 */
public class StructureCompareCLI {

    private static final Logger LOG = LoggerFactory.getLogger(StructureCompareCLI.class);
    private static final String LEGACY_CONTENT_KEY = "X-TIKA:content";
    private static final Pattern VERSION = Pattern.compile("[\\d.]+.*$", Pattern.DOTALL);
    private static final int MAX_GROUP_LENGTH = 40;

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("a").longOpt("extractsA").hasArg().required()
                .desc("reference extracts (the stripper, or labels)").build());
        options.addOption(Option.builder("b").longOpt("extractsB").hasArg().required()
                .desc("candidate extracts").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required()
                .desc("output directory for per-file.tsv and summary.md").build());
        options.addOption(Option.builder("g").longOpt("groupBy").hasArg()
                .desc("metadata key of A to group the summary by (default pdf:producer)").build());
        options.addOption(Option.builder("d").longOpt("minDice").hasArg()
                .desc("least token dice for two blocks to match (default 0.5)").build());
        options.addOption(Option.builder("s").longOpt("stripB").hasArg()
                .desc("suffix to drop from B names before pairing, e.g. .pdf pairs "
                        + "x.docx.pdf.json with x.docx.json (labels from the source document)")
                .build());
        CommandLine cl;
        try {
            cl = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("StructureCompare", options);
            System.exit(1);
            return;
        }
        run(Paths.get(cl.getOptionValue("a")), Paths.get(cl.getOptionValue("b")),
                Paths.get(cl.getOptionValue("o")), cl.getOptionValue("g", "pdf:producer"),
                Double.parseDouble(cl.getOptionValue("d", "0.5")), cl.getOptionValue("s", ""));
    }

    static List<StructureScores> run(Path a, Path b, Path out, String groupKey, double minDice)
            throws IOException {
        return run(a, b, out, groupKey, minDice, "");
    }

    static List<StructureScores> run(Path a, Path b, Path out, String groupKey, double minDice,
                                     String stripB) throws IOException {
        Files.createDirectories(out);
        List<StructureScores> all = new ArrayList<>();
        int missing = 0;
        int unreadable = 0;
        List<Path> files;
        try (Stream<Path> stream = Files.list(a)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted()
                    .toList();
        }
        Map<String, Path> bByKey = new HashMap<>();
        try (Stream<Path> stream = Files.list(b)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> bByKey.put(pairingKey(p.getFileName().toString(), stripB), p));
        }
        try (BufferedWriter tsv = Files.newBufferedWriter(out.resolve("per-file.tsv"),
                StandardCharsets.UTF_8)) {
            tsv.write(StructureScores.tsvHeader());
            tsv.newLine();
            for (Path pa : files) {
                Path pb = bByKey.get(pairingKey(pa.getFileName().toString(), ""));
                if (pb == null || !Files.isRegularFile(pb)) {
                    missing++;
                    continue;
                }
                Extract ea = read(pa);
                Extract eb = read(pb);
                if (ea == null || eb == null) {
                    unreadable++;
                    continue;
                }
                List<Block> blocksA;
                List<Block> blocksB;
                try {
                    blocksA = BlockExtractor.extract(ea.content);
                    blocksB = BlockExtractor.extract(eb.content);
                } catch (Exception e) {
                    LOG.warn("not XHTML, skipping {}: {}", pa.getFileName(), e.getMessage());
                    unreadable++;
                    continue;
                }
                String name = pa.getFileName().toString().replaceAll("\\.json$", "");
                StructureScores s = StructureScores.compute(name, group(ea.metadata, groupKey),
                        blocksA, blocksB, minDice);
                all.add(s);
                tsv.write(s.tsvLine());
                tsv.newLine();
            }
        }
        String title = "Structure: " + a.getFileName() + " (A) vs " + b.getFileName() + " (B)";
        String summary = SummaryWriter.summarize(all, title)
                + "\nMissing in B: " + missing + "; unreadable or not XHTML: " + unreadable + "\n";
        Files.writeString(out.resolve("summary.md"), summary, StandardCharsets.UTF_8);
        System.out.print(summary);
        return all;
    }

    private static final class Extract {
        final Metadata metadata;
        final String content;

        Extract(Metadata metadata, String content) {
            this.metadata = metadata;
            this.content = content;
        }
    }

    /** The container document's metadata and content; null if the file is not an extract. */
    private static Extract read(Path p) {
        try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            List<Metadata> list = JsonMetadataList.fromJson(reader);
            if (list == null || list.isEmpty()) {
                return null;
            }
            Metadata m = list.get(0);
            String content = m.get(TikaCoreProperties.TIKA_CONTENT);
            if (content == null) {
                content = m.get(LEGACY_CONTENT_KEY);
            }
            return content == null ? null : new Extract(m, content);
        } catch (IOException | RuntimeException e) {
            LOG.warn("cannot read {}: {}", p, e.getMessage());
            return null;
        }
    }

    /** "x.docx.pdf.json" with stripB ".pdf" is "x.docx", the key its source's extract has. */
    static String pairingKey(String name, String stripB) {
        String key = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        if (!stripB.isEmpty() && key.endsWith(stripB)) {
            key = key.substring(0, key.length() - stripB.length());
        }
        return key;
    }

    /** The grouping value with its version stripped: "Microsoft Word 2013" and "2016" group. */
    static String group(Metadata m, String key) {
        String v = m.get(key);
        if (v == null || v.isBlank()) {
            return "(none)";
        }
        v = VERSION.matcher(v).replaceFirst("").replaceAll("\\s+", " ").trim();
        if (v.isEmpty()) {
            return "(none)";
        }
        return v.length() > MAX_GROUP_LENGTH ? v.substring(0, MAX_GROUP_LENGTH) : v;
    }
}
