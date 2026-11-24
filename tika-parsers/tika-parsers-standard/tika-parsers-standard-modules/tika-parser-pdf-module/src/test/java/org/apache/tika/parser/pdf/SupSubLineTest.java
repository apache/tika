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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

public class SupSubLineTest {

    //TIKA-5073
    @Test
    public void testRewrittenWordWithMixedGlyphsIsNotTagged() {
        // "¹ﬁeld": PDFBox writes the ligature as "fi", so the text no longer maps onto the glyphs
        List<TextPosition> body = word("Article", 12, 0);
        List<TextPosition> mixed = new ArrayList<>(word("1", 7, 4));
        mixed.addAll(word("ﬁeld", 12, 0));
        SupSubLine line = new SupSubLine(List.of(body, mixed));
        assertEquals(List.of(new SupSubLine.Chunk("1field", null)), line.chunks("1field", mixed));
    }

    //TIKA-5073
    @Test
    public void testRewrittenWordWithOneTagKeepsIt() {
        List<TextPosition> body = word("Article", 12, 0);
        List<TextPosition> raised = word("2ﬁ", 7, 4);
        SupSubLine line = new SupSubLine(List.of(body, raised));
        assertEquals(List.of(new SupSubLine.Chunk("2fi", "sup")), line.chunks("2fi", raised));
    }

    //TIKA-5073
    @Test
    public void testSameSizeInAnotherFontIsNotTagged() {
        // Courier's cap height is ~20% below Helvetica's: same 12pt, slightly off the baseline
        List<TextPosition> body = word("Article", 12, 8.6f, 0);
        List<TextPosition> code = word("code", 12, 6.9f, 0.9f);
        SupSubLine line = new SupSubLine(List.of(body, code));
        assertEquals(List.of(new SupSubLine.Chunk("code", null)), line.chunks("code", code));
    }

    private static List<TextPosition> word(String text, float size, float rise) {
        return word(text, size, size, rise);
    }

    /**
     * One glyph per char in a font of the given size whose glyphs are {@code height} tall,
     * raised by {@code rise} from a baseline at y=100.
     */
    private static List<TextPosition> word(String text, float size, float height, float rise) {
        List<TextPosition> positions = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            Matrix m = new Matrix(size, 0, 0, size, i * size, 100 + rise);
            positions.add(new TextPosition(0, 600, 800, m, (i + 1) * size, 100 + rise, height,
                    size, size / 4, text.substring(i, i + 1), new int[]{text.charAt(i)}, null,
                    size, (int) size));
        }
        return positions;
    }
}
