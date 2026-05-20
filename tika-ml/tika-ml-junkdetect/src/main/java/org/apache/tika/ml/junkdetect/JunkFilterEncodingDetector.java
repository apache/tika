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
import org.apache.tika.ml.chardetect.AdaptiveProbe;
import org.apache.tika.parser.ParseContext;
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
@TikaComponent(spi = false, name = "junk-filter-encoding-detector")
public class JunkFilterEncodingDetector implements MetaEncodingDetector {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(JunkFilterEncodingDetector.class);

    /** How many probe bytes to read for decoding candidates.  Matches the
     * default read limit used by the charset base detectors. */
    private static final int DEFAULT_READ_LIMIT = 16384;

    /** Per-script (clean_mean, mojibake_mean) measured by
     *  {@code CalibrationGapDiagnostic} on the labeled charset devtest
     *  (200 records per source × multiple wrong targets).  Used to rescale
     *  per-candidate raw logits to a cross-script-comparable [junk≈0,
     *  clean≈1] scale before arbitration.  Without this, HAN and LATIN
     *  classifiers (which are structurally more permissive — clean mean
     *  ~+0.7 vs HANGUL's +1.7, mojibake mean ~-4 vs HANGUL's -10) would
     *  out-score correct decodings under stricter classifiers on
     *  cross-script comparisons (the Korean→Chinese over-override case).
     *  Falls back to LATIN constants for unmeasured scripts. */
    private static final Map<String, float[]> SCRIPT_CAL = Map.ofEntries(
            Map.entry("LATIN",      new float[]{ 0.773f, -3.240f}),
            Map.entry("HAN",        new float[]{ 0.719f, -4.122f}),
            Map.entry("HANGUL",     new float[]{ 1.697f, -9.700f}),
            Map.entry("CYRILLIC",   new float[]{ 1.524f, -5.041f}),
            Map.entry("ARABIC",     new float[]{ 1.491f, -13.904f}),
            Map.entry("HEBREW",     new float[]{ 1.144f, -13.898f}),
            Map.entry("ARMENIAN",   new float[]{ 1.114f, -15.221f}),
            Map.entry("TIBETAN",    new float[]{ 1.500f, -7.179f}),
            Map.entry("BENGALI",    new float[]{ 1.860f, -5.000f}),
            Map.entry("DEVANAGARI", new float[]{ 1.541f, -5.000f}),
            Map.entry("GREEK",      new float[]{ 1.500f, -13.226f})
    );
    private static final float[] FALLBACK_CAL = SCRIPT_CAL.get("LATIN");

    /** Rescale a raw logit to a [junk≈0, clean≈1] common scale using the
     *  per-script (clean_mean, moji_mean) constants in {@link #SCRIPT_CAL}. */
    private static double calibrate(double rawZ, String script) {
        float[] cal = SCRIPT_CAL.getOrDefault(script, FALLBACK_CAL);
        float clean = cal[0];
        float moji = cal[1];
        double span = clean - moji;
        if (span <= 0) return rawZ;
        return (rawZ - moji) / span;
    }

    /** Cached quality detector.  {@code null} if none is on the classpath. */
    private final TextQualityDetector qualityDetector;

    private int readLimit = DEFAULT_READ_LIMIT;

