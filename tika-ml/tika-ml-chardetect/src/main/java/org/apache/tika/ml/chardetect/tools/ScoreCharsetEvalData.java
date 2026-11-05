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
package org.apache.tika.ml.chardetect.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.icu4j.Icu4jEncodingDetector;
import org.apache.tika.detect.universal.UniversalEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Scores detectors on {@link BuildCharsetEvalData} output. A prediction is correct when it decodes
 * the probe to the same string as the charset the probe was written in. No fold table.
 *
 * <p>Each probe is scored without a BOM and again with one: the true BOM for Unicode charsets, a lying
 * UTF-8 BOM for legacy ones. Accuracy should not depend on it.
 *
 * <p>Writes one TSV row per (detector, split, charset, lang, variant, kind, bom, probe length):
 * n, correct, accuracy, and the most common wrong prediction.
 *
 * <pre>
 * ScoreCharsetEvalData --data DIR --split test --output FILE.tsv [--lengths 32,128,512,full]
 *     [--max-per-file N]
 * </pre>
 */
public class ScoreCharsetEvalData {

    private static final int FULL = Integer.MAX_VALUE;
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Path data = null, output = null, predictions = null;
        List<String> only = null;
        int shard = 0, shards = 1;
        String split = "test";
        int[] lengths = {32, 128, 512, FULL};
        int maxPerFile = Integer.MAX_VALUE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    data = Path.of(args[++i]);
                    break;
                case "--split":
                    split = args[++i];
                    break;
                case "--output":
                    output = Path.of(args[++i]);
                    break;
                case "--predictions":
                    predictions = Path.of(args[++i]);
                    break;
                case "--shard": {
                    String[] t = args[++i].split("/");
                    shard = Integer.parseInt(t[0]);
                    shards = Integer.parseInt(t[1]);
                    break;
                }
                case "--detectors":
                    only = List.of(args[++i].split(","));
                    break;
                case "--max-per-file":
                    maxPerFile = Integer.parseInt(args[++i]);
                    break;
                case "--lengths":
                    lengths = Arrays.stream(args[++i].split(","))
                            .mapToInt(s -> s.equals("full") ? FULL : Integer.parseInt(s)).toArray();
                    break;
                default:
                    throw new IllegalArgumentException("unknown arg " + args[i]);
            }
        }
        if (data == null || output == null) {
            System.err.println("usage: ScoreCharsetEvalData --data DIR --output FILE.tsv [--split test]");
            System.exit(1);
        }
        Map<String, EncodingDetector> detectors = new LinkedHashMap<>();
        DefaultEncodingDetector tika4x = new DefaultEncodingDetector();
        detectors.put("tika4x", tika4x);
        detectors.put("icu4j", new Icu4jEncodingDetector());
        detectors.put("universal", new UniversalEncodingDetector());
        if (only != null) {
            detectors.keySet().retainAll(only);
        }
        System.err.println("tika4x chain: " + chainNames(tika4x));
        // per-sample rows so two runs can be paired (McNemar) on identical probes
        Writer pw = predictions == null ? null : new java.io.OutputStreamWriter(
                new java.util.zip.GZIPOutputStream(Files.newOutputStream(predictions)), StandardCharsets.UTF_8);
        if (pw != null) {
            pw.write("detector\tcharset\tidx\tlang\tvariant\tkind\tbom\tlength\thigh_bytes\tpred\tok\n");
        }

        Map<String, long[]> cells = new TreeMap<>();
        Map<String, Map<String, Integer>> wrong = new TreeMap<>();
        Map<String, Integer> asciiSkipped = new TreeMap<>();
        List<Path> bins = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(data.resolve(split), "*.bin.gz")) {
            ds.forEach(bins::add);
        }
        bins.sort(null);
        if (shards > 1) {
            // by charset file: shard outputs concatenate (predictions are gzip members)
            List<Path> mine = new ArrayList<>();
            for (int k = 0; k < bins.size(); k++) {
                if (k % shards == shard) {
                    mine.add(bins.get(k));
                }
            }
            bins = mine;
        }
        for (Path bin : bins) {
            String csName = bin.getFileName().toString().replace(".bin.gz", "");
            Charset truth = Charset.forName(csName);
            Path metaPath = bin.resolveSibling(csName + ".meta.jsonl.gz");
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(bin))));
                 BufferedReader meta = new BufferedReader(new InputStreamReader(
                         new GZIPInputStream(Files.newInputStream(metaPath)), StandardCharsets.UTF_8))) {
                for (int n = 0; n < maxPerFile; n++) {
                    int len;
                    try {
                        len = in.readUnsignedShort();
                    } catch (EOFException e) {
                        break;
                    }
                    byte[] chunk = new byte[len];
                    in.readFully(chunk);
                    JsonNode m = JSON.readTree(meta.readLine());
                    String cellBase = csName + "\t" + m.get("lang").asText() + "\t" + m.get("variant").asText()
                            + "\t" + (m.get("subst").asBoolean() ? "subst" : "native");
                    for (int L : lengths) {
                        byte[] probe = L >= chunk.length ? chunk : Arrays.copyOf(chunk, L);
                        String lenKey = L == FULL ? "full" : Integer.toString(L);
                        if (BuildCharsetEvalData.asciiEquivalent(probe, truth)) {
                            // free pass for any ASCII-compatible guess: count, don't score
                            asciiSkipped.merge(split + "\t" + cellBase + "\t" + lenKey, 1, Integer::sum);
                            continue;
                        }
                        String expected = new String(probe, truth);
                        String bucket = highBucket(probe);
                        for (String bom : bomModes(truth)) {
                            byte[] input = withBom(bom, truth, probe);
                            for (Map.Entry<String, EncodingDetector> d : detectors.entrySet()) {
                                String pred = top(d.getValue(), input);
                                // the BOM itself is not scored: compare body decodes
                                boolean ok = pred != null
                                        && new String(probe, Charset.forName(pred)).equals(expected);
                                String key = d.getKey() + "\t" + split + "\t" + cellBase + "\t" + bom + "\t"
                                        + lenKey + "\t" + bucket;
                                if (pw != null) {
                                    pw.write(d.getKey() + "\t" + csName + "\t" + n + "\t"
                                            + cellBase.substring(cellBase.indexOf('\t') + 1) + "\t" + bom + "\t" + lenKey
                                            + "\t" + bucket + "\t" + pred + "\t" + (ok ? 1 : 0) + "\n");
                                }
                                long[] c = cells.computeIfAbsent(key, k -> new long[2]);
                                c[0]++;
                                if (ok) {
                                    c[1]++;
                                } else {
                                    wrong.computeIfAbsent(key, k -> new TreeMap<>())
                                            .merge(pred == null ? "(none)" : pred, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
            System.err.println("scored " + csName);
        }
        if (pw != null) {
            pw.close();
        }
        try (Writer w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            w.write("detector\tsplit\tcharset\tlang\tvariant\tkind\tbom\tlength\thigh_bytes\tn\tcorrect\taccuracy\ttop_wrong"
                    + "\tascii_probes_skipped\n");
            for (Map.Entry<String, long[]> e : cells.entrySet()) {
                long[] c = e.getValue();
                String topWrong = "";
                Map<String, Integer> wm = wrong.get(e.getKey());
                if (wm != null) {
                    Map.Entry<String, Integer> t = wm.entrySet().stream().max(Map.Entry.comparingByValue()).get();
                    topWrong = t.getKey() + ":" + t.getValue();
                }
                String[] f = e.getKey().split("\t");
                String cellKey = String.join("\t", Arrays.copyOfRange(f, 1, 6)) + "\t" + f[7];
                w.write(String.format(Locale.ROOT, "%s\t%d\t%d\t%.4f\t%s\t%d%n", e.getKey(), c[0], c[1],
                        (double) c[1] / c[0], topWrong, asciiSkipped.getOrDefault(cellKey, 0)));
            }
        }
    }

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    /**
     * BOM is a hint, not a declaration. Unicode truths: none, true, and a lie (UTF-16LE BOM on UTF-8,
     * opposite byte order on UTF-16). Legacy truths: none, lying UTF-8 BOM, lying UTF-16LE BOM.
     */
    static List<String> bomModes(Charset truth) {
        switch (truth.name()) {
            case "UTF-8":
                return List.of("none", "true", "lying-utf16le");
            case "UTF-16LE":
            case "UTF-16BE":
                return List.of("none", "true", "swapped");
            default:
                return List.of("none", "lying-utf8", "lying-utf16le");
        }
    }

    static byte[] withBom(String mode, Charset truth, byte[] body) {
        boolean le = truth.name().equals("UTF-16LE");
        boolean be = truth.name().equals("UTF-16BE");
        byte[] bom;
        switch (mode) {
            case "none":
                return body;
            case "lying-utf8":
                bom = UTF8_BOM;
                break;
            case "lying-utf16le":
                bom = LE_BOM;
                break;
            case "true":
                bom = le ? LE_BOM : be ? BE_BOM : UTF8_BOM;
                break;
            case "swapped":
                bom = le ? BE_BOM : LE_BOM;
                break;
            default:
                throw new IllegalArgumentException(mode);
        }
        byte[] out = Arrays.copyOf(bom, bom.length + body.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    static String top(EncodingDetector d, byte[] probe) {
        try (TikaInputStream t = TikaInputStream.get(probe)) {
            List<EncodingResult> r = d.detect(t, new Metadata(), new ParseContext());
            return r.isEmpty() ? null : r.get(0).getCharset().name();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Non-ASCII byte count bucket of the probe: 0 (7-bit charsets), 1, 2-5, 6-20, 21+. */
    static String highBucket(byte[] b) {
        int n = 0;
        for (byte x : b) {
            if (x < 0) {
                n++;
            }
        }
        return n == 0 ? "0" : n <= 1 ? "1" : n <= 5 ? "2-5" : n <= 20 ? "6-20" : "21+";
    }


    static String chainNames(CompositeEncodingDetector c) {
        List<String> names = new ArrayList<>();
        for (EncodingDetector d : c.getDetectors()) {
            names.add(d.getClass().getSimpleName());
        }
        return names.toString();
    }
}
