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
package org.apache.tika.parser.vlm;

import java.util.ArrayList;
import java.util.List;

/**
 * Shields TeX math in model output from the Markdown parser.
 * <p>
 * CommonMark has no math syntax: inside {@code $\|x\|$} it reads {@code \|} as an
 * escaped pipe and drops the backslash, and a GFM table splits cells on the pipes.
 * {@link #protect} swaps each math span for a placeholder before parsing;
 * {@link #restore} and {@link #split} put the original text back afterwards.
 * <p>
 * Delimiters: {@code $...$}, {@code $$...$$}, {@code \(...\)}, {@code \[...\]}.
 * {@code $...$} follows pandoc's guards so prices are not math: the opening
 * {@code $} must be followed by a non-space, the closing one preceded by a non-space
 * and not followed by a digit. No span crosses a blank line or a backtick, and code
 * spans and fenced code blocks are left alone.
 */
final class MathSpans {

    static final char OPEN = '';
    static final char CLOSE = '';

    /** One piece of text after {@link #split}: plain text, or a math span with delimiters. */
    static final class Piece {
        final String text;
        final boolean math;
        final boolean display;

        Piece(String text, boolean math, boolean display) {
            this.text = text;
            this.math = math;
            this.display = display;
        }
    }

    private final String text;
    private final List<String> spans;

    private MathSpans(String text, List<String> spans) {
        this.text = text;
        this.spans = spans;
    }

    /** The input with each math span replaced by a placeholder. */
    String text() {
        return text;
    }

