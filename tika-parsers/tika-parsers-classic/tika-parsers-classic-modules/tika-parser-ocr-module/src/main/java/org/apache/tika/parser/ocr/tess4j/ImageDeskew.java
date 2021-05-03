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

/**
 * Copied and pasted from Tess4j (https://sourceforge.net/projects/tess4j/)
 */
package org.apache.tika.parser.ocr.tess4j;

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageDeskew {
    private static final Logger LOG = LoggerFactory.getLogger(ImageDeskew.class);

    private final BufferedImage cImage;
    private final int cSteps = 200;
    private double[] cSinA;
    private double[] cCosA;
    private double cDMin;
    private int[] cHMatrix;

    public ImageDeskew(BufferedImage var1) {
        this.cImage = var1;
    }

    public double getSkewAngle() {
        double var2 = 0.0D;
        int var4 = 0;
        this.calc();
        HoughLine[] var1 = this.getTop(20);
        if (var1.length < 20) {
            return 0.0D;
        } else {
            for (int var5 = 0; var5 < 19; ++var5) {
                var2 += var1[var5].alpha;
                ++var4;
            }

            return var2 / (double) var4;
        }
    }

    private HoughLine[] getTop(int var1) {
        HoughLine[] var2 = new HoughLine[var1];

        for (int var3 = 0; var3 < var1; ++var3) {
            var2[var3] = new HoughLine();
        }

        int var4;
        int var5;
        for (var4 = 0; var4 < this.cHMatrix.length - 1; ++var4) {
            if (this.cHMatrix[var4] > var2[var1 - 1].count) {
                var2[var1 - 1].count = this.cHMatrix[var4];
                var2[var1 - 1].index = var4;

                for (var5 = var1 - 1; var5 > 0 && var2[var5].count > var2[var5 - 1].count; --var5) {
                    HoughLine var7 = var2[var5];
                    var2[var5] = var2[var5 - 1];
                    var2[var5 - 1] = var7;
                }
            }
        }

        for (int var6 = 0; var6 < var1; ++var6) {
            var5 = var2[var6].index / this.cSteps;
            var4 = var2[var6].index - var5 * this.cSteps;
            var2[var6].alpha = this.getAlpha(var4);
            var2[var6].d = (double) var5 + this.cDMin;
        }

        return var2;
    }

    private void calc() {
        int var1 = (int) ((double) this.cImage.getHeight() / 4.0D);
        int var2 = (int) ((double) this.cImage.getHeight() * 3.0D / 4.0D);
        this.init();

        for (int var3 = var1; var3 < var2; ++var3) {
            for (int var4 = 1; var4 < this.cImage.getWidth() - 2; ++var4) {
                if (ImageUtil.isBlack(this.cImage, var4, var3) &&
                        !ImageUtil.isBlack(this.cImage, var4, var3 + 1)) {
                    this.calc(var4, var3);
                }
            }
        }

    }

    private void calc(int var1, int var2) {
        for (int var7 = 0; var7 < this.cSteps - 1; ++var7) {
            double var3 = (double) var2 * this.cCosA[var7] - (double) var1 * this.cSinA[var7];
            int var5 = (int) (var3 - this.cDMin);
            int var6 = var5 * this.cSteps + var7;

            try {
                this.cHMatrix[var6]++;
            } catch (Exception var9) {
                LOG.warn("", var9);
            }

        }

    }

    private void init() {
        this.cSinA = new double[this.cSteps - 1];
        this.cCosA = new double[this.cSteps - 1];

        for (int var3 = 0; var3 < this.cSteps - 1; ++var3) {
            double var1 = this.getAlpha(var3) * 3.141592653589793D / 180.0D;
            this.cSinA[var3] = Math.sin(var1);
            this.cCosA[var3] = Math.cos(var1);
        }

        this.cDMin = -this.cImage.getWidth();
        final double cDStep = 1.0D;
        final int cDCount =
                (int) (2.0D * (double) (this.cImage.getWidth() + this.cImage.getHeight()) / cDStep);
        this.cHMatrix = new int[cDCount * this.cSteps];
    }

    public double getAlpha(int var1) {
        final double cAlphaStart = -20.0D;
        final double cAlphaStep = 0.2D;
        return cAlphaStart + (double) var1 * cAlphaStep;
    }

    public static class HoughLine {
        public int count = 0;
        public int index = 0;
        public double alpha;
        public double d;

        public HoughLine() {
        }
    }
}
