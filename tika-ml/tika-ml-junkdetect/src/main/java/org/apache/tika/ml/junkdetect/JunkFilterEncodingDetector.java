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
package org.apache.tika.ml.junkdetect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityDetector;

/**
 * A {@link MetaEncodingDetector} that arbitrates charset candidates by
 * asking a {@link TextQualityDetector} which decoded candidate looks
 * most like natural text.
 *
 * <p>Each base {@link org.apache.tika.detect.EncodingDetector} in the
 * {@link org.apache.tika.detect.CompositeEncodingDetector} chain emits
 * candidates into the {@link EncodingDetectorContext}.  This meta detector
 * then reads the raw probe bytes, decodes them under each unique candidate
 * charset, and runs pairwise comparisons via
 * {@link TextQualityDetector#compare} to pick the candidate whose decoding
 * produces the cleanest text.  BOM-declared, meta-tag-declared,
 * structural, and statistical candidates all compete on the same footing —
 * quality of the resulting decode is the sole criterion at this layer.
 *
 * <p>The {@link TextQualityDetector} implementation is discovered via
 * {@link ServiceLoader}.  When no implementation is on the classpath,
 * this detector becomes a no-op: {@link #detect} returns an empty list
 * and {@link org.apache.tika.detect.CompositeEncodingDetector} falls
 * back to its default confidence-based ordering.
 *
 * @since Apache Tika 4.0.0 (TIKA-4720)
 */
@TikaComponent(name = "junk-filter-encoding-detector")
public class JunkFilterEncodingDetector implements MetaEncodingDetector {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(JunkFilterEncodingDetector.class);

    /** How many probe bytes to read for decoding candidates.  Matches the
     * default read limit used by the charset base detectors. */
    private static final int DEFAULT_READ_LIMIT = 16384;

    /** Cached quality detector.  {@code null} if none is on the classpath. */
    private final TextQualityDetector qualityDetector;

    private int readLimit = DEFAULT_READ_LIMIT;

    public JunkFilterEncodingDetector() {
        // The junk detector is hardcoded rather than ServiceLoader-discovered
        // so construction cannot silently fail to register a quality detector
        // and leave this meta detector as a no-op.  JunkDetector lives in the
        // same module and loads its bundled model from the classpath.
        TextQualityDetector q = null;
        try {
            q = JunkDetector.loadFromClasspath();
            LOG.debug("Loaded JunkDetector: {}", q.getClass().getName());
        } catch (Throwable t) {
            // A broken model binary (e.g. block-table dimension mismatch
            // across JVM Unicode versions) would otherwise propagate and
            // prevent this meta detector from registering at all.  Fail safe:
            // log and operate as a no-op.
            LOG.warn("Failed to load JunkDetector; JunkFilterEncodingDetector "
                    + "will operate as a no-op: {}", t.toString());
        }
        this.qualityDetector = q;
    }

    /** Test-only / deterministic-wiring constructor. */
    public JunkFilterEncodingDetector(TextQualityDetector qualityDetector) {
        this.qualityDetector = qualityDetector;
    }

    public int getReadLimit() {
        return readLimit;
    }