    static MathSpans protect(String s) {
        List<String> spans = new ArrayList<>();
        if (s.indexOf(OPEN) >= 0 || s.indexOf(CLOSE) >= 0
                || (s.indexOf('$') < 0 && s.indexOf('\\') < 0)) {
            return new MathSpans(s, spans);
        }
        StringBuilder out = new StringBuilder(s.length());
        // a failed search for a closer stops at a boundary; openers before it fail too
        int[] failedUntil = new int[4];
        int n = s.length();
        int i = 0;
        while (i < n) {
            if (i == 0 || s.charAt(i - 1) == '\n') {
                int fenceEnd = skipFencedBlock(s, i);
                if (fenceEnd > i) {
                    out.append(s, i, fenceEnd);
                    i = fenceEnd;
                    continue;
                }
            }
            char c = s.charAt(i);
            int end = -1;
            if (c == '`') {
                int skip = skipCodeSpan(s, i);
                out.append(s, i, skip);
                i = skip;
                continue;
            } else if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                if (next == '(' || next == '[') {
                    int kind = next == '(' ? 0 : 1;
                    if (i >= failedUntil[kind]) {
                        end = findCloser(s, i + 2, next == '(' ? ')' : ']', kind, failedUntil);
                    }
                }
                if (end < 0) {
                    out.append(s, i, i + 2);
                    i += 2;
                    continue;
                }
            } else if (c == '$') {
                if (i + 1 < n && s.charAt(i + 1) == '$') {
                    if (i >= failedUntil[2]) {
                        end = findCloser(s, i + 2, '$', 2, failedUntil);
                    }
                    if (end < 0) {
                        out.append("$$");
                        i += 2;
                        continue;
                    }
                } else if (i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))
                        && i >= failedUntil[3]) {
                    end = findCloser(s, i + 1, '$', 3, failedUntil);
                }
            }
            if (end < 0) {
                out.append(c);
                i++;
                continue;
            }
            out.append(OPEN).append(spans.size()).append(CLOSE);
            spans.add(s.substring(i, end));
            i = end;
        }
        return new MathSpans(out.toString(), spans);
    }

    /**
     * Finds the end (exclusive) of a math span whose content starts at {@code from}.
     * kind: 0 {@code \)}, 1 {@code \]}, 2 {@code $$}, 3 {@code $}. Returns -1, and
     * records the boundary in {@code failedUntil}, when there is none.
     */
    private static int findCloser(String s, int from, char close, int kind, int[] failedUntil) {
        int n = s.length();
        int i = from;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '`' || (c == '\n' && isBlankLineAhead(s, i + 1))) {
                break;
            }
            if (c == '\\') {
                if (kind <= 1 && i + 1 < n && s.charAt(i + 1) == close) {
                    return i > from ? i + 2 : -1;
                }
                i += 2;
                continue;
            }
            if (c == '$') {
                if (kind == 2) {
                    if (i + 1 < n && s.charAt(i + 1) == '$' && i > from) {
                        return i + 2;
                    }
                } else if (kind == 3) {
                    boolean afterNonSpace = !Character.isWhitespace(s.charAt(i - 1));
                    boolean digitNext = i + 1 < n && Character.isDigit(s.charAt(i + 1));
                    if (afterNonSpace && !digitNext) {
                        return i + 1;
                    }
                }
            }
            i++;
        }
        failedUntil[kind] = i;
        return -1;
    }

    private static boolean isBlankLineAhead(String s, int i) {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i >= s.length() || s.charAt(i) == '\n';
    }

    /** End of a backtick code span starting at i, or the end of the backtick run. */
    private static int skipCodeSpan(String s, int i) {
        int run = runLength(s, i, '`');
        int j = i + run;
        while (j < s.length()) {
            int k = s.indexOf('`', j);
            if (k < 0) {
                break;
            }
            int len = runLength(s, k, '`');
            if (len == run) {
                return k + len;
            }
            j = k + len;
        }
        return i + run;
    }

    /** End of a fenced code block opening at line start i, or i if there is none. */
    private static int skipFencedBlock(String s, int i) {
        int j = i;
        while (j < s.length() && j - i < 3 && s.charAt(j) == ' ') {
            j++;
        }
        if (j >= s.length() || (s.charAt(j) != '`' && s.charAt(j) != '~')) {
            return i;
        }
        char fence = s.charAt(j);
        int len = runLength(s, j, fence);
        if (len < 3) {
            return i;
        }
        int line = s.indexOf('\n', j);
        while (line >= 0) {
            int k = line + 1;
            while (k < s.length() && k - line - 1 < 3 && s.charAt(k) == ' ') {
                k++;
            }
            if (runLength(s, k, fence) >= len) {
                int eol = s.indexOf('\n', k);
                return eol < 0 ? s.length() : eol + 1;
            }
            line = s.indexOf('\n', k);
        }
        return s.length();
    }

    private static int runLength(String s, int i, char c) {
        int j = i;
        while (j < s.length() && s.charAt(j) == c) {
            j++;
        }
        return j - i;
    }

    /** Replaces placeholders in a literal with the original math text. */
    String restore(String s) {
        if (s == null || spans.isEmpty() || s.indexOf(OPEN) < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (Piece p : split(s)) {
            sb.append(p.text);
        }
        return sb.toString();
    }

    /** Splits text at placeholders into plain and math pieces. */
    List<Piece> split(String s) {
        List<Piece> pieces = new ArrayList<>();
        int start = 0;
        int i = spans.isEmpty() ? -1 : s.indexOf(OPEN);
        while (i >= 0) {
            int close = s.indexOf(CLOSE, i);
            int index = close < 0 ? -1 : parseIndex(s, i + 1, close);
            if (index < 0) {
                i = s.indexOf(OPEN, i + 1);
                continue;
            }
            if (i > start) {
                pieces.add(new Piece(s.substring(start, i), false, false));
            }
            String math = spans.get(index);
            pieces.add(new Piece(math, true, math.startsWith("$$") || math.startsWith("\\[")));
            start = close + 1;
            i = s.indexOf(OPEN, start);
        }
        if (start < s.length()) {
            pieces.add(new Piece(s.substring(start), false, false));
        }
        return pieces;
    }

    private int parseIndex(String s, int from, int to) {
        if (to <= from || to - from > 9) {
            return -1;
        }
        int v = 0;
        for (int k = from; k < to; k++) {
            char c = s.charAt(k);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v < spans.size() ? v : -1;
    }
}
