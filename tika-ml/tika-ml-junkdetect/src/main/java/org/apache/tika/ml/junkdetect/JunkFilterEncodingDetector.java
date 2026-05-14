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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
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
@TikaComponent(spi = false, name = "junk-filter-encoding-detector")
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

        // Strip HTML/XML markup before decoding so the quality score reflects
        // body text, not whitespace and tags.  Falls back to the raw probe
        // when no well-formed tags are detected.
        byte[] forDecode = bytes;
        byte[] stripDst = new byte[bytes.length];
        HtmlByteStripper.Result stripped =
                HtmlByteStripper.strip(bytes, 0, bytes.length, stripDst, 0);
        boolean stripUsed = stripped.tagCount > 0 && stripped.length > 0;
        LOG.trace("junk-filter strip: input={}B tagCount={} stripped={}B used={}",
                bytes.length, stripped.tagCount, stripped.length, stripUsed);
        if (stripUsed) {
            forDecode = new byte[stripped.length];
            System.arraycopy(stripDst, 0, forDecode, 0, stripped.length);
        }

        // Decode probe under each candidate, preserving insertion order so
        // tournament seeding is deterministic.
        //
        // Each decoded string is then run through HTML entity expansion.
        // For entity-encoded HTML (numeric refs like &#3405;), this is
        // load-bearing: entity refs are ASCII bytes that decode identically
        // under every candidate charset, so they don't differentiate.
        // After expansion they become real codepoints — and crucially, in
        // the *wrong* decoding (e.g. mojibake-as-HAN), they introduce
        // cross-script transitions (HAN ↔ MALAYALAM mid-document) that the
        // quality detector's script-transition feature correctly penalises.
        // See `20260512-junkdetector-codepoint-hash-plan.md` (AIT5 case).
        Map<Charset, String> candidates = new LinkedHashMap<>();
        for (Charset cs : uniqueCharsets) {
            String decoded = safeDecode(forDecode, cs);
            if (decoded != null && !decoded.isEmpty()) {
                decoded = expandHtmlEntities(decoded);
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

        // Pairwise tournament: the first candidate seeds the champion slot;
        // every subsequent candidate challenges the current champion.
        Iterator<Map.Entry<Charset, String>> it = candidates.entrySet().iterator();
        Map.Entry<Charset, String> champion = it.next();
        LOG.trace("junk-filter tournament seed: {}", champion.getKey().name());
        while (it.hasNext()) {
            Map.Entry<Charset, String> challenger = it.next();
            TextQualityComparison cmp = qualityDetector.compare(
                    champion.getKey().name(), champion.getValue(),
                    challenger.getKey().name(), challenger.getValue());
            LOG.trace("junk-filter compare {} vs {} -> {} (delta={} A={} B={})",
                    champion.getKey().name(), challenger.getKey().name(),
                    cmp.winner(), String.format(java.util.Locale.ROOT, "%.3f", cmp.delta()),
                    cmp.scoreA(), cmp.scoreB());
            if (challenger.getKey().name().equals(cmp.winner())) {
                champion = challenger;
            }
        }
        LOG.trace("junk-filter -> {} (tournament champion)", champion.getKey().name());

        float confidence = context.getTopConfidenceFor(champion.getKey());
        context.setArbitrationInfo("junk-filter-selected");
        return List.of(new EncodingResult(champion.getKey(), confidence));
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

    private static final Pattern ENTITY_DEC = Pattern.compile("&#(\\d{1,7});");
    private static final Pattern ENTITY_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");
    private static final Pattern ENTITY_NAMED =
            Pattern.compile("&(amp|lt|gt|quot|apos|nbsp|copy|reg);");

    /**
     * Expands HTML numeric and a small set of named entity references in
     * {@code s}.  Malformed or out-of-range entities pass through unchanged.
     * The named-entity set is intentionally small — only the universally-
     * declared HTML5 entities that don't depend on a DOCTYPE.  Anything more
     * exotic stays as a literal entity reference (which scores as ASCII noise,
     * the same as it would have before).
     */
    static String expandHtmlEntities(String s) {
        s = ENTITY_DEC.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1));
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // overflow — fall through, leave entity literal
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = ENTITY_HEX.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1), 16);
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // overflow — fall through, leave entity literal
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = ENTITY_NAMED.matcher(s).replaceAll(mr -> {
            switch (mr.group(1)) {
                case "amp":  return "&";
                case "lt":   return "<";
                case "gt":   return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return " ";
                case "copy": return "©";
                case "reg":  return "®";
                default:     return Matcher.quoteReplacement(mr.group());
            }
        });
        return s;
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
