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

import java.io.IOException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;
import org.apache.tika.parser.enricher.ImageProbe;

/**
 * Refuses a blank image: the share of pixels whose gray level differs from the image's most
 * common gray level (the background) by more than {@code tolerance} is at most {@code maxInk}.
 * Measured on a copy of at most 1,000 pixels a side. The default {@code maxInk}, 0.0001
 * (0.01 %), is below a page with a few lines of text: on 13,641 PDF pages it flagged the 87 with
 * no ink at all and 3 with a single line. With {@code despeckle} (the default) a pixel counts as
 * ink only if most of its 3 x 3 neighbourhood does, so scattered specks (a noisy scan) do not
 * hide a blank page: salt-and-pepper noise of 0.1 % to 5 % comes out blank, and none of 600 text
 * pages did. An image with transparency is blank only if it is blank over white and over black.
 * An image it cannot read or decode is admitted.
 *
 * @since Apache Tika 4.2
 */
@TikaComponent(name = "blank-image-check")
public class BlankImageCheck implements ImageCheck {

    private static final long serialVersionUID = 1L;

    private double maxInk = 0.0001;
    private int tolerance = 40;
    private boolean despeckle = true;

    @Override
    public Verdict check(ImageProbe image, Metadata metadata, ParseContext context)
            throws IOException {
        ImageDecoding.Gray gray = ImageDecoding.gray(image);
        if (gray == null || gray.levels().length == 0) {
            return Verdict.admit();
        }
        boolean blank = ink(gray.levels(), gray.width(), gray.height()) <= maxInk
                && (gray.overBlack() == null
                || ink(gray.overBlack(), gray.width(), gray.height()) <= maxInk);
        return blank ? Verdict.skip("blank") : Verdict.admit();
    }

    /** The share of pixels that differ from the background, after the specks are removed. */
    private double ink(byte[] levels, int width, int height) {
        int[] histogram = new int[256];
        for (byte level : levels) {
            histogram[level & 255]++;
        }
        int mode = 0;
        for (int i = 1; i < 256; i++) {
            if (histogram[i] > histogram[mode]) {
                mode = i;
            }
        }
        byte[] mask = new byte[levels.length];
        for (int i = 0; i < levels.length; i++) {
            mask[i] = (byte) (Math.abs((levels[i] & 255) - mode) > tolerance ? 1 : 0);
        }
        long ink = 0;
        if (!despeckle) {
            for (byte m : mask) {
                ink += m;
            }
        } else {
            // a 3 x 3 median of a binary mask is a majority vote: isolated specks, as on a noisy
            // scan, are outvoted by the background, a stroke of text is not
            int[] horizontal = new int[levels.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = y * width + x;
                    horizontal[i] = mask[i] + (x > 0 ? mask[i - 1] : 0)
                            + (x < width - 1 ? mask[i + 1] : 0);
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = y * width + x;
                    int votes = horizontal[i] + (y > 0 ? horizontal[i - width] : 0)
                            + (y < height - 1 ? horizontal[i + width] : 0);
                    if (votes >= 5) {
                        ink++;
                    }
                }
            }
        }
        return (double) ink / levels.length;
    }

    public boolean isDespeckle() {
        return despeckle;
    }

    public void setDespeckle(boolean despeckle) {
        this.despeckle = despeckle;
    }

    public double getMaxInk() {
        return maxInk;
    }

    public void setMaxInk(double maxInk) {
        if (!(maxInk >= 0 && maxInk < 1)) {
            throw new IllegalArgumentException("maxInk must be at least 0 and below 1, got: "
                    + maxInk);
        }
        this.maxInk = maxInk;
    }

    public int getTolerance() {
        return tolerance;
    }

    public void setTolerance(int tolerance) {
        if (tolerance < 0 || tolerance > 255) {
            throw new IllegalArgumentException("tolerance must be 0 to 255, got: " + tolerance);
        }
        this.tolerance = tolerance;
    }
}
