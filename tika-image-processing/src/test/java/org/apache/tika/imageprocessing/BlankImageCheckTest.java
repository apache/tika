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
package org.apache.tika.imageprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;

public class BlankImageCheckTest {

    private static ImageCheck.Verdict check(BlankImageCheck check, byte[] image) throws Exception {
        return check.check(ImageFixtures.probe(image), new Metadata(), new ParseContext());
    }

    @Test
    public void testAWhiteImageIsBlank() throws Exception {
        ImageCheck.Verdict v = check(new BlankImageCheck(), ImageFixtures.png(300, 400, 0));
        assertEquals(false, v.admitted());
        assertEquals("blank", v.reason());
    }

    @Test
    public void testAFlatGreyScanIsBlank() throws Exception {
        // the background is whatever is most common, not white
        assertEquals(false, check(new BlankImageCheck(),
                ImageFixtures.png(300, 400, 0, new Color(170, 170, 170))).admitted());
    }

    @Test
    public void testAnImageWithInkIsAdmitted() throws Exception {
        assertTrue(check(new BlankImageCheck(), ImageFixtures.png(300, 400, 0.1)).admitted());
    }

    @Test
    public void testInkBelowTheThresholdIsBlankAndAboveIsNot() throws Exception {
        // 3 columns of 300 are ink: 1 %
        byte[] image = ImageFixtures.png(300, 400, 0.01);
        BlankImageCheck strict = new BlankImageCheck();
        assertTrue(check(strict, image).admitted());
        BlankImageCheck loose = new BlankImageCheck();
        loose.setMaxInk(0.02);
        assertEquals(false, check(loose, image).admitted());
    }

    @Test
    public void testToleranceDecidesWhatCountsAsInk() throws Exception {
        // light grey (200) on white is within 60 of the background
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(200, 200,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(new Color(200, 200, 200));
        g.fillRect(0, 0, 60, 200);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        BlankImageCheck lenient = new BlankImageCheck();
        lenient.setTolerance(60);
        assertEquals(false, check(lenient, out.toByteArray()).admitted());
        assertTrue(check(new BlankImageCheck(), out.toByteArray()).admitted());
    }

    @Test
    public void testSomethingThatIsNotAnImageIsAdmitted() throws Exception {
        assertTrue(check(new BlankImageCheck(), new byte[] {1, 2, 3, 4}).admitted());
    }

    @Test
    public void testALargeImageIsSubsampledNotRefused() throws Exception {
        // 3000 x 3000: read at one third; the block of ink is still found
        assertTrue(check(new BlankImageCheck(), ImageFixtures.png(3000, 3000, 0.2)).admitted());
        assertEquals(false, check(new BlankImageCheck(), ImageFixtures.png(3000, 3000, 0))
                .admitted());
    }

    private static byte[] speckled(int width, int height, double density) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (random.nextDouble() < density) {
                    image.setRGB(x, y, 0);
                }
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    public void testScatteredSpecksAreBlank() throws Exception {
        for (double density : new double[] {0.001, 0.01, 0.05}) {
            assertEquals(false, check(new BlankImageCheck(), speckled(800, 1000, density))
                    .admitted(), "salt-and-pepper noise of " + density);
        }
    }

    @Test
    public void testDespeckleCanBeTurnedOff() throws Exception {
        BlankImageCheck raw = new BlankImageCheck();
        raw.setDespeckle(false);
        assertTrue(check(raw, speckled(800, 1000, 0.01)).admitted());
    }

    @Test
    public void testTextIsNotMistakenForSpecks() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(800, 400,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 400);
        g.setColor(Color.BLACK);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        for (int line = 0; line < 20; line++) {
            g.drawString("The quick brown fox jumps over the lazy dog, again and again.", 10,
                    20 + line * 18);
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        assertTrue(check(new BlankImageCheck(), out.toByteArray()).admitted());
    }

    @Test
    public void testBadSettingsAreRefused() {
        BlankImageCheck check = new BlankImageCheck();
        assertThrows(IllegalArgumentException.class, () -> check.setMaxInk(-0.1));
        assertThrows(IllegalArgumentException.class, () -> check.setMaxInk(1));
        assertThrows(IllegalArgumentException.class, () -> check.setTolerance(256));
    }

    @Test
    public void testDarkTextOnATransparentBackgroundIsAdmitted() throws Exception {
        // transparent pixels stored as RGB 0, the same as the text
        byte[] image = ImageFixtures.encode(ImageFixtures.image(BufferedImage.TYPE_INT_ARGB,
                300, 400, 0x00000000, 0xFF000000, 0.1), "png");
        assertTrue(check(new BlankImageCheck(), image).admitted());
    }

    @Test
    public void testLightTextOnATransparentBackgroundIsAdmitted() throws Exception {
        byte[] image = ImageFixtures.encode(ImageFixtures.image(BufferedImage.TYPE_INT_ARGB,
                300, 400, 0x00FFFFFF, 0xFFFFFFFF, 0.1), "png");
        assertTrue(check(new BlankImageCheck(), image).admitted());
    }

    @Test
    public void testIndexedTextOnATransparentBackgroundIsAdmitted() throws Exception {
        IndexColorModel palette = new IndexColorModel(8, 2, new byte[] {0, 0},
                new byte[] {0, 0}, new byte[] {0, 0}, new byte[] {0, (byte) 255});
        BufferedImage indexed = new BufferedImage(300, 400, BufferedImage.TYPE_BYTE_INDEXED,
                palette);
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 30; x++) {
                indexed.getRaster().setSample(x, y, 0, 1);
            }
        }
        assertTrue(check(new BlankImageCheck(), ImageFixtures.encode(indexed, "png")).admitted());
    }

    @Test
    public void testAFullyTransparentImageIsBlank() throws Exception {
        byte[] image = ImageFixtures.encode(ImageFixtures.image(BufferedImage.TYPE_INT_ARGB,
                300, 400, 0x00000000, 0x00000000, 0), "png");
        assertEquals(false, check(new BlankImageCheck(), image).admitted());
    }
}
