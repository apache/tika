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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Naive-Bayes pipeline detector: structural checks for wide Unicode
 * + BOMs before falling through to the bigram NB classifier for
 * everything else.
 *
 * <p>Order of operations:</p>
 * <ol>
 *   <li><strong>UTF-32 codepoint validity</strong> via
 *       {@link WideUnicodeDetector}.  4-byte-aligned probes with
 *       valid Unicode codepoints in exactly one endian order are
 *       deterministically UTF-32.</li>
 *   <li><strong>UTF-16 column-asymmetry specialist</strong>.  Stride-2
 *       column histograms reliably distinguish UTF-16-LE / BE from
 *       other content — a question bigram NB fundamentally can't
 *       answer (LE and BE produce the same bigram multiset).</li>
 *   <li><strong>Naive-Bayes bigram classifier</strong>.  Handles the
 *       single-byte and multi-byte CJK classes where byte-bigrams
 *       are the natural discriminative signal.</li>
 * </ol>
 *
 * <p><strong>BOM detection is NOT handled here.</strong>  The canonical
 * location is {@code org.apache.tika.detect.BOMDetector} (tika-core),
 * SPI-registered, runs first in {@code DefaultEncodingDetector}'s
 * chain and emits a {@code DECLARATIVE} candidate.  This pipeline
 * composes with that detector externally, not internally.</p>
 *
 * <p>Each prefix layer short-circuits when it produces a confident
 * candidate.  Conservative: only return at a layer when that layer's
 * structural check is clean.</p>
 */
@TikaComponent(spi = false, name = "mojibuster-encoding-detector")
public class MojibusterEncodingDetector implements EncodingDetector {

    private static final Logger LOG =
            LoggerFactory.getLogger(MojibusterEncodingDetector.class);

    /** Default NB bigram model on the classpath. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/nb-bigram.bin";

    private static final int MAX_PROBE_BYTES = 4096;

    /**
     * Minimum number of successfully-parsed well-formed tags required
     * before we trust the stripped output.  The stripper only counts
     * actual {@code <tag>} and {@code <!--comment-->} entries — stray
     * {@code <} bytes in plain text don't increment the counter.
     *
     * <p>Using tag count instead of a byte-ratio heuristic is
     * encoding-agnostic: it works for any charset the stripper can
     * reach (ASCII-compatible content).  On a probe that happens to
     * be EBCDIC (where {@code 0x3C} means something other than
     * {@code <}), stray {@code 0x3C} bytes almost never form a
     * well-formed tag structure, so tagCount stays at 0 and we
     * keep the original bytes.</p>
     */
    private static final int MIN_TAG_COUNT_TO_USE_STRIP = 1;

    /**
     * Confidence attached to UTF-32 structural candidates — high but
     * sub-1.0 so the ResultType.STRUCTURAL flag carries meaning
     * without blocking downstream override on mislabeled content.
     */
    private static final float UTF32_STRUCTURAL_CONF = 0.95f;

    /**
     * Confidence attached to the UTF-8 LIKELY candidate emitted when
     * {@link StructuralEncodingRules#checkUtf8} returns LIKELY_UTF8.
     * High but sub-1.0 because short CJK probes can pass UTF-8 grammar
     * by coincidence (FP ≤ 0.77% at 16B, ≤ 0.05% at 256B on our training
     * corpus).  Downstream language-signal arbitration decides genuine
     * FPs — the gate's job is to nominate, not to overrule.
     */
    private static final float UTF8_STRUCTURAL_CONF = 0.95f;

    /**
     * Low-evidence threshold (number of high bytes &ge; 0x80) below
     * which {@link #applyLatinSiblingFallback} fires.  Short probes
     * (sparse Latin in HTML, vCard fragments) get non-1252 Latin
     * sibling picks from NB on bias / hash-bucket accidents; when
     * the probe decodes byte-identically under windows-1252 we
     * relabel to windows-1252 — the WHATWG-canonical answer.  Above
     * this threshold the model has genuine evidence to discriminate
     * sibling code pages.
     */
    private static final int LATIN_FALLBACK_HIGH_BYTE_THRESHOLD = 5;

    /** Confidence for the windows-1252 fallback emitted on empty/ASCII probes. */
    private static final float FALLBACK_CONFIDENCE = 0.1f;

