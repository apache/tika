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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Rebalance the per-class training corpus so all Western European Latin
 * SBCS classes have the same underlying source text — eliminating the
 * "common ASCII bigrams pull toward MacRoman" bias surfaced by
 * {@link DiagnoseDiscrimination}.
 *
 * <p>Reads {@code windows-1252.bin.gz} (which contains samples drawn
 * from all 19 Western European languages), decodes each sample under
 * windows-1252 to get the original text, then re-encodes that text
 * under each target charset (MacRoman / IBM850 / IBM500 / IBM1047),
 * writing new {@code <target>.bin.gz} files.  Samples whose codepoints
 * cannot be encoded under a target are skipped (handled via the encoder's
 * REPORT action — keeps the per-class distribution clean rather than
 * importing replacement chars that aren't part of the target's natural
 * vocabulary).</p>
 *
 * <p>Result: MacRoman / IBM850 / IBM500 / IBM1047 see the SAME source
 * text as windows-1252, just encoded differently — so the only
 * cross-class differences are byte-position differences, which is
 * exactly what the model should be learning.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   java RebalanceCharsetTraining \
 *       --train-dir &lt;dir containing windows-1252.bin.gz&gt; \
 *       --output-dir &lt;dir to write re-encoded .bin.gz files&gt; \
 *       [--targets x-MacRoman,IBM850,IBM500,IBM1047]
 * </pre>
 */
public final class RebalanceCharsetTraining {

    private static final int MAX_SAMPLE_BYTES = 65_535;

    private RebalanceCharsetTraining() {
    }

    public static void main(String[] args) throws IOException {
        Path trainDir = null;
        Path outputDir = null;
        String[] targets = {"x-MacRoman", "IBM850", "IBM500", "IBM1047"};
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--train-dir":
                    trainDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--targets":
                    targets = args[++i].split(",");
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (trainDir == null || outputDir == null) {
            System.err.println("Usage: RebalanceCharsetTraining"
                    + " --train-dir <dir>  --output-dir <dir>"
                    + " [--targets x-MacRoman,IBM850,IBM500,IBM1047]");
            System.exit(1);
        }
        Files.createDirectories(outputDir);

        Path source = trainDir.resolve("windows-1252.bin.gz");
        if (!Files.exists(source)) {
            System.err.println("No source: " + source);
            System.exit(1);
        }
        Charset win1252 = Charset.forName("windows-1252");

        Map<String, CharsetEncoder> encoders = new LinkedHashMap<>();
        Map<String, DataOutputStream> outs = new LinkedHashMap<>();
        Map<String, long[]> stats = new LinkedHashMap<>();
        for (String t : targets) {
            String javaName = toJavaCharsetName(t);
            Charset cs;
            try {
                cs = Charset.forName(javaName);
            } catch (Exception ex) {
                System.err.println("Unsupported charset: " + t + " (java=" + javaName + ")");
                continue;
            }
            // IGNORE drops unencodable codepoints (e.g. windows-1252's
            // Š / ž / Czech letters that MacRoman / IBM850 don't have)
            // but keeps the rest of the sample.  Preserves bigram
            // statistics for the encodable substring instead of throwing
            // away the whole sample.
            CharsetEncoder enc = cs.newEncoder()
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .onMalformedInput(CodingErrorAction.IGNORE);
            encoders.put(t, enc);
            Path outFile = outputDir.resolve(t + ".bin.gz");
            DataOutputStream dos = new DataOutputStream(
                    new GZIPOutputStream(new FileOutputStream(outFile.toFile())));
            outs.put(t, dos);
            stats.put(t, new long[]{0, 0, 0}); // kept, dropped, bytesWritten
        }

        long sourceSamples = 0;
        try (DataInputStream dis = new DataInputStream(
                new GZIPInputStream(new FileInputStream(source.toFile())))) {
            while (true) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (java.io.EOFException eof) {
                    break;
                }
                byte[] sample = new byte[len];
                dis.readFully(sample);
                String text = new String(sample, win1252);
                sourceSamples++;
                for (Map.Entry<String, CharsetEncoder> e : encoders.entrySet()) {
                    String label = e.getKey();
                    CharsetEncoder enc = e.getValue();
                    enc.reset();
                    long[] s = stats.get(label);
                    try {
                        ByteBuffer bb = enc.encode(CharBuffer.wrap(text));
                        byte[] re = new byte[bb.remaining()];
                        bb.get(re);
                        if (re.length == 0 || re.length > MAX_SAMPLE_BYTES) {
                            s[1]++;
                            continue;
                        }
                        DataOutputStream dos = outs.get(label);
                        dos.writeShort(re.length);
                        dos.write(re);
                        s[0]++;
                        s[2] += re.length;
                    } catch (Exception ex) {
                        s[1]++;
                    }
                }
            }
        }

        for (DataOutputStream d : outs.values()) {
            d.close();
        }
        System.out.printf(Locale.ROOT, "%nRead %,d source samples from %s%n",
                sourceSamples, source.getFileName());
        for (String t : targets) {
            long[] s = stats.get(t);
            if (s == null) continue;
            System.out.printf(Locale.ROOT,
                    "  %-15s  kept=%,d  dropped=%,d  bytes=%,d  → %s%n",
                    t, s[0], s[1], s[2], outputDir.resolve(t + ".bin.gz"));
        }
        System.out.println();
        System.out.println("Other classes in " + trainDir + " are unchanged.");
        System.out.println("To train on the rebalanced data, point TrainNaiveBayesBigram at"
                + " a dir that combines this output with the other unchanged classes.");
    }

    /**
     * Mirrors the training-label → Java charset mapping in
     * BuildCharsetTrainingData / NaiveBayesBigramEncodingDetector.
     */
    private static String toJavaCharsetName(String label) {
        switch (label) {
            case "x-mac-cyrillic": return "x-MacCyrillic";
            case "windows-874":   return "x-windows-874";
            case "IBM420-ltr":
            case "IBM420-rtl":    return "IBM420";
            case "IBM424-ltr":
            case "IBM424-rtl":    return "IBM424";
            default: return label;
        }
    }
}
