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

/**
 * Byte-level HTML tag stripper used as a preprocess for charset detection.
 *
 * <p>Operates on raw bytes rather than decoded characters: {@code <}, {@code >},
 * quotes, and the ASCII letters in {@code script}/{@code style} are single-byte
 * and preserved in every ASCII-compatible encoding the detector arbitrates
 * between (UTF-8, ISO-8859-*, windows-125*, KOI8-*). This lets us strip once
 * on the raw probe bytes and feed the shorter result to every candidate
 * decoder, instead of decoding-then-stripping N times.
 *
 * <p>Not safe for UTF-16 or UTF-32 input: {@code <} is 2 or 4 bytes there.
 * Callers should handle those candidates separately (they are almost always
 * BOM-identified).
 *
 * <p>Contents of {@code <script>} and {@code <style>} elements, as well as
 * HTML comments, are dropped: they carry bytes that are not natural-language
 * text and pollute language-model scoring.
 *
 * <p>The strip is performed <em>in place</em>: bytes are written back into
 * the input buffer, and the returned length marks the end of the content
 * prefix. This is safe because the state machine maintains the invariant
 * {@code w <= i} at the start of every iteration (where {@code w} is the
 * next write index and {@code i} is the read index), and {@code w <= i - 1}
 * when entering states that may write two bytes in a single tick
 * (LT stray-{@code <}). Every read of {@code buf[i]} is captured into a
 * local before any write that could overlap it, and the {@code i + 1},
 * {@code i + 2} peeks in the LT-{@code !--} check reference positions the
 * write cursor has not reached.
 *
 * <p>Ported from {@code tika-encoding-detector-charsoup} in the tika-main
 * repository.  Placed in this module so the Naive-Bayes pipeline detector
 * can preprocess probes without a cross-module dependency on charsoup.</p>
 */
public final class HtmlByteStripper {

    private static final int TEXT = 0;
    private static final int LT = 1;
    private static final int TAG_NAME = 2;
    private static final int TAG = 3;
    private static final int TAG_QUOTE_D = 4;
    private static final int TAG_QUOTE_S = 5;
    private static final int COMMENT = 6;
    private static final int RAW_BODY = 7;
    private static final int ATTR_NAME = 8;
    private static final int ATTR_AFTER_EQUALS = 9;

    private static final byte[] SCRIPT = {'s', 'c', 'r', 'i', 'p', 't'};
    private static final byte[] STYLE = {'s', 't', 'y', 'l', 'e'};
    private static final byte[] END_SCRIPT = {'<', '/', 's', 'c', 'r', 'i', 'p', 't'};
    private static final byte[] END_STYLE = {'<', '/', 's', 't', 'y', 'l', 'e'};

    /**
     * HTML attribute names whose values contain natural-language text
     * visible to end users.  When one of these is found inside a tag,
     * the stripper emits the attribute's value bytes to the output
     * stream (preserving language signal for downstream detectors).
     *
     * <p>Lowercase ASCII; matched case-insensitively.  Conservative
     * selection — includes attributes that are reliably
     * human-readable text.  Excludes {@code value} (often contains
     * base64 blobs or IDs) and {@code content} (on meta tags it's
     * a charset declaration, not user text).</p>
     */
    private static final byte[][] TEXT_ATTRS = {
            {'a', 'l', 't'},
            {'t', 'i', 't', 'l', 'e'},
            {'p', 'l', 'a', 'c', 'e', 'h', 'o', 'l', 'd', 'e', 'r'},
            {'a', 'r', 'i', 'a', '-', 'l', 'a', 'b', 'e', 'l'},
            {'s', 'u', 'm', 'm', 'a', 'r', 'y'},
            {'l', 'a', 'b', 'e', 'l'},
    };

    /**
     * Result of a strip operation: new content length and the number
     * of well-formed tags (including comments) successfully parsed.
     * Callers use {@code tagCount == 0} to detect "no markup present" —
     * a more encoding-agnostic signal than post-hoc byte-count
     * heuristics.
     */
    public static final class Result {
        /** Content byte count written into the destination. */
        public final int length;
        /** Number of well-formed tags parsed (including comments). */
        public final int tagCount;