    /**
     * Maximum fraction of malformed-UTF-8 bytes we tolerate before
     * disqualifying NB's UTF-8 pick.  Real-world UTF-8 files often contain
     * one or two corrupted bytes (copy-paste accidents, truncation,
     * transport flips) — rejecting them outright would force the detector
     * to drop a high-confidence UTF-8 classification on otherwise-valid
     * text and fall through to {@code AutoDetectReader.detect}, which
     * raises {@code TikaException} when the chain returns no candidates.
     * 0.5% (1 byte per 200) accommodates "tiny corruption" while still
     * rejecting genuinely-non-UTF-8 streams (which would have many more
     * malformed bytes).
     *
     * <p>TACTICAL: remove or revisit when Mojibuster's UTF-8 grammar
     * check is replaced with a probabilistic decoder that returns a
     * confidence score directly.</p>
     */
    private static final double UTF8_MALFORMED_TOLERANCE = 0.005;

    /** Windows-1252: the WHATWG-canonical default for unlabeled Western content. */
    private static final String WIN1252 = "windows-1252";

    private final NaiveBayesBigramEncodingDetector nb;
    private final Utf16SpecialistEncodingDetector utf16;

    /**
     * Default SPI constructor: load the NB bigram model from the
     * classpath at {@link #DEFAULT_MODEL_RESOURCE}.  The UTF-16
     * specialist loads its own model the same way.
     */
    public MojibusterEncodingDetector() throws IOException {
        this.nb = loadFromClasspath();
        this.utf16 = new Utf16SpecialistEncodingDetector();
    }

    public MojibusterEncodingDetector(Path nbModelPath) throws IOException {
        this.nb = new NaiveBayesBigramEncodingDetector(nbModelPath);
        this.utf16 = new Utf16SpecialistEncodingDetector();
    }

    private static NaiveBayesBigramEncodingDetector loadFromClasspath() throws IOException {
        InputStream in = MojibusterEncodingDetector.class
                .getResourceAsStream(DEFAULT_MODEL_RESOURCE);
        if (in == null) {
            throw new IOException(
                    "NB bigram model not found on classpath at " + DEFAULT_MODEL_RESOURCE);
        }
        try (InputStream stream = in) {
            return new NaiveBayesBigramEncodingDetector(stream);
        }
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        byte[] probe = readProbe(tis);
        return detect(probe, metadata);
    }

    /** Byte-array entry point without metadata — same as passing {@code null}. */
    public List<EncodingResult> detect(byte[] probe) {
        return detect(probe, null);
    }

