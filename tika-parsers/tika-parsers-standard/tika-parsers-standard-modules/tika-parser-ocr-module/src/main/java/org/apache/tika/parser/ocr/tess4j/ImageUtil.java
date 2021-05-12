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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ImageUtil.class);

    public ImageUtil() {
    }

    public static boolean isBlack(BufferedImage var0, int var1, int var2) {
        if (var0.getType() == 12) {
            WritableRaster var5 = var0.getRaster();
            int var4 = var5.getSample(var1, var2, 0);
            return var4 == 0;
        } else {
            short var3 = 140;
            return isBlack(var0, var1, var2, var3);
        }
    }

    public static boolean isBlack(BufferedImage var0, int var1, int var2, int var3) {
        double var8 = 0.0D;
        if (var1 >= 0 && var2 >= 0 && var1 <= var0.getWidth() && var2 <= var0.getHeight()) {
            try {
                int var4 = var0.getRGB(var1, var2);
                int var5 = var4 >> 16 & 255;
                int var6 = var4 >> 8 & 255;
                int var7 = var4 & 255;
                var8 = (double) var5 * 0.299D + (double) var6 * 0.587D + (double) var7 * 0.114D;
            } catch (Exception var11) {
                LOG.warn("", var11);
            }

            return var8 < (double) var3;
        } else {
            return false;
        }
    }

    public static BufferedImage rotate(BufferedImage var0, double var1, int var3, int var4) {
        int var5 = var0.getWidth(null);
        int var6 = var0.getHeight(null);
        int var10 = 0;
        int var9 = 0;
        int var8 = 0;
        int var7 = 0;
        int[] var11 = new int[]{0, 0, var5, 0, var5, var6, 0, var6};
        double var12 = Math.toRadians(var1);

        for (int var14 = 0; var14 < var11.length; var14 += 2) {
            int var15 = (int) (Math.cos(var12) * (double) (var11[var14] - var3) -
                    Math.sin(var12) * (double) (var11[var14 + 1] - var4) + (double) var3);
            int var16 = (int) (Math.sin(var12) * (double) (var11[var14] - var3) +
                    Math.cos(var12) * (double) (var11[var14 + 1] - var4) + (double) var4);
            if (var15 > var9) {
                var9 = var15;
            }

            if (var15 < var7) {
                var7 = var15;
            }

            if (var16 > var10) {
                var10 = var16;
            }

            if (var16 < var8) {
                var8 = var16;
            }
        }

        var3 -= var7;
        var4 -= var8;
        BufferedImage var17 = new BufferedImage(var9 - var7, var10 - var8, var0.getType());
        Graphics2D var18 = var17.createGraphics();
        var18.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        var18.setBackground(Color.white);
        var18.fillRect(0, 0, var17.getWidth(), var17.getHeight());
        AffineTransform var19 = new AffineTransform();
        var19.rotate(var12, var3, var4);
        var18.setTransform(var19);
        var18.drawImage(var0, -var7, -var8, null);
        var18.dispose();
        return var17;
    }
}
