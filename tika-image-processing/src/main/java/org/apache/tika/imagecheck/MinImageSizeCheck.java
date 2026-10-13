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
package org.apache.tika.imagecheck;

import java.io.IOException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;
import org.apache.tika.parser.enricher.ImageProbe;

/**
 * Refuses an image smaller than {@code minTotalPixels} in area, or whose shorter side is under
 * {@code minShortSidePixels}, by the dimensions in its header (not the recorded metadata, which
 * not every image has). A strip of 11 x 1000 passes the area test but holds no line of text,
 * hence the side minimum. {@code minWidthPixels} and {@code minHeightPixels} ask for more of one
 * dimension than {@code minShortSidePixels} does (a wide banner may need only a short height);
 * each applies on top of it. A setting of 0 turns that test off; an image of unknown size is
 * admitted.
 *
 * @since Apache Tika 4.2
 */
@TikaComponent(name = "min-image-size")
public class MinImageSizeCheck implements ImageCheck {

    private static final long serialVersionUID = 1L;

    private long minTotalPixels = 10_000;
    private int minShortSidePixels = 16;
    private int minWidthPixels;
    private int minHeightPixels;

    @Override
    public Verdict check(ImageProbe image, Metadata metadata, ParseContext context)
            throws IOException {
        long[] size = ImageDecoding.size(image);
        if (size[0] <= 0 || size[1] <= 0) {
            return Verdict.admit();
        }
        boolean tooSmall = size[0] * size[1] < minTotalPixels
                || size[0] < Math.max(minShortSidePixels, minWidthPixels)
                || size[1] < Math.max(minShortSidePixels, minHeightPixels);
        return tooSmall ? Verdict.skip("image-too-small") : Verdict.admit();
    }

    public long getMinTotalPixels() {
        return minTotalPixels;
    }

    public void setMinTotalPixels(long minTotalPixels) {
        if (minTotalPixels < 0) {
            throw new IllegalArgumentException(
                    "minTotalPixels must be 0 or more, got: " + minTotalPixels);
        }
        this.minTotalPixels = minTotalPixels;
    }

    public int getMinShortSidePixels() {
        return minShortSidePixels;
    }

    public void setMinShortSidePixels(int minShortSidePixels) {
        if (minShortSidePixels < 0) {
            throw new IllegalArgumentException(
                    "minShortSidePixels must be 0 or more, got: " + minShortSidePixels);
        }
        this.minShortSidePixels = minShortSidePixels;
    }

    public int getMinWidthPixels() {
        return minWidthPixels;
    }

    public void setMinWidthPixels(int minWidthPixels) {
        if (minWidthPixels < 0) {
            throw new IllegalArgumentException(
                    "minWidthPixels must be 0 or more, got: " + minWidthPixels);
        }
        this.minWidthPixels = minWidthPixels;
    }

    public int getMinHeightPixels() {
        return minHeightPixels;
    }

    public void setMinHeightPixels(int minHeightPixels) {
        if (minHeightPixels < 0) {
            throw new IllegalArgumentException(
                    "minHeightPixels must be 0 or more, got: " + minHeightPixels);
        }
        this.minHeightPixels = minHeightPixels;
    }
}
