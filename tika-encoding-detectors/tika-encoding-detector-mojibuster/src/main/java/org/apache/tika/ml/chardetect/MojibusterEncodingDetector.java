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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.BOMDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingProbeCache;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.HighByteLetterStats;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Charset detection pipeline: structural checks for UTF-32, UTF-8 and
 * ISO-2022, then the hashed byte n-gram model ({@link HashedNgramEncodingDetector})
 * for everything else, UTF-16 and UTF-32 included.
 *
 * <p>Order of operations:</p>
 * <ol>
 *   <li><strong>Pure ASCII</strong> (no NUL, no byte &ge; 0x80, no C0 control other than
 *       TAB, LF, FF, CR, ESC): ISO-2022 if its escapes decode to CJK, else the
 *       windows-1252 fallback.</li>
 *   <li><strong>UTF-32 codepoint validity</strong> via {@link WideUnicodeDetector}: a
 *       STRUCTURAL candidate.</li>
 *   <li><strong>UTF-8 grammar</strong>: a STRUCTURAL candidate when the probe is UTF-8;
 *       vetoes the model's UTF-8 when it cannot be.</li>
 *   <li><strong>Hashed n-gram model</strong> on the HTML-stripped probe: STATISTICAL
 *       candidates, with UTF-16 vetoed under an endianness whose surrogates are broken.</li>
 * </ol>
 *
 * <p><strong>BOM detection is NOT handled here.</strong>  The canonical
 * location is {@code org.apache.tika.detect.BOMDetector} (tika-core),
 * SPI-registered, runs first in {@code DefaultEncodingDetector}'s
 * chain and emits a {@code DECLARATIVE} candidate.  This pipeline
 * composes with that detector externally, not internally.</p>
 */
@TikaComponent(spi = false, name = "mojibuster-encoding-detector")
public class MojibusterEncodingDetector implements EncodingDetector {

    private static final Logger LOG =
            LoggerFactory.getLogger(MojibusterEncodingDetector.class);

    // Probe sized by tag-stripped content (16 KB target), capped at 512 KB raw.
    // Markup-heavy pages whose distinguishing bytes (esp. UTF-8 multi-byte
    // sequences) sit past a fixed 16 KB raw window would otherwise starve the
    // structural UTF-8 check and the model. See AdaptiveProbe.
    private static final int PROBE_CONTENT_TARGET = AdaptiveProbe.DEFAULT_CONTENT_TARGET;
    private static final int PROBE_RAW_CAP = AdaptiveProbe.DEFAULT_RAW_CAP;

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
     * Minimum HTML entity count to apply the stripper even when no
     * well-formed tags are present.  A single stray {@code &amp;}
     * mention in plain prose shouldn't trigger the strip path, but
     * entity-heavy content (HTML-quoted text in a plain-text file,
     * truncated reads where the leading tag was lost, etc.) should.
     */
    private static final int MIN_ENTITY_COUNT_TO_USE_STRIP = 3;

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

    /** Confidence for an ISO-2022-JP/KR/CN structural candidate (7-bit, escape-based). */
    private static final float ISO2022_STRUCTURAL_CONF = 0.95f;

    /** ISO-2022 decode-verify: a stray {@code ESC $} in plain ASCII must not win, so
     *  require the decode to yield real CJK at near-zero replacement rate. */
    private static final int ISO2022_MIN_CJK = 4;
    private static final double ISO2022_MAX_FFFD_RATE = 0.05;

    /** False-CJK veto: drop a legacy-CJK model candidate whose UTF-8-stripped decode
     *  fails above this rate.  Post-strip, real CJK (pure or mixed) is ≤1.6% and
     *  genuine false-CJK ≥5.3%, so ~2.5% separates them (see CjkDecodeValidator). */
    private static final double CJK_FAILURE_VETO_THRESHOLD = 0.025;

    /** Confidence for the windows-1252 fallback emitted on empty/ASCII probes: an "I don't know". */
    private static final float FALLBACK_CONFIDENCE = 0.1f;

