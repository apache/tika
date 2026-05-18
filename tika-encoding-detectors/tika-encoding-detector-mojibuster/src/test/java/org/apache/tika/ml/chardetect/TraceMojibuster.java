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
package org.apache.tika.ml.chardetect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.apache.tika.detect.EncodingResult;

/**
 * Trace each layer of Mojibuster on a set of files: raw NB, post-strip NB,
 * and full Mojibuster.detect().  Helps locate where a specific charset
 * pick comes from in the pipeline.
 */
public final class TraceMojibuster {

    private TraceMojibuster() {
    }

    public static void main(String[] args) throws Exception {
        Path probeDir = null;
        String[] probes = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--probe-dir":
                    probeDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--probes":
                    probes = args[++i].split(",");
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (probeDir == null || probes == null) {
            System.err.println("Usage: TraceMojibuster --probe-dir <dir> --probes p1,p2,...");
            System.exit(1);
        }
        // Load the bundled model from the classpath (same path Mojibuster uses).
        NaiveBayesBigramEncodingDetector rawNb;
        try (InputStream is = MojibusterEncodingDetector.class
                .getResourceAsStream(
                        "/org/apache/tika/ml/chardetect/nb-bigram.bin")) {
            if (is == null) throw new IOException("bundled model not on classpath");
            rawNb = new NaiveBayesBigramEncodingDetector(is);
        }
        MojibusterEncodingDetector det = new MojibusterEncodingDetector();

        for (String pid : probes) {
            Path p = probeDir.resolve(pid);
            if (!Files.exists(p)) {
                System.err.println("Missing: " + p);
                continue;
            }
            byte[] bytes = Files.readAllBytes(p);
            String shortId = pid.contains("/")
                    ? pid.substring(pid.indexOf('/') + 1, pid.indexOf('/') + 13) : pid;
            System.out.println();
            System.out.println("==== " + shortId + "  raw=" + bytes.length + " bytes ====");

            // Layer 1: raw NB on raw bytes (no strip).
            List<EncodingResult> rawResults = rawNb.detect(bytes);
            System.out.println("  raw NB (no strip):       " + fmt(rawResults));

            // Layer 2: NB on HTML-stripped bytes.
            byte[] dst = new byte[bytes.length];
            HtmlByteStripper.Result sr = HtmlByteStripper.strip(bytes, 0, bytes.length, dst, 0);
            if (sr.tagCount >= 1) {
                byte[] stripped = new byte[sr.length];
                System.arraycopy(dst, 0, stripped, 0, sr.length);
                System.out.printf(Locale.ROOT,
                        "  HTML strip: tags=%d, post-strip=%d bytes (%.1f%% kept)%n",
                        sr.tagCount, sr.length, 100.0 * sr.length / bytes.length);
                List<EncodingResult> stripResults = rawNb.detect(stripped);
                System.out.println("  NB on stripped bytes:    " + fmt(stripResults));
            } else {
                System.out.println("  HTML strip: tagCount=0 (backoff, used original)");
            }

            // Layer 3: full Mojibuster (which internally strips conditionally).
            List<EncodingResult> mojiResults = det.detect(bytes);
            System.out.println("  Full Mojibuster.detect:  " + fmt(mojiResults));
        }
    }

    private static String fmt(List<EncodingResult> rs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) {
            if (i > 0) sb.append(", ");
            EncodingResult r = rs.get(i);
            sb.append(r.getCharset().name())
                    .append("@").append(String.format(Locale.ROOT, "%.3f", r.getConfidence()))
                    .append("/").append(r.getResultType());
        }
        if (sb.length() == 0) sb.append("<empty>");
        return sb.toString();
    }
}
