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
package org.apache.tika.renderer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RenderSettingsTest {

    // US Letter in points
    private static final double W = 612, H = 792;

    @Test
    public void testDpiIsTheTargetWithoutABox() {
        RenderSettings s = RenderSettings.defaults();
        assertEquals(300f, s.effectiveDpi(W, H));
        assertEquals(2550L * 3300L, s.estimatedPixels(W, H));
    }

    @Test
    public void testBoxIsACeilingNeverAFloor() {
        RenderSettings s = RenderSettings.defaults();
        s.setMaxWidth(256);
        s.setMaxHeight(256);
        // 792 pt tall: 256 px is 23.27 dpi, and the page then fills the box exactly
        assertEquals(23.27f, s.effectiveDpi(W, H), 0.01f);
        assertEquals(256, Math.round(H / 72.0 * s.effectiveDpi(W, H)));
        assertTrue(s.estimatedPixels(W, H) <= 256L * 256L);
        // a box wider than the page at 300 dpi changes nothing
        s.setMaxWidth(10_000);
        s.setMaxHeight(10_000);
        assertEquals(300f, s.effectiveDpi(W, H));
    }

    @Test
    public void testOneSidedBoxBindsThatSideOnly() {
        RenderSettings s = RenderSettings.defaults();
        s.setMaxWidth(612);
        assertEquals(72f, s.effectiveDpi(W, H));
        assertEquals(612L * 792L, s.estimatedPixels(W, H));
    }

    @Test
    public void testMinimumIsCheckedAtTheTargetDpi() {
        RenderSettings s = RenderSettings.defaults();
        // a hairline rule: 600 x 0.5 pt is 2500 x 2.08 px at 300 dpi, rounded up to 3
        assertFalse(s.belowMinimum(600, 0.5));
        s.setMinHeight(4);
        assertTrue(s.belowMinimum(600, 0.5));
        // the ceiling does not make a page "small": still judged at 300 dpi
        s.setMaxWidth(8);
        s.setMaxHeight(8);
        assertFalse(s.belowMinimum(W, H));
        assertTrue(s.belowMinimumPixels(1, 100));
        assertFalse(s.belowMinimumPixels(2, 4));
    }

    @Test
    public void testFitScalesBitmapsDownOnly() {
        RenderSettings s = RenderSettings.defaults();
        assertArrayEquals(new long[] {4000, 3000}, s.fit(4000, 3000));
        s.setMaxWidth(400);
        s.setMaxHeight(400);
        assertArrayEquals(new long[] {400, 300}, s.fit(4000, 3000));
        assertArrayEquals(new long[] {300, 400}, s.fit(3000, 4000));
        assertArrayEquals(new long[] {40, 30}, s.fit(40, 30));
    }

    @Test
    public void testOverAppliesSetFieldsOnly() {
        RenderSettings overlay = new RenderSettings();
        overlay.setDpi(96);
        overlay.setImageType(ImageType.RGB);
        RenderSettings s = RenderSettings.defaults().over(overlay);
        assertEquals(96, s.getDpi());
        assertEquals(ImageType.RGB, s.getImageType());
        assertEquals(ImageFormat.PNG, s.getImageFormat());
        assertEquals(0.5f, s.getImageQuality());
        assertEquals(2, s.getMinWidth());
        // the overlay is untouched and still sparse
        assertNull(overlay.getImageFormat());
        assertEquals(RenderSettings.defaults().getDpi(), RenderSettings.defaults().over(null).getDpi());
    }

    @Test
    public void testSameImageIgnoresGates() {
        RenderSettings a = RenderSettings.defaults();
        RenderSettings b = RenderSettings.defaults();
        b.setMaxImagePixels(5L);
        b.setMinWidth(100);
        assertTrue(a.rendersSameImageAs(b));
        b.setMaxWidth(256);
        assertFalse(a.rendersSameImageAs(b));
    }

    @Test
    public void testValidation() {
        RenderSettings s = new RenderSettings();
        assertThrows(IllegalArgumentException.class, () -> s.setDpi(0));
        assertThrows(IllegalArgumentException.class, () -> s.setImageQuality(1.5f));
        assertThrows(IllegalArgumentException.class, () -> s.setMaxImagePixels(0L));
        assertThrows(IllegalArgumentException.class, () -> s.setMaxWidth(0));
        assertThrows(IllegalArgumentException.class, () -> s.setMaxHeight(-2));
        assertThrows(IllegalArgumentException.class, () -> s.setMinWidth(-1));
        s.setMaxWidth(-1);
        s.setMinWidth(0);
        assertEquals(-1, s.getMaxWidth());
    }
}