    public void setReadLimit(int readLimit) {
        this.readLimit = readLimit;
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (qualityDetector == null) {
            return Collections.emptyList();
        }
        if (parseContext == null) {
            return Collections.emptyList();
        }
        EncodingDetectorContext context = parseContext.get(EncodingDetectorContext.class);
        if (context == null || context.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        Set<Charset> uniqueCharsets = context.getUniqueCharsets();
        if (uniqueCharsets.size() <= 1) {
            // Nothing to arbitrate.  Let the composite's default ordering
            // pick the single candidate.
            return Collections.emptyList();
        }

        if (tis == null) {
            context.setArbitrationInfo("junk-filter-no-stream");
            return Collections.emptyList();
        }

        byte[] bytes = readProbe(tis);
        if (bytes == null || bytes.length == 0) {
            context.setArbitrationInfo("junk-filter-empty-stream");
            return Collections.emptyList();
        }
        bytes = stripBomBytes(bytes);

        // Decode probe under each candidate, preserving insertion order so
        // tournament seeding is deterministic.
        Map<Charset, String> candidates = new LinkedHashMap<>();
        for (Charset cs : uniqueCharsets) {
            String decoded = safeDecode(bytes, cs);
            if (decoded != null && !decoded.isEmpty()) {
                candidates.put(cs, decoded);
            }
        }
        if (candidates.size() <= 1) {
            // One or zero candidates produced usable text; nothing to compare.
            return Collections.emptyList();
        }
        if (allDecodingsIdentical(candidates)) {
            // Byte-identical decodings (typical on pure-ASCII probes).
            // Text quality cannot distinguish them.  Prefer an author
            // declaration (BOM / HTML meta charset / HTTP Content-Type)
            // over statistical or structural candidates: if the document
            // tells us what it is and the bytes are compatible with that
            // claim, honour it.
            Charset declared = pickDeclarative(context, candidates.keySet());
            if (declared != null) {
                float conf = context.getTopConfidenceFor(declared);
                context.setArbitrationInfo("junk-filter-prefer-declarative");
                return List.of(new EncodingResult(declared, conf));
            }
            context.setArbitrationInfo("junk-filter-identical-decodings");
            return Collections.emptyList();
        }

        // Pairwise tournament: the first candidate seeds the champion slot;
        // every subsequent candidate challenges the current champion.
        Iterator<Map.Entry<Charset, String>> it = candidates.entrySet().iterator();
        Map.Entry<Charset, String> champion = it.next();
        while (it.hasNext()) {
            Map.Entry<Charset, String> challenger = it.next();
            TextQualityComparison cmp = qualityDetector.compare(
                    champion.getKey().name(), champion.getValue(),
                    challenger.getKey().name(), challenger.getValue());
            if ("B".equals(cmp.winner())) {
                champion = challenger;
            }
        }

        float confidence = context.getTopConfidenceFor(champion.getKey());
        context.setArbitrationInfo("junk-filter-selected");
        return List.of(new EncodingResult(champion.getKey(), confidence));
    }

    /**
     * Return the first DECLARATIVE charset in {@code context} whose charset
     * is also in {@code eligible}, or {@code null} if no declarative result
     * matches an eligible candidate.  "Eligible" = present in the candidates
     * we actually decoded (i.e. excludes candidates that failed to decode).
     */
    private static Charset pickDeclarative(EncodingDetectorContext context,
                                           Set<Charset> eligible) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getResultType() == EncodingResult.ResultType.DECLARATIVE
                        && eligible.contains(er.getCharset())) {
                    return er.getCharset();
                }
            }
        }
        return null;
    }

    private static boolean allDecodingsIdentical(Map<Charset, String> candidates) {
        String first = null;
        for (String s : candidates.values()) {
            if (first == null) {
                first = s;
            } else if (!first.equals(s)) {
                return false;
            }
        }
        return true;
    }

    private byte[] readProbe(TikaInputStream tis) throws IOException {
        try {
            tis.mark(readLimit);
            byte[] buf = new byte[readLimit];
            int total = 0;
            int read;
            while (total < readLimit
                    && (read = tis.read(buf, total, readLimit - total)) != -1) {
                total += read;
            }
            if (total == 0) {
                return null;
            }
            if (total < readLimit) {
                byte[] trimmed = new byte[total];
                System.arraycopy(buf, 0, trimmed, 0, total);
                return trimmed;
            }
            return buf;
        } finally {
            tis.reset();
        }
    }

    private static String safeDecode(byte[] bytes, Charset charset) {
        try {
            return new String(bytes, charset);
        } catch (Exception e) {
            LOG.debug("Decode failed for {}: {}", charset.name(), e.toString());
            return null;
        }
    }

    /**
     * Strip a leading byte-order mark, if any.  UTF-32 signatures are
     * checked before UTF-16 because the UTF-32 LE BOM ({@code FF FE 00 00})
     * starts with the UTF-16 LE BOM ({@code FF FE}).
     */
    private static byte[] stripBomBytes(byte[] bytes) {
        int bomLen = bomLength(bytes);
        if (bomLen == 0) {
            return bytes;
        }
        return Arrays.copyOfRange(bytes, bomLen, bytes.length);
    }

    private static int bomLength(byte[] b) {
        if (b.length >= 4
                && (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x00
                && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xFF) {
            return 4; // UTF-32BE
        }
        if (b.length >= 4
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE
                && (b[2] & 0xFF) == 0x00 && (b[3] & 0xFF) == 0x00) {
            return 4; // UTF-32LE
        }
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return 3; // UTF-8
        }
        if (b.length >= 2
                && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return 2; // UTF-16BE
        }
        if (b.length >= 2
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return 2; // UTF-16LE
        }
        return 0;
    }

}
