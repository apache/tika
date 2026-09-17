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

import java.io.Serializable;
import java.util.Objects;

/**
 * How a document is rendered to pixels: the {@code "render"} block of {@code "pages"}. A
 * renderer reads the effective settings from the {@link org.apache.tika.parser.ParseContext}
 * (class-keyed), scoped there by the parser around each render call.
 * <p>
 * {@code dpi} is the target; {@code maxWidth}/{@code maxHeight} is a ceiling: render at
 * {@code dpi}, scale down to fit if either bound is exceeded, aspect preserved, never enlarge.
 * {@code maxImagePixels} skips a page that would still be larger than that; {@code minWidth}/
 * {@code minHeight} skips one that would be smaller at the target dpi (a hairline rule, a spacer).
 * <p>
 * Every field is nullable so an instance can be an overlay: {@link #over} applies the set
 * fields of one instance to another. {@link #defaults()} has every field set.
 *
 * @since Apache Tika 4.1
 */
public class RenderSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int NO_LIMIT = -1;

    private Integer dpi;
    private ImageType imageType;
    private ImageFormat imageFormat;
    private Float imageQuality;
    private Long maxImagePixels;
    private Integer maxWidth;
    private Integer maxHeight;
    private Integer minWidth;
    private Integer minHeight;

    /** 300 dpi gray PNG at quality 0.5, at most 100 megapixels, no box, at least 2 x 2. */
    public static RenderSettings defaults() {
        RenderSettings settings = new RenderSettings();
        settings.dpi = 300;
        settings.imageType = ImageType.GRAY;
        settings.imageFormat = ImageFormat.PNG;
        settings.imageQuality = 0.5f;
        settings.maxImagePixels = 100_000_000L;
        settings.maxWidth = NO_LIMIT;
        settings.maxHeight = NO_LIMIT;
        settings.minWidth = 2;
        settings.minHeight = 2;
        return settings;
    }

    /** A copy of this with every set field of {@code overlay} applied; null overlay is this. */
    public RenderSettings over(RenderSettings overlay) {
        RenderSettings result = copy();
        if (overlay == null) {
            return result;
        }
        if (overlay.dpi != null) {
            result.dpi = overlay.dpi;
        }
        if (overlay.imageType != null) {
            result.imageType = overlay.imageType;
        }
        if (overlay.imageFormat != null) {
            result.imageFormat = overlay.imageFormat;
        }
        if (overlay.imageQuality != null) {
            result.imageQuality = overlay.imageQuality;
        }
        if (overlay.maxImagePixels != null) {
            result.maxImagePixels = overlay.maxImagePixels;
        }
        if (overlay.maxWidth != null) {
            result.maxWidth = overlay.maxWidth;
        }
        if (overlay.maxHeight != null) {
            result.maxHeight = overlay.maxHeight;
        }
        if (overlay.minWidth != null) {
            result.minWidth = overlay.minWidth;
        }
        if (overlay.minHeight != null) {
            result.minHeight = overlay.minHeight;
        }
        return result;
    }

    public RenderSettings copy() {
        RenderSettings result = new RenderSettings();
        result.dpi = dpi;
        result.imageType = imageType;
        result.imageFormat = imageFormat;
        result.imageQuality = imageQuality;
        result.maxImagePixels = maxImagePixels;
        result.maxWidth = maxWidth;
        result.maxHeight = maxHeight;
        result.minWidth = minWidth;
        result.minHeight = minHeight;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RenderSettings other && Objects.equals(dpi, other.dpi)
                && imageType == other.imageType && imageFormat == other.imageFormat
                && Objects.equals(imageQuality, other.imageQuality)
                && Objects.equals(maxImagePixels, other.maxImagePixels)
                && Objects.equals(maxWidth, other.maxWidth)
                && Objects.equals(maxHeight, other.maxHeight)
                && Objects.equals(minWidth, other.minWidth)
                && Objects.equals(minHeight, other.minHeight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dpi, imageType, imageFormat, imageQuality, maxImagePixels, maxWidth,
                maxHeight, minWidth, minHeight);
    }

    /**
     * The dpi a page of this size in points is rendered at: the target, reduced so the image
     * fits the box, never raised. Fractional, so the box is met exactly. Requires every field set.
     */
    public float effectiveDpi(double widthPoints, double heightPoints) {
        double scale = 1.0;
        if (maxWidth > 0 && widthPoints > 0) {
            scale = Math.min(scale, maxWidth / (widthPoints / 72.0 * dpi));
        }
        if (maxHeight > 0 && heightPoints > 0) {
            scale = Math.min(scale, maxHeight / (heightPoints / 72.0 * dpi));
        }
        return (float) Math.max(0.01, dpi * scale);
    }

    /** True when the page rendered at the target dpi would be smaller than the minimum. */
    public boolean belowMinimum(double widthPoints, double heightPoints) {
        return Math.ceil(widthPoints / 72.0 * dpi) < minWidth
                || Math.ceil(heightPoints / 72.0 * dpi) < minHeight;
    }

    /** Pixels of the page rendered at {@link #effectiveDpi}; what {@code maxImagePixels} bounds. */
    public long estimatedPixels(double widthPoints, double heightPoints) {
        return estimatedPixels(widthPoints, heightPoints, effectiveDpi(widthPoints, heightPoints));
    }

    /** Pixels of the page rendered at this dpi; saturates rather than overflowing. */
    public static long estimatedPixels(double widthPoints, double heightPoints, double dpi) {
        double pixels = Math.ceil(widthPoints / 72.0 * dpi) * Math.ceil(heightPoints / 72.0 * dpi);
        return pixels >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) pixels;
    }

    /** True when an image of this many pixels exceeds {@code maxImagePixels}. */
    public boolean exceedsMaxPixels(long pixels) {
        return maxImagePixels > 0 && pixels > maxImagePixels;
    }

    /**
     * A pixel size scaled down to fit the box, aspect preserved, never enlarged: for renderers
     * that start from a bitmap rather than a page size.
     */
    public long[] fit(long width, long height) {
        double scale = 1.0;
        if (maxWidth > 0 && width > maxWidth) {
            scale = Math.min(scale, (double) maxWidth / width);
        }
        if (maxHeight > 0 && height > maxHeight) {
            scale = Math.min(scale, (double) maxHeight / height);
        }
        return new long[] { Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)) };
    }

    public Integer getDpi() {
        return dpi;
    }

    public void setDpi(Integer dpi) {
        if (dpi != null && dpi < 1) {
            throw new IllegalArgumentException("dpi must be at least 1, got: " + dpi);
        }
        this.dpi = dpi;
    }

    public ImageType getImageType() {
        return imageType;
    }

    public void setImageType(ImageType imageType) {
        this.imageType = imageType;
    }

    public ImageFormat getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
    }

    /**
     * ImageIO's compression quality. For PNG an inverted effort knob, not fidelity: 1.0 writes
     * an uncompressed file, 0.0 spends ~10x the time of 0.5 for a few percent smaller output.
     */
    public Float getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(Float imageQuality) {
        if (imageQuality != null && (imageQuality < 0f || imageQuality > 1f)) {
            throw new IllegalArgumentException(
                    "imageQuality must be between 0.0 and 1.0, got: " + imageQuality);
        }
        this.imageQuality = imageQuality;
    }

    public Long getMaxImagePixels() {
        return maxImagePixels;
    }

    /** A page that would still be larger than this after the box is skipped; -1 for no limit. */
    public void setMaxImagePixels(Long maxImagePixels) {
        if (maxImagePixels != null && maxImagePixels < 1 && maxImagePixels != NO_LIMIT) {
            throw new IllegalArgumentException(
                    "maxImagePixels must be -1 (no limit) or at least 1, got: " + maxImagePixels);
        }
        this.maxImagePixels = maxImagePixels;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    /** Widest the image may be, in pixels; scaled down to fit, never enlarged; -1 for no bound. */
    public void setMaxWidth(Integer maxWidth) {
        this.maxWidth = bound("maxWidth", maxWidth);
    }

    public Integer getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(Integer maxHeight) {
        this.maxHeight = bound("maxHeight", maxHeight);
    }

    public Integer getMinWidth() {
        return minWidth;
    }

    /** Narrower than this at the target dpi and the document is not rendered at all; 0 for none. */
    public void setMinWidth(Integer minWidth) {
        this.minWidth = minimum("minWidth", minWidth);
    }

    public Integer getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(Integer minHeight) {
        this.minHeight = minimum("minHeight", minHeight);
    }

    private static Integer bound(String name, Integer value) {
        if (value != null && value < 1 && value != NO_LIMIT) {
            throw new IllegalArgumentException(
                    name + " must be -1 (no bound) or at least 1, got: " + value);
        }
        return value;
    }

    private static Integer minimum(String name, Integer value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must be at least 0, got: " + value);
        }
        return value;
    }
}