        public Result(int length, int tagCount) {
            this.length = length;
            this.tagCount = tagCount;
        }
    }

    private HtmlByteStripper() {
    }

    /**
     * Strip HTML/XML tags, comments, and the bodies of {@code <script>} and
     * {@code <style>} elements from {@code src[srcOffset .. srcOffset+srcLen)}
     * into {@code dst} starting at {@code dstOffset}. Returns the number of
     * content bytes written into {@code dst}.
     *
     * <p>{@code dst.length - dstOffset} must be at least {@code srcLen}
     * (stripping never produces more output than input).
     *
     * <p>{@code src} and {@code dst} may refer to the same array; the source
     * and destination ranges may overlap if {@code dstOffset <= srcOffset}.
     * In that case the state machine's invariant (write index never leads
     * read index) guarantees every source byte is loaded into a local
     * before any write that could reach its position. Other overlap shapes
     * are not supported and must not be used.
     */
    public static Result strip(byte[] src, int srcOffset, int srcLen,
                     byte[] dst, int dstOffset) {
        int w = dstOffset;
        int state = TEXT;
        int nameStart = 0;
        byte[] rawEnd = null;
        int rawMatch = 0;
        int end = srcOffset + srcLen;
        int tagCount = 0;
        int attrNameStart = 0;
        // When true, the current quoted attribute value's bytes are
        // emitted to dst (attribute name matched TEXT_ATTRS).  Reset
        // to false when the quote closes or the tag ends.
        boolean emitAttrValue = false;

        for (int i = srcOffset; i < end; i++) {
            byte b = src[i];
            switch (state) {
                case TEXT:
                    if (b == '<') {
                        state = LT;
                    } else {
                        dst[w++] = b;
                    }
                    break;

                case LT:
                    if (b == '!' && i + 2 < end
                            && src[i + 1] == '-' && src[i + 2] == '-') {
                        state = COMMENT;
                        tagCount++;
                        i += 2;
                    } else if (b == '/' || isAsciiLetter(b)) {
                        state = TAG_NAME;
                        nameStart = i;
                        tagCount++;
                    } else {
                        // stray '<' — treat literally
                        dst[w++] = '<';
                        if (b != '<') {
                            dst[w++] = b;
                            state = TEXT;
                        }
                        // if b == '<', stay in LT
                    }
                    break;

                case TAG_NAME:
                    if (isTagNameTerminator(b)) {
                        int nameLen = i - nameStart;
                        if (matchesCI(src, nameStart, nameLen, SCRIPT)) {
                            rawEnd = END_SCRIPT;
                        } else if (matchesCI(src, nameStart, nameLen, STYLE)) {
                            rawEnd = END_STYLE;
                        } else {
                            rawEnd = null;
                        }
                        if (b == '>') {
                            if (rawEnd != null) {
                                state = RAW_BODY;
                                rawMatch = 0;
                            } else {
                                state = TEXT;
                            }
                        } else {
                            state = TAG;
                        }
                    }
                    break;

                case TAG:
                    if (b == '"') {
                        state = TAG_QUOTE_D;
                    } else if (b == '\'') {
                        state = TAG_QUOTE_S;
                    } else if (b == '>') {
                        if (rawEnd != null) {
                            state = RAW_BODY;
                            rawMatch = 0;
                        } else {
                            state = TEXT;
                        }
                    } else if (isAsciiLetter(b) || b == '-' || b == '_') {
                        // starts attribute name
                        state = ATTR_NAME;
                        attrNameStart = i;
                    }
                    break;

                case ATTR_NAME:
                    if (b == '=' || b == ' ' || b == '\t' || b == '\n'
                            || b == '\r' || b == '/' || b == '>') {
                        int nameLen = i - attrNameStart;
                        emitAttrValue = matchesAnyCI(src, attrNameStart, nameLen, TEXT_ATTRS);
                        if (b == '>') {
                            if (rawEnd != null) {
                                state = RAW_BODY;
                                rawMatch = 0;
                            } else {
                                state = TEXT;
                            }
                            emitAttrValue = false;
                        } else if (b == '=') {
                            state = ATTR_AFTER_EQUALS;
                        } else {
                            // whitespace or / — attr has no value.  Reset
                            // emit flag (no value coming) and go to TAG.
                            emitAttrValue = false;
                            state = TAG;
                        }
                    }
                    break;

                case ATTR_AFTER_EQUALS:
                    if (b == '"') {
                        if (emitAttrValue) {
                            dst[w++] = ' '; // word separator
                        }
                        state = TAG_QUOTE_D;
                    } else if (b == '\'') {
                        if (emitAttrValue) {
                            dst[w++] = ' ';
                        }
                        state = TAG_QUOTE_S;
                    } else if (b == '>') {
                        emitAttrValue = false;
                        state = (rawEnd != null) ? RAW_BODY : TEXT;
                        if (state == RAW_BODY) rawMatch = 0;
                    } else if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
                        // skip whitespace between = and value
                    } else {
                        // unquoted attribute value — skip until whitespace or >
                        emitAttrValue = false;
                        state = TAG;
                    }
                    break;

                case TAG_QUOTE_D:
                    if (b == '"') {
                        emitAttrValue = false;
                        state = TAG;
                    } else if (emitAttrValue) {
                        dst[w++] = b;
                    }
                    break;

                case TAG_QUOTE_S:
                    if (b == '\'') {
                        emitAttrValue = false;
                        state = TAG;
                    } else if (emitAttrValue) {
                        dst[w++] = b;
                    }
                    break;

                case COMMENT:
                    if (b == '>' && i >= srcOffset + 2
                            && src[i - 1] == '-' && src[i - 2] == '-') {
                        state = TEXT;
                    }
                    break;

                case RAW_BODY:
                    if (rawMatch < rawEnd.length) {
                        byte target = rawEnd[rawMatch];
                        byte actual = (target >= 'a' && target <= 'z')
                                ? (byte) (b | 0x20) : b;
                        if (actual == target) {
                            rawMatch++;
                        } else {
                            rawMatch = (b == '<') ? 1 : 0;
                        }
                    } else {
                        // matched "</script" or "</style"; require tag-name terminator
                        // to avoid false positives like </scripted>
                        if (isTagNameTerminator(b)) {
                            rawEnd = null;
                            rawMatch = 0;
                            state = (b == '>') ? TEXT : TAG;
                        } else {
                            rawMatch = (b == '<') ? 1 : 0;
                        }
                    }
                    break;

                default:
                    throw new IllegalStateException("unreachable state " + state);
            }
        }