    /**
     * Byte-array entry point with optional metadata.  If metadata's
     * content-type suggests HTML/XML (or is absent), HTML is stripped
     * before the NB stage — but never before the wide-Unicode
     * structural checks, which need byte alignment intact.
     */
    public List<EncodingResult> detect(byte[] probe, Metadata metadata) {
        if (LOG.isTraceEnabled()) {
            int probeLen = probe == null ? 0 : probe.length;
            int highBytes = probe == null ? 0 : countHighBytes(probe);
            LOG.trace("mojibuster enter probe={}B highBytes={}", probeLen, highBytes);
        }
        // Empty / near-empty probes: return the WHATWG default so
        // downstream callers don't see an empty list (which propagates
        // up as "Failed to detect the character encoding of a
        // document" in TXTParser / RFC822Parser / etc).  windows-1252
        // at low confidence lets any declarative hint override.
        if (probe == null || probe.length < 2) {
            LOG.trace("mojibuster -> windows-1252 fallback (probe<2B)");
            return windows1252Fallback();
        }

        // Pure-ASCII: no high bytes and no nulls.  Bigram NB cannot
        // discriminate Latin code pages from their ASCII prefix — all
        // ASCII-compatible SBCS encodings produce the same bigram
        // multiset on pure-ASCII bytes.  Return windows-1252 (HTML5 /
        // WHATWG default for unlabeled Western content) before
        // consulting NB so we don't hand back a bias-driven x-MacRoman
        // or IBM850 pick.
        if (isPureAscii(probe)) {
            LOG.trace("mojibuster -> windows-1252 fallback (pure ASCII)");
            return windows1252Fallback();
        }

        // Build a candidate pool from all layers.  Every layer
        // contributes 0-N candidates with appropriate ResultType and
        // confidence.  Structural signals get high but not absolute
        // confidence — content can be mislabeled or structurally
        // coincidental.
        //
        // BOM detection lives outside this pipeline — tika-core's
        // BOMDetector is SPI-registered and emits its own candidate.

        java.util.List<EncodingResult> pool = new java.util.ArrayList<>();

        // UTF-32 codepoint validity — structural candidate.  Also
        // collects UTF-16 surrogate invalidity flags used below.
        WideUnicodeDetector.Result wide = WideUnicodeDetector.analyze(probe);
        LOG.trace("mojibuster wideUnicode charset={} invalidLE={} invalidBE={}",
                wide.charset, wide.invalidUtf16Le, wide.invalidUtf16Be);
        if (wide.charset != null) {
            pool.add(new EncodingResult(wide.charset, UTF32_STRUCTURAL_CONF,
                    wide.charset.name(), EncodingResult.ResultType.STRUCTURAL));
        }

        // UTF-16 specialist (stride-2 column histogram features,
        // maxent).  Gated externally by column-asymmetry evidence to
        // prevent over-fires on legacy CJK bytes whose stride-1 byte
        // patterns don't distinguish UTF-16 from legacy encodings.
        // When the gate fires and the specialist has a confident
        // winner, short-circuit: return a single UTF-16LE/BE
        // STRUCTURAL candidate.  Stride-1 byte bigrams cannot
        // discriminate UTF-16 reliably (see
        // why-stride1-bigrams-dont-work-for-utf16.md), so we keep
        // UTF-16 out of NB training and delegate to the specialist.
        boolean utf16Gate = StructuralEncodingRules.has2ByteColumnAsymmetryEvidence(probe);
        LOG.trace("mojibuster utf16Gate={}", utf16Gate);
        if (utf16Gate) {
            List<EncodingResult> utf16Results = utf16.detect(probe);
            LOG.trace("mojibuster utf16Specialist returned {} candidates", utf16Results.size());
            for (EncodingResult r : utf16Results) {
                String name = r.getCharset().name();
                boolean invalid =
                        ("UTF-16LE".equals(name) && wide.invalidUtf16Le)
                        || ("UTF-16BE".equals(name) && wide.invalidUtf16Be);
                LOG.trace("mojibuster utf16Specialist candidate={} invalid={}", name, invalid);
                if (!invalid) {
                    LOG.trace("mojibuster -> utf16 short-circuit {}", name);
                    return List.of(new EncodingResult(r.getCharset(),
                            UTF32_STRUCTURAL_CONF, r.getLabel(),
                            EncodingResult.ResultType.STRUCTURAL));
                }
            }
        }

        // UTF-8 is a trained NB class.  Two structural contributions
        // from the grammar check:
        //   • NOT_UTF8 → post-NB disqualifier: if grammar proves the
        //     probe cannot be valid UTF-8, drop UTF-8 from NB's output
        //     regardless of confidence.
        //   • LIKELY_UTF8 → emit a STRUCTURAL candidate alongside NB.
        //     Safety net for short probes (e.g. 2-byte probes where
        //     NB picks a coincidental Korean/CJK class because the
        //     single bigram is more common in that class's vocab).
        //     sortAndDedup then picks UTF-8 over NB's statistical call
        //     because STRUCTURAL confidence outranks STATISTICAL.
        //   • AMBIGUOUS (pure ASCII or only truncated lead): no
        //     emission; NB + fallbacks handle it.
        StructuralEncodingRules.Utf8Result utf8 = StructuralEncodingRules.checkUtf8(probe);
        // TACTICAL: tolerate small corruption.  If the grammar check returned
        // NOT_UTF8 but the malformed-byte fraction is tiny, treat as UTF-8 —
        // a single bad continuation byte in 2KB of CJK is nearly always
        // corruption, not "this isn't UTF-8".  Remove when grammar check is
        // replaced with a probabilistic decoder.
        boolean utf8Tolerated = false;
        if (utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8) {
            int errors = StructuralEncodingRules.countUtf8Errors(probe);
            if (errors > 0
                    && (double) errors / probe.length <= UTF8_MALFORMED_TOLERANCE) {
                utf8Tolerated = true;
                LOG.trace("mojibuster utf8 NOT_UTF8 tolerated: {} error events in {}B ({}%)",
                        errors, probe.length,
                        String.format(Locale.ROOT, "%.3f",
                                100.0 * errors / probe.length));
            } else if (errors > 0) {
                LOG.trace("mojibuster utf8 NOT_UTF8 NOT tolerated: {} error events in {}B ({}%)",
                        errors, probe.length,
                        String.format(Locale.ROOT, "%.3f",
                                100.0 * errors / probe.length));
            }
        }
        LOG.trace("mojibuster utf8Check={} tolerated={}", utf8, utf8Tolerated);
        if (utf8 == StructuralEncodingRules.Utf8Result.LIKELY_UTF8) {
            pool.add(new EncodingResult(
                    java.nio.charset.StandardCharsets.UTF_8,
                    UTF8_STRUCTURAL_CONF, "UTF-8",
                    EncodingResult.ResultType.STRUCTURAL));
        }

        // Conditionally strip HTML.  Only if (a) content-type
        // suggests HTML/XML or is unknown, AND (b) the probe looks
        // ASCII-compatible (so we don't corrupt EBCDIC).  If stripping
        // removes < 5% of bytes, treat as non-HTML and use original
        // bytes — protects against stray `<` bytes in plain text.
        byte[] nbInput = maybeStripHtml(probe, metadata);

        // Naive-Bayes top-K candidates — statistical.
        List<EncodingResult> nbResults = nb.detect(nbInput);
        if (LOG.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (EncodingResult r : nbResults) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.getCharset().name())
                  .append("@").append(String.format(Locale.ROOT, "%.2f", r.getConfidence()));
            }
            LOG.trace("mojibuster nb({}B input) -> [{}]", nbInput.length, sb);
        }
        for (EncodingResult r : nbResults) {
            String name = r.getCharset().name();
            // NOT_UTF8 disqualifier — applied unless the malformed-byte
            // fraction is tiny (see UTF8_MALFORMED_TOLERANCE).
            if ("UTF-8".equals(name)
                    && utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8
                    && !utf8Tolerated) {
                continue;
            }
            pool.add(r);
        }

        List<EncodingResult> ranked = sortAndDedup(pool);
        // Low-evidence Latin-sibling → windows-1252 rewrite.  Runs
        // after sort so only the final top candidate is considered
        // for the rewrite, preserving lower-ranked siblings.
        List<EncodingResult> finalResults = applyLatinSiblingFallback(probe, ranked);
        // Never return an empty list.  An empty result propagates up as
        // "Failed to detect the character encoding of a document" in
        // AutoDetectReader.detect, which kills parsing entirely.  When
        // every layer has rejected its candidates (NOT_UTF8 disqualifier
        // dropped NB's only pick, NB returned no candidates at all,
        // wide-Unicode and UTF-16 specialists abstained), fall back to
        // the WHATWG default.  Downstream JunkFilter / declarative
        // candidates can still override at low confidence.
        if (finalResults.isEmpty()) {
            LOG.trace("mojibuster pool empty -> windows-1252 fallback");
            return windows1252Fallback();
        }
        if (LOG.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (EncodingResult r : finalResults) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.getCharset().name())
                  .append("[").append(r.getResultType()).append("]")
                  .append("@").append(String.format(Locale.ROOT, "%.2f", r.getConfidence()));
            }
            LOG.trace("mojibuster exit ({} results) [{}]", finalResults.size(), sb);
        }
        return finalResults;
    }

    /**
     * windows-1252 @ low confidence — used on empty / ASCII-only
     * probes so callers never see an empty result list.
     */
    private static List<EncodingResult> windows1252Fallback() {
        Charset cs = Charset.forName(WIN1252);
        return List.of(new EncodingResult(cs, FALLBACK_CONFIDENCE, WIN1252,
                EncodingResult.ResultType.STATISTICAL));
    }

    /**
     * Pure 7-bit ASCII test: no bytes &ge; 0x80 and no null bytes.
     * Null-byte exclusion prevents misclassifying UTF-16/32 content
     * whose bytes happen to be all &lt; 0x80 (all-Cyrillic UTF-16-LE
     * would satisfy the high-byte test alone).
     */
    private static boolean isPureAscii(byte[] probe) {
        for (byte b : probe) {
            int c = b & 0xFF;
            if (c == 0 || c >= 0x80) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolve UTF-16 to LE or BE once NB has called it "UTF-16".
     *
     * <p>Two deterministic tests:
     * <ol>
     *   <li>Null-density: count null bytes in even-offset positions
     *       vs odd-offset positions.  For ASCII-in-UTF-16-LE the
     *       high byte is 0x00 at odd positions; for BE it's at even
     *       positions.  If one column is clearly null-dominant, that
     *       column indicates the endianness.</li>
     *   <li>Codepoint validity fallback: for ambiguous probes (pure
     *       CJK UTF-16, no nulls in either column) count how many
     *       16-bit codepoints under LE vs BE interpretation land in
     *       assigned Unicode BMP ranges (non-PUA, non-unassigned).
     *       Whichever interpretation yields more valid codepoints
     *       wins.</li>
     * </ol>
     *
     * <p>Also honors the {@code invalidUtf16Le}/{@code invalidUtf16Be}
     * flags from {@link WideUnicodeDetector} — if either endianness
     * is structurally invalid (surrogate-pair violation), the other
     * wins by default.
     *
     * @return the resolved charset, or {@code null} if the probe is
     *         structurally invalid under both interpretations
     */
    private static java.nio.charset.Charset disambiguateUtf16(byte[] probe,
                                                              boolean invalidLe,
                                                              boolean invalidBe) {
        if (invalidLe && invalidBe) {
            return null;
        }
        if (invalidLe) {
            return java.nio.charset.Charset.forName("UTF-16BE");
        }
        if (invalidBe) {
            return java.nio.charset.Charset.forName("UTF-16LE");
        }
        int nullEven = 0;
        int nullOdd = 0;
        for (int i = 0; i + 1 < probe.length; i += 2) {
            if (probe[i] == 0) nullEven++;
            if (probe[i + 1] == 0) nullOdd++;
        }
        // Clear null-density winner: one column is ≥ 3× more
        // null-dominant than the other.
        if (nullEven >= 3 * Math.max(1, nullOdd)) {
            return java.nio.charset.Charset.forName("UTF-16BE");
        }
        if (nullOdd >= 3 * Math.max(1, nullEven)) {
            return java.nio.charset.Charset.forName("UTF-16LE");
        }
        // Ambiguous on null-density (CJK content).  Count valid BMP
        // codepoints under each interpretation.  A "valid" codepoint
        // is any non-zero codepoint outside the surrogate range
        // (0xD800-0xDFFF) — for CJK content most bytes map into
        // assigned blocks, and random-byte-interpreted-as-UTF-16
        // produces many surrogate-range halves.
        int validLe = 0;
        int validBe = 0;
        for (int i = 0; i + 1 < probe.length; i += 2) {
            int lo = probe[i] & 0xFF;
            int hi = probe[i + 1] & 0xFF;
            int leCp = (hi << 8) | lo;
            int beCp = (lo << 8) | hi;
            if (leCp != 0 && (leCp < 0xD800 || leCp > 0xDFFF)) {
                validLe++;
            }
            if (beCp != 0 && (beCp < 0xD800 || beCp > 0xDFFF)) {
                validBe++;
            }
        }
        return validLe >= validBe
                ? java.nio.charset.Charset.forName("UTF-16LE")
                : java.nio.charset.Charset.forName("UTF-16BE");
    }

    /**
     * Relabel the top result to windows-1252 when all of the following
     * hold:
     * <ul>
     *   <li>top candidate is a non-1252 member of
     *       {@link CharsetConfusables#SBCS_LATIN_FAMILY};</li>
     *   <li>high-byte count &lt;
     *       {@link #LATIN_FALLBACK_HIGH_BYTE_THRESHOLD};</li>
     *   <li>the probe decodes byte-identically under the candidate
     *       and under windows-1252 — no information is lost by the
     *       rewrite.</li>
     * </ul>
     * Rationale: on sparse-Latin probes NB picks sibling code pages
     * (ISO-8859-3, x-MacRoman, IBM850) on bias.  windows-1252 is the
     * WHATWG-canonical answer and matches downstream test
     * expectations.  Mirrors Mojibuster's LATIN_FALLBACK_WIN1252 rule.
     */
    private static List<EncodingResult> applyLatinSiblingFallback(byte[] probe,
                                                                  List<EncodingResult> ranked) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        if (countHighBytes(probe) >= LATIN_FALLBACK_HIGH_BYTE_THRESHOLD) {
            return ranked;
        }
        EncodingResult top = ranked.get(0);
        String topName = top.getCharset().name();
        if (WIN1252.equals(topName)) {
            return ranked;
        }
        if (!CharsetConfusables.SBCS_LATIN_FAMILY.contains(topName)) {
            return ranked;
        }
        Charset win1252 = Charset.forName(WIN1252);
        if (!DecodeEquivalence.byteIdenticalOnProbe(probe, top.getCharset(), win1252)) {
            return ranked;
        }
        List<EncodingResult> out = new java.util.ArrayList<>(ranked.size());
        out.add(new EncodingResult(win1252, top.getConfidence(), WIN1252,
                top.getResultType()));
        for (int i = 1; i < ranked.size(); i++) {
            out.add(ranked.get(i));
        }
        return out;
    }

    private static int countHighBytes(byte[] probe) {
        int n = 0;
        for (byte b : probe) {
            if ((b & 0xFF) >= 0x80) {
                n++;
            }
        }
        return n;
    }

    /**
     * Sort pool by confidence descending, deduplicate by charset name
     * keeping the highest-confidence instance.  Stable ordering is
     * good enough for current needs; if we need trust-type tiebreaks
     * (STRUCTURAL > DECLARATIVE > STATISTICAL) later, add here.
     */
    private static List<EncodingResult> sortAndDedup(List<EncodingResult> pool) {
        if (pool.isEmpty()) {
            return Collections.emptyList();
        }
        pool.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<EncodingResult> out = new java.util.ArrayList<>(pool.size());
        for (EncodingResult r : pool) {
            if (seen.add(r.getCharset().name())) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Returns stripped bytes if the probe contains well-formed HTML/XML
     * tags; otherwise returns the original probe unchanged.
     *
     * <p>Two guards:</p>
     * <ol>
     *   <li>If content-type is set and doesn't suggest HTML/XML, skip
     *       stripping entirely (trust the label).</li>
     *   <li>If content-type is HTML/XML or absent, RUN the stripper —
     *       but only use its output if it successfully parsed at least
     *       one well-formed tag.  Zero tags means the probe is either
     *       plain text (no markup to strip) or a non-ASCII-compatible
     *       encoding where stray {@code 0x3C} bytes don't form valid
     *       tag structures.</li>
     * </ol>
     */
    private static byte[] maybeStripHtml(byte[] probe, Metadata metadata) {
        String contentType = null;
        if (metadata != null) {
            // Prefer user override, then parser override, then magic, then header.
            contentType = metadata.get(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE);
            if (contentType == null) {
                contentType = metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
            }
            if (contentType == null) {
                contentType = metadata.get(TikaCoreProperties.CONTENT_TYPE_MAGIC_DETECTED);
            }
            if (contentType == null) {
                contentType = metadata.get(Metadata.CONTENT_TYPE);
            }
        }
        if (!shouldTryStrip(contentType)) {
            return probe;
        }
        // Strip into a fresh destination buffer so `probe` stays
        // untouched — essential for the tag-count-zero backoff.
        byte[] dst = new byte[probe.length];
        HtmlByteStripper.Result stripped =
                HtmlByteStripper.strip(probe, 0, probe.length, dst, 0);
        if (stripped.tagCount < MIN_TAG_COUNT_TO_USE_STRIP) {
            // No well-formed tags found — probe isn't markup (or the
            // bytes don't parse as markup in any ASCII-compatible
            // reading).  Use original.
            return probe;
        }
        byte[] trimmed = new byte[stripped.length];
        System.arraycopy(dst, 0, trimmed, 0, stripped.length);
        return trimmed;
    }

    /**
     * Strip if content-type suggests HTML/XML markup, or is absent
     * (mime detection hasn't happened yet — tag-count guard provides
     * the encoding-agnostic safety net).
     */
    private static boolean shouldTryStrip(String contentType) {
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("html") || lower.contains("xml");
    }

    private static byte[] readProbe(TikaInputStream tis) throws IOException {
        tis.mark(MAX_PROBE_BYTES);
        byte[] buf = new byte[MAX_PROBE_BYTES];
        try {
            int n = IOUtils.read(tis, buf);
            if (n < buf.length) {
                byte[] trimmed = new byte[n];
                System.arraycopy(buf, 0, trimmed, 0, n);
                return trimmed;
            }
            return buf;
        } finally {
            tis.reset();
        }
    }
}