    public JunkFilterEncodingDetector() {
        // JunkDetector is hardcoded rather than ServiceLoader-discovered so
        // construction never silently leaves this meta detector as a no-op.
        // A model-load failure (e.g. block-table dimension mismatch across
        // JVM Unicode versions) is logged and the detector becomes a no-op.
        TextQualityDetector q = null;
        try {
            q = JunkDetector.loadFromClasspath();
        } catch (Throwable t) {
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

        // Decode each candidate, then HtmlContentCleaner.clean — the same
        // tag-strip + entity-expand TrainJunkModel applies, so train and
        // inference match.  Entity expansion is load-bearing: numeric refs
        // become codepoints whose cross-script transitions expose mojibake
        // under a wrong decoding (AIT5 case).
        Map<Charset, String> candidates = new LinkedHashMap<>();
        for (Charset cs : uniqueCharsets) {
            String decoded = safeDecode(bytes, cs);
            if (decoded != null && !decoded.isEmpty()) {
                decoded = HtmlContentCleaner.clean(decoded);
            }
            if (decoded != null && !decoded.isEmpty()) {
                candidates.put(cs, decoded);
                if (LOG.isTraceEnabled()) {
                    int sampleLen = Math.min(400, decoded.length());
                    String sample = decoded.substring(0, sampleLen)
                            .replace('\n', ' ').replace('\r', ' ');
                    LOG.trace("junk-filter decoded {}: '{}{}' (len={})",
                            cs.name(), sample,
                            decoded.length() > sampleLen ? "…" : "",
                            decoded.length());
                }
            } else {
                LOG.trace("junk-filter decode {} -> null/empty", cs.name());
            }
        }
        if (candidates.size() <= 1) {
            // One or zero candidates produced usable text; nothing to compare.
            LOG.trace("junk-filter <=1 usable candidate, abstaining");
            return Collections.emptyList();
        }
        // When a DECLARATIVE candidate decodes byte-identically to at least
        // one other candidate, honour the declaration — text quality cannot
        // distinguish equivalent decodings, and a downstream divergent byte
        // (e.g. 0xA4 = € in ISO-8859-15, ¤ in windows-1252) will then be
        // decoded as the author intended.
        Charset declared = pickDeclarativeWithEquivalentDecode(context, candidates);
        if (declared != null) {
            float conf = context.getTopConfidenceFor(declared);
            context.setArbitrationInfo("junk-filter-prefer-declarative");
            LOG.trace("junk-filter -> {} (declarative with equivalent decode)",
                    declared.name());
            return List.of(new EncodingResult(declared, conf));
        }
        if (allDecodingsIdentical(candidates)) {
            // All decodings are byte-identical but no DECLARATIVE candidate
            // is present.  Text quality cannot distinguish them; defer to
            // the composite's default ordering.
            context.setArbitrationInfo("junk-filter-identical-decodings");
            LOG.trace("junk-filter all decodings identical, deferring");
            return Collections.emptyList();
        }

        // Calibrated-rescale argmax.  Score each candidate once with the
        // quality detector, rescale per-script to a [junk≈0, clean≈1]
        // common scale, then pick the highest.  The rescaling is what
        // makes cross-script comparisons sound — without it, the more
        // permissive HAN/LATIN classifiers can out-score the stricter
        // HANGUL/ARABIC/HEBREW ones on equal-quality text and arbitrate
        // wrong (the Korean→Chinese case).
        //
        // Operates on raw decoded candidates — the strip-COMMON step that
        // used to live here was removed once γ (whitespace-bigram skip)
        // and NFC normalization landed inside JunkDetector itself.  Those
        // address the same Masada-style whitespace-storm root cause for
        // every caller of JunkDetector and avoid the train/inference
        // distribution divergence that the strip introduced.
        Charset champion = null;
        double championCalZ = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Charset, String> entry : candidates.entrySet()) {
            org.apache.tika.quality.TextQualityScore sc =
                    qualityDetector.score(entry.getValue());
            float rawZ = sc.isUnknown() ? 0f : sc.getZScore();
            String script = sc.isUnknown() ? "LATIN" : sc.getDominantScript();
            double calZ = calibrate(rawZ, script);
            LOG.trace("junk-filter score {} raw_z={} script={} cal_z={}",
                    entry.getKey().name(),
                    String.format(java.util.Locale.ROOT, "%.3f", rawZ),
                    script,
                    String.format(java.util.Locale.ROOT, "%.3f", calZ));
            if (calZ > championCalZ) {
                championCalZ = calZ;
                champion = entry.getKey();
            }
        }
        LOG.trace("junk-filter -> {} (calibrated argmax, cal_z={})",
                champion.name(),
                String.format(java.util.Locale.ROOT, "%.3f", championCalZ));

        float confidence = context.getTopConfidenceFor(champion);
        context.setArbitrationInfo("junk-filter-selected");
        return List.of(new EncodingResult(champion, confidence));
    }

    /**
     * Return the first DECLARATIVE charset whose decoded output equals at
     * least one other candidate's, or {@code null}.
     */
    private static Charset pickDeclarativeWithEquivalentDecode(
            EncodingDetectorContext context, Map<Charset, String> candidates) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getResultType() != EncodingResult.ResultType.DECLARATIVE) {
                    continue;
                }
                String declaredDecoded = candidates.get(er.getCharset());
                if (declaredDecoded == null) {
                    continue; // declared charset failed to decode the probe
                }
                for (Map.Entry<Charset, String> entry : candidates.entrySet()) {
                    if (!entry.getKey().equals(er.getCharset())
                            && declaredDecoded.equals(entry.getValue())) {
                        return er.getCharset();
                    }
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
        // readLimit is the tag-stripped content target; cap raw reads at 512 KB.
        byte[] probe = AdaptiveProbe.read(tis, readLimit, AdaptiveProbe.DEFAULT_RAW_CAP);
        return probe.length == 0 ? null : probe;
    }

    private static String safeDecode(byte[] bytes, Charset charset) {
        try {
            return new String(bytes, charset);
        } catch (Exception e) {
            LOG.debug("Decode failed for {}: {}", charset.name(), e.toString());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // HTML entity expansion
    //
    // Applied to every decoded candidate before quality scoring.  Resolves
    // numeric character refs (&#NNNN; / &#xHHHH;) to their target codepoints
    // and a small set of common named entities.  Malformed entities pass
    // through as literal text.  Sufficient for the AIT5-class failure
    // mode where blogspot/news pages use numeric Malayalam/Bengali entities
    // intermixed with raw UTF-8 codepoints.
    // -----------------------------------------------------------------------

    /**
     * Delegates to {@link HtmlContentCleaner#expandHtmlEntities} — the single
     * implementation shared with training.  Retained here as the historical
     * entry point used by tests and diagnostics.
     */
    static String expandHtmlEntities(String s) {
        return HtmlContentCleaner.expandHtmlEntities(s);
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