    /**
     * Maximum fraction of malformed-UTF-8 bytes we tolerate before
     * disqualifying the model's UTF-8 pick.  Real-world UTF-8 files often contain
     * one or two corrupted bytes (copy-paste accidents, truncation,
     * transport flips) — rejecting them outright would force the detector
     * to drop a high-confidence UTF-8 classification on otherwise-valid
     * text and fall through to {@code AutoDetectReader.detect}, which
     * raises {@code TikaException} when the chain returns no candidates.
     * Per-byte error rate governing LONG probes (the absolute cap below is a
     * floor for short ones).  0.01% (~1 malformed sequence per 10 KB)
     * accommodates real UTF-8 with a few stray/corrupt bytes (e.g. a 150 KB
     * page with 4 errors = 0.003%) while still rejecting a win-1252 page
     * misread as UTF-8 (a 20 KB Western page surfaces ~14 invalid sequences =
     * 0.07%, 7× over).
     *
     * <p>TACTICAL: remove or revisit when Mojibuster's UTF-8 grammar
     * check is replaced with a probabilistic decoder that returns a
     * confidence score directly.</p>
     */
    private static final double UTF8_MALFORMED_TOLERANCE = 0.0001;

    /**
     * Absolute floor on tolerated UTF-8 error events for SHORT probes, where a
     * rate is meaningless (a 20-byte string with 1 bad byte is 5%).  The
     * effective cap is {@code max(this, probeLen * UTF8_MALFORMED_TOLERANCE)} —
     * so short probes allow 1, long probes are governed by the rate.  (Earlier
     * this was a hard cap applied at all lengths, which wrongly rejected long,
     * genuinely-UTF-8 pages carrying a couple of stray bytes.)
     */
    private static final int UTF8_MAX_TOLERATED_ERRORS = 1;

    /** Minimum valid multi-byte UTF-8 sequences before a tolerated (not clean)
     *  probe is promoted to STRUCTURAL — else a short filename could false-
     *  positive on a single coincidental error (mirrors {@link
     *  CjkDecodeValidator#MIN_HIGH_BYTES}). */
    private static final int MIN_TOLERATED_UTF8_SEQUENCES = 30;

    /**
     * Long probes: errors tolerated per valid multi-byte sequence, literal U+FFFD (EF BF BD,
     * damage from an earlier conversion) not counted as evidence. cc-html train: real UTF-8 at
     * most 0.066, nearest legacy page 0.32.
     */
    private static final double UTF8_ERRORS_PER_SEQUENCE = 0.10;

    /** Windows-1252: the WHATWG-canonical default for unlabeled Western content. */
    private static final String WIN1252 = "windows-1252";

    private final HashedNgramEncodingDetector model;

    /** Loads the bundled model from the classpath. */
    public MojibusterEncodingDetector() throws IOException {
        this.model = new HashedNgramEncodingDetector();
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        byte[] probe = readProbe(tis, parseContext);
        return detect(probe, metadata);
    }

    /** Byte-array entry point without metadata — same as passing {@code null}. */
    public List<EncodingResult> detect(byte[] probe) {
        return detect(probe, null);
    }

