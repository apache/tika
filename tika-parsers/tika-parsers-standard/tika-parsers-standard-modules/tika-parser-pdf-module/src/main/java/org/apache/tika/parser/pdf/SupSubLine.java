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
package org.apache.tika.parser.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.pdfbox.text.TextPosition;

/**
 * One line of text, against whose body size and baseline a glyph is guessed to be sup or sub
 * ({@link PDFParserConfig#isDetectSuperscriptSubscript()}).
 */
final class SupSubLine {

    // smaller than this fraction of the line's body size, and off its baseline by more than
    // MIN_SHIFT of it, is sup or sub; Word lowers a subscript by only ~9% of the font size
    private static final float SMALLER = 0.9f;
    private static final float MIN_SHIFT = 0.05f;
    // a longer run is far more often a table label or a line of small print than a superscript
    private static final int MAX_RUN_CHARS = 3;

    /** Part of a word and its vertTag: sup, sub or null (a blank chunk always null). */
    record Chunk(String text, String vertTag) {
    }

    private final float bodySize;
    private final float baseline;
    // glyphs of a sup or sub run longer than MAX_RUN_CHARS, left untagged
    private final Set<TextPosition> tooLong = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Body size is the most common font size on the line; baseline the most common baseline
     * of glyphs in that size. A run of sup or sub longer than {@value #MAX_RUN_CHARS} characters,
     * spaces not counted, is not tagged.
     */
    SupSubLine(List<List<TextPosition>> words) {
        Map<Float, Integer> sizes = new HashMap<>();
        for (List<TextPosition> word : words) {
            for (TextPosition position : word) {
                if (!isBlank(position)) {
                    sizes.merge(round(size(position)), 1, Integer::sum);
                }
            }
        }
        bodySize = mostCommon(sizes);
        Map<Float, Integer> baselines = new HashMap<>();
        for (List<TextPosition> word : words) {
            for (TextPosition position : word) {
                if (!isBlank(position) && round(size(position)) == bodySize) {
                    baselines.merge(round(position.getYDirAdj()), 1, Integer::sum);
                }
            }
        }
        baseline = mostCommon(baselines);
        List<TextPosition> run = new ArrayList<>();
        String runVertTag = null;
        for (List<TextPosition> word : words) {
            for (TextPosition position : word) {
                if (isBlank(position)) {
                    continue;
                }
                String t = shift(position);
                if (!Objects.equals(t, runVertTag)) {
                    markIfTooLong(run);
                    runVertTag = t;
                }
                if (t != null) {
                    run.add(position);
                }
            }
        }
        markIfTooLong(run);
    }

    private void markIfTooLong(List<TextPosition> run) {
        if (rawText(run).length() > MAX_RUN_CHARS) {
            tooLong.addAll(run);
        }
        run.clear();
    }

    /** The word cut where its vertTag changes. */
    List<Chunk> chunks(String text, List<TextPosition> positions) {
        List<Chunk> chunks = new ArrayList<>();
        if (!text.equals(rawText(positions))) {
            // bidi or normalization changed the text, so its glyphs no longer map onto it:
            // the whole word takes its glyphs' vertTag only if they all have the same one
            String vertTag = null;
            boolean first = true;
            for (TextPosition position : positions) {
                if (!isBlank(position)) {
                    String t = vertTag(position);
                    if (!first && !Objects.equals(t, vertTag)) {
                        vertTag = null;
                        break;
                    }
                    vertTag = t;
                    first = false;
                }
            }
            chunks.add(new Chunk(text, vertTag));
            return chunks;
        }
        StringBuilder sb = new StringBuilder();
        String vertTag = null;
        for (TextPosition position : positions) {
            String u = position.getUnicode();
            if (u == null) {
                continue;
            }
            String t = u.isBlank() ? null : vertTag(position);
            if (sb.length() > 0 && !Objects.equals(t, vertTag)) {
                chunks.add(new Chunk(sb.toString(), vertTag));
                sb.setLength(0);
            }
            sb.append(u);
            vertTag = t;
        }
        if (sb.length() > 0) {
            chunks.add(new Chunk(sb.toString(), vertTag));
        }
        return chunks;
    }

    private String vertTag(TextPosition position) {
        return tooLong.contains(position) ? null : shift(position);
    }

    /** sup or sub by size and baseline alone. */
    private String shift(TextPosition position) {
        if (!(size(position) < bodySize * SMALLER)) {
            return null;
        }
        // y grows down the page
        float rise = baseline - position.getYDirAdj();
        if (rise > bodySize * MIN_SHIFT) {
            return "sup";
        }
        if (rise < -bodySize * MIN_SHIFT) {
            return "sub";
        }
        return null;
    }

    // font size in user space; not getHeightDir, which is the font's cap height or bbox, so a
    // glyph at the body size in a font with shorter capitals would look smaller; abs, since
    // text turned 180 degrees has a negative scale
    private static float size(TextPosition position) {
        return Math.abs(position.getYScale());
    }

    private static boolean isBlank(TextPosition position) {
        return position.getUnicode() == null || position.getUnicode().isBlank();
    }

    static String rawText(List<TextPosition> positions) {
        StringBuilder sb = new StringBuilder();
        for (TextPosition position : positions) {
            if (position.getUnicode() != null) {
                sb.append(position.getUnicode());
            }
        }
        return sb.toString();
    }

    private static float round(float f) {
        return Math.round(f * 10) / 10f;
    }

    /** The key counted most often, the larger on a tie; NaN when there is none. */
    private static float mostCommon(Map<Float, Integer> counts) {
        float best = Float.NaN;
        int bestCount = 0;
        for (Map.Entry<Float, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() > best)) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