        return new Result(w - dstOffset, tagCount);
    }

    /**
     * In-place stripping is intentionally not exposed.  If a caller
     * strips a buffer and the stripper produces {@code tagCount == 0}
     * (no markup found), there is no way to recover the original
     * bytes — any byte before position {@code length} may already
     * have been overwritten.  Callers that need the
     * backoff-to-original behavior must allocate a separate
     * destination buffer and pass both to {@link #strip}.</pre>
     */

    private static boolean isAsciiLetter(byte b) {
        int c = b & 0xFF;
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isTagNameTerminator(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '>' || b == '/';
    }

    /**
     * True when {@code buf[start .. start+len)} case-insensitively
     * matches any of the expected byte arrays.  Used for the
     * TEXT_ATTRS allowlist check.
     */
    private static boolean matchesAnyCI(byte[] buf, int start, int len, byte[][] candidates) {
        for (byte[] c : candidates) {
            if (matchesCI(buf, start, len, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Case-insensitive ASCII match of {@code expected} against
     * {@code buf[start .. start+len)}. Returns false if lengths differ.
     */
    private static boolean matchesCI(byte[] buf, int start, int len, byte[] expected) {
        if (len != expected.length) {
            return false;
        }
        for (int k = 0; k < len; k++) {
            int c = buf[start + k] & 0xFF;
            int lower = (c >= 'A' && c <= 'Z') ? c | 0x20 : c;
            if (lower != (expected[k] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