    /**
     * Byte-array entry point with optional metadata.  If metadata's
     * content-type suggests HTML/XML (or is absent), HTML is stripped
     * before the model — but never before the wide-Unicode
     * structural checks, which need byte alignment intact.
     */
    public List<EncodingResult> detect(byte[] probe, Metadata metadata) {
        // BOMDetector reports the BOM; here its bytes are noise (EF BB BF alone is valid
        // multi-byte UTF-8, and they feed the model).  A BOM is a hint, not proof.
        // The wide-Unicode checks still see it.
        byte[] withBom = probe;
        int bom = probe == null ? 0 : BOMDetector.bomLength(probe);
        if (bom > 0) {
            probe = Arrays.copyOfRange(probe, bom, probe.length);
        }
        if (LOG.isTraceEnabled()) {
            int probeLen = probe == null ? 0 : probe.length;
            int highBytes = probe == null ? 0 : HighByteLetterStats.countHighBytes(probe);
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

        // ISO-2022 is 7-bit and escape-based; ISO-2022-KR also shifts with SO/SI.
        boolean asciiRange = isAsciiRange(probe);
        if (asciiRange) {
            Charset iso2022 = detectIso2022Verified(probe);
            if (iso2022 != null) {
                LOG.trace("mojibuster -> {} (iso-2022 structural)", iso2022.name());
                return List.of(new EncodingResult(iso2022, ISO2022_STRUCTURAL_CONF,
                        iso2022.name(), EncodingResult.ResultType.STRUCTURAL));
            }
        }
        // Pure ASCII decodes the same under every ASCII-compatible charset: answer with the
        // WHATWG default rather than a model pick among identical decodes.
        if (isPureAscii(probe)) {
            LOG.trace("mojibuster -> windows-1252 fallback (pure ASCII)");
            return windows1252Fallback();
        }

        java.util.List<EncodingResult> pool = new java.util.ArrayList<>();

        // UTF-32 codepoint validity, plus UTF-16/UTF-32 validity for the model's wide picks.
        WideUnicodeDetector.Result wide = WideUnicodeDetector.analyze(withBom);
        LOG.trace("mojibuster wideUnicode charset={} invalidLE={} invalidBE={} invalid32LE={} invalid32BE={}",
                wide.charset, wide.invalidUtf16Le, wide.invalidUtf16Be,
                wide.invalidUtf32Le, wide.invalidUtf32Be);
        if (wide.charset != null) {
            pool.add(new EncodingResult(wide.charset, UTF32_STRUCTURAL_CONF,
                    wide.charset.name(), EncodingResult.ResultType.STRUCTURAL));
        }

        // UTF-8 is a model class.  Two structural contributions
        // from the grammar check:
        //   • NOT_UTF8 → post-model disqualifier: if grammar proves the
        //     probe cannot be valid UTF-8, drop UTF-8 from the model's output
        //     regardless of confidence.
        //   • LIKELY_UTF8 → emit a STRUCTURAL candidate alongside the model.
        //     sortAndDedup then picks UTF-8 over the model's statistical call
        //     because STRUCTURAL confidence outranks STATISTICAL.
        //   • AMBIGUOUS (pure ASCII or only truncated lead): no
        //     emission; the model + fallbacks handle it.
        StructuralEncodingRules.Utf8Stats utf8Stats = StructuralEncodingRules.utf8Stats(probe);
        StructuralEncodingRules.Utf8Result utf8 = utf8Stats.toResult();
        // TACTICAL: tolerate small corruption.  If the grammar check returned
        // NOT_UTF8 but the malformed-byte fraction is tiny, treat as UTF-8 —
        // a single bad continuation byte in 2KB of CJK is nearly always
        // corruption, not "this isn't UTF-8".  Remove when grammar check is
        // replaced with a probabilistic decoder.
        boolean utf8Tolerated = false;
        if (utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8) {
            int errors = utf8Stats.errors();
            // Length-aware: absolute floor for short probes, rate for long ones.
            int maxTolerated = Math.max(UTF8_MAX_TOLERATED_ERRORS,
                    (int) (probe.length * UTF8_MALFORMED_TOLERANCE));
            int evidence = utf8Stats.sequences() - countReplacementChars(probe);
            boolean perSequence = evidence >= MIN_TOLERATED_UTF8_SEQUENCES
                    && errors <= evidence * UTF8_ERRORS_PER_SEQUENCE;
            if (errors > 0 && (errors <= maxTolerated || perSequence)) {
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
        // Promote on LIKELY_UTF8, or on tolerated errors backed by abundant
        // evidence (evidenceTolerated).  Bare tolerance isn't enough: on a short
        // probe (e.g. a zip entry name, routed here by ZipParser) the model already
        // covers a real UTF-8 case as STATISTICAL, so a lone tolerated error is
        // more likely a coincidentally-valid legacy string.  But on a long,
        // genuinely-UTF-8 document the statistical stage can return an empty pool
        // (TIKA-4810) — without this, one stray legacy byte costs the whole
        // document its STRUCTURAL proof and JunkFilter has nothing to prefer
        // over the declared charset.
        boolean evidenceTolerated = utf8Tolerated
                && utf8Stats.sequences() >= MIN_TOLERATED_UTF8_SEQUENCES;
        if (utf8 == StructuralEncodingRules.Utf8Result.LIKELY_UTF8 || evidenceTolerated) {
            pool.add(new EncodingResult(
                    java.nio.charset.StandardCharsets.UTF_8,
                    UTF8_STRUCTURAL_CONF, "UTF-8",
                    EncodingResult.ResultType.STRUCTURAL));
        }

        // Conditionally strip HTML.  Only if (a) content-type
        // suggests HTML/XML or is unknown, AND (b) the stripper parsed
        // well-formed tags or entities (so we don't corrupt EBCDIC).
        byte[] modelInput = maybeStripHtml(probe, metadata);

        // Empty when the model abstains or calls the probe binary.
        List<EncodingResult> modelResults = model.detect(modelInput);
        if (LOG.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (EncodingResult r : modelResults) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.getCharset().name())
                  .append("@").append(String.format(Locale.ROOT, "%.2f", r.getConfidence()));
            }
            LOG.trace("mojibuster model({}B input) -> [{}]", modelInput.length, sb);
        }
        for (EncodingResult r : modelResults) {
            String name = r.getCharset().name();
            // NOT_UTF8 disqualifier — applied unless the malformed-byte
            // fraction is tiny (see UTF8_MALFORMED_TOLERANCE).
            if ("UTF-8".equals(name)
                    && utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8
                    && !utf8Tolerated) {
                continue;
            }
            if (("UTF-16LE".equals(name) && wide.invalidUtf16Le)
                    || ("UTF-16BE".equals(name) && wide.invalidUtf16Be)) {
                LOG.trace("mojibuster veto {} (broken surrogates)", name);
                continue;
            }
            if (("UTF-32LE".equals(name) && wide.invalidUtf32Le)
                    || ("UTF-32BE".equals(name) && wide.invalidUtf32Be)) {
                LOG.trace("mojibuster veto {} (invalid code points)", name);
                continue;
            }
            // False-CJK veto: a legacy multi-byte CJK pick whose bytes don't
            // validate (high decode-failure on the UTF-8-stripped remainder) is
            // Latin/Cyrillic/garbage mis-read as CJK.  Drop it — if it was the model's
            // only candidate the pool empties and the windows-1252 fallback wins.
            if (CjkDecodeValidator.appliesTo(name)) {
                double failRate = CjkDecodeValidator.strippedFailureRate(modelInput, r.getCharset());
                if (failRate >= CJK_FAILURE_VETO_THRESHOLD) {
                    LOG.trace("mojibuster veto {} (cjk decode-failure {}%)", name,
                            String.format(Locale.ROOT, "%.2f", failRate * 100));
                    continue;
                }
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
        // dropped the model's only pick, the model abstained or called the
        // probe binary), fall back to the WHATWG default.  Downstream
        // JunkFilter / declarative candidates can still override at low confidence.
        if (finalResults.isEmpty()) {
            LOG.trace("mojibuster pool empty -> windows-1252 fallback");
            return windows1252Fallback();
        }
        // 7-bit, NUL-free bytes with C0 controls: the model ran only to catch short BOM-less
        // UTF-16 (Thai 0E xx, Cyrillic 04 xx, ...).  Any other pick decodes like windows-1252.
        if (asciiRange && !isWideUnicode(finalResults.get(0).getCharset())) {
            LOG.trace("mojibuster -> windows-1252 fallback (7-bit with controls)");
            return windows1252Fallback();
        }
        // When the top result is STRUCTURAL (clean UTF-8/UTF-32/ISO-2022 grammar),
        // return only that one result.  JunkFilter must not re-open Mojibuster's
        // internal ordering and pick a lower-ranked STATISTICAL CJK candidate
        // over the STRUCTURAL winner on non-languagey content — that was the 11k
        // regression root cause.  With a single STRUCTURAL result, JunkFilter
        // still arbitrates when *another* detector disagrees (lying HTML headers),
        // which is the intended use case.
        //
        // When the top result is STATISTICAL, keep the full ranked list so that
        // JunkFilter can arbitrate within-family ambiguities (e.g. GB18030 vs
        // x-windows-949), using its language-quality scoring.
        EncodingResult top = finalResults.get(0);
        List<EncodingResult> toReturn = (top.getResultType() == EncodingResult.ResultType.STRUCTURAL)
                ? List.of(top) : finalResults;
        if (LOG.isTraceEnabled()) {
            LOG.trace("mojibuster exit ({}) {}[{}]@{}",
                    top.getResultType() == EncodingResult.ResultType.STRUCTURAL ? "top1" : "full",
                    top.getCharset().name(),
                    top.getResultType(),
                    String.format(Locale.ROOT, "%.2f", top.getConfidence()));
        }
        return toReturn;
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
     * Detect ISO-2022-JP/KR/CN by escape sequence, then verify the decode is
     * real CJK (not a stray {@code ESC $} in ASCII text).  Returns the charset
     * or {@code null}.  Caller guarantees {@code probe} is 7-bit with no NUL.
     */
    private static Charset detectIso2022Verified(byte[] probe) {
        Charset cs = StructuralEncodingRules.detectIso2022(probe);
        if (cs == null) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(probe, cs); // REPLACE on malformed/unmappable
        } catch (Exception e) {
            return null;
        }
        int cjk = 0;
        int fffd = 0;
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD) {
                fffd++;
            } else if (isCjkChar(cp)) {
                cjk++;
            }
        }
        if (cjk >= ISO2022_MIN_CJK
                && fffd <= decoded.length() * ISO2022_MAX_FFFD_RATE) {
            return cs;
        }
        return null;
    }

    /** Han / kana / hangul / CJK punctuation — the scripts ISO-2022-JP/KR/CN carry. */
    private static boolean isCjkChar(int cp) {
        return (cp >= 0x3040 && cp <= 0x30FF)     // hiragana + katakana
                || (cp >= 0x4E00 && cp <= 0x9FFF) // CJK unified
                || (cp >= 0x3400 && cp <= 0x4DBF) // CJK ext A
                || (cp >= 0xAC00 && cp <= 0xD7A3) // hangul syllables
                || (cp >= 0xFF66 && cp <= 0xFF9F) // halfwidth katakana
                || (cp >= 0x3000 && cp <= 0x303F); // CJK symbols/punctuation
    }

    private static int countReplacementChars(byte[] probe) {
        int n = 0;
        for (int i = 0; i + 2 < probe.length; i++) {
            if (probe[i] == (byte) 0xEF && probe[i + 1] == (byte) 0xBF && probe[i + 2] == (byte) 0xBD) {
                n++;
                i += 2;
            }
        }
        return n;
    }

    /**
     * Pure ASCII text: no byte &ge; 0x80, no NUL, and no C0 control other than TAB, LF, FF,
     * CR and ESC (ANSI escapes).  Short BOM-less UTF-16 Thai, Cyrillic or Indic has neither NUL
     * nor high bytes (0E xx, 04 xx, 09 xx), only C0 controls, so those must disqualify.
     */
    static boolean isPureAscii(byte[] probe) {
        for (byte b : probe) {
            int c = b & 0xFF;
            if (c >= 0x80 || (c < 0x20 && c != '\t' && c != '\n' && c != '\f' && c != '\r'
                    && c != 0x1B)) {
                return false;
            }
        }
        return true;
    }

    /** No byte &ge; 0x80 and no NUL. */
    private static boolean isAsciiRange(byte[] probe) {
        for (byte b : probe) {
            if (b <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWideUnicode(Charset cs) {
        String name = cs.name();
        return name.startsWith("UTF-16") || name.startsWith("UTF-32");
    }

    /**
     * Relabel the top result to windows-1252 when top is a non-1252
     * member of {@link CharsetConfusables#SBCS_LATIN_FAMILY} and
     * windows-1252 decodes at least as many Unicode-Letter codepoints
     * at high-byte positions as the candidate does.
     *
     * <p>Rationale: the statistical model can lean toward MacRoman / IBM850 /
     * IBM852 / ISO-8859-X siblings on Western European text where the
     * underlying bytes are actually windows-1252.  Under the wrong
     * sibling, the high bytes decode to symbols / punctuation /
     * unassigned codepoints — not letters.  Under the correct
     * windows-1252, they decode to letters (ä, ö, ü, é, ñ, …).  So a
     * letter-count compare directly distinguishes "this is actually
     * windows-1252 mis-labeled" from "this is genuinely MacRoman".
     * A real MacRoman document with bytes like 0x88 (à in MacRoman)
     * decodes to a letter under MacRoman but a symbol (ˆ) under
     * windows-1252 — letter compare correctly keeps MacRoman.</p>
     *
     * <p>Replaces the prior strict gates ({@code countHighBytes &lt; 5}
     * AND {@code byteIdenticalOnProbe(top, win-1252)}) which left
     * ≥ 5-high-byte Western European pages unprotected.</p>
     */
    private static List<EncodingResult> applyLatinSiblingFallback(byte[] probe,
                                                                  List<EncodingResult> ranked) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        EncodingResult top = ranked.get(0);
        String topName = top.getCharset().name();
        if (WIN1252.equals(topName)) {
            return ranked;
        }
        // Scoped to Western European Latin family only.  Central
        // European (win-1250 / ISO-8859-2 / IBM852), Baltic (win-1257 /
        // ISO-8859-13), Turkish (win-1254), Maltese (ISO-8859-3),
        // Romanian (ISO-8859-16) etc. are NOT in scope — those
        // represent different language regions, and rewriting them to
        // windows-1252 corrupts genuine non-Western content (the
        // letter-count compare ties on most of their Latin letters
        // because Unicode classifies both decodings as Letters,
        // misleading the rule into a wrong flip).
        // MacRoman's curly quotes (0xD2-0xD5) are letters under windows-1252, so the count
        // below always favours windows-1252 on real MacRoman.
        if (!CharsetConfusables.WESTERN_LATIN_FAMILY.contains(topName) || "x-MacRoman".equals(topName)) {
            return ranked;
        }
        Charset win1252 = Charset.forName(WIN1252);
        int winLetters = HighByteLetterStats.countCasedHighByteLetters(probe, win1252);
        int topLetters = HighByteLetterStats.countCasedHighByteLetters(probe, top.getCharset());
        // Tie goes to windows-1252 (WHATWG-canonical default).
        if (winLetters < topLetters) {
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

    /**
     * Sort pool by trust type (STRUCTURAL &gt; DECLARATIVE &gt; STATISTICAL),
     * then by confidence within a type, and deduplicate by charset name keeping
     * the first (highest-priority) instance.  Type priority is load-bearing:
     * the model pins its statistical winner to confidence 1.0, so a structural
     * candidate (UTF-8 grammar proof, UTF-32 codepoint validity) emitted below
     * 1.0 would otherwise lose the sort despite being the stronger signal.
     */
    private static List<EncodingResult> sortAndDedup(List<EncodingResult> pool) {
        if (pool.isEmpty()) {
            return Collections.emptyList();
        }
        pool.sort((a, b) -> {
            int byType = Integer.compare(typeRank(a.getResultType()),
                    typeRank(b.getResultType()));
            return byType != 0 ? byType
                    : Float.compare(b.getConfidence(), a.getConfidence());
        });
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<EncodingResult> out = new java.util.ArrayList<>(pool.size());
        for (EncodingResult r : pool) {
            if (seen.add(r.getCharset().name())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Trust-type priority for sorting: lower wins. */
    private static int typeRank(EncodingResult.ResultType t) {
        switch (t) {
            case STRUCTURAL:
                return 0;
            case DECLARATIVE:
                return 1;
            default:
                return 2;
        }
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
                contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
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
        if (stripped.tagCount < MIN_TAG_COUNT_TO_USE_STRIP
                && stripped.entityCount < MIN_ENTITY_COUNT_TO_USE_STRIP) {
            // No well-formed tags AND not enough entities to be markup —
            // probe isn't markup (or the bytes don't parse as markup in
            // any ASCII-compatible reading).  Use original.
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

    private static byte[] readProbe(TikaInputStream tis, ParseContext parseContext)
            throws IOException {
        EncodingDetectorContext context =
                parseContext == null ? null : parseContext.get(EncodingDetectorContext.class);
        EncodingProbeCache cache = context == null ? null : context.getProbeCache();
        if (cache != null) {
            byte[] cached = cache.get(PROBE_CONTENT_TARGET, PROBE_RAW_CAP);
            if (cached != null) {
                return cached;
            }
        }
        byte[] probe = AdaptiveProbe.read(tis, PROBE_CONTENT_TARGET, PROBE_RAW_CAP);
        if (cache != null) {
            cache.put(probe, PROBE_CONTENT_TARGET, PROBE_RAW_CAP);
        }
        return probe;
    }
}
