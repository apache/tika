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

import java.io.Serializable;
import java.util.Objects;

/**
 * Settings for the page images the parser emits as embedded documents under
 * {@code imageStrategy} {@code RENDER_PAGES_BEFORE_PARSE} or {@code RENDER_PAGES_AT_PAGE_END}:
 * {@code "pdf-parser": {"rendering": {"dpi": 96, "imageType": "RGB"}}}. A field left unset
 * takes the {@code ocr} value, so with no block at all these renders keep matching the OCR
 * renders as they did in 4.0. OCR, page annotators and PAGES inference input always render
 * with the {@code ocr} settings.
 *
 * @since Apache Tika 4.1.0
 */
public class RenderingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer dpi;
    private OcrConfig.ImageType imageType;
    private OcrConfig.ImageFormat imageFormat;
    private Float imageQuality;
    private Long maxImagePixels;

    /** The OCR settings, as a fully set block. */
    public static RenderingConfig from(OcrConfig ocr) {
        RenderingConfig config = new RenderingConfig();
        config.dpi = ocr.getDpi();
        config.imageType = ocr.getImageType();
        config.imageFormat = ocr.getImageFormat();
        config.imageQuality = ocr.getImageQuality();
        config.maxImagePixels = ocr.getMaxImagePixels();
        return config;
    }

    /** This block with every unset field taken from {@code ocr}. */
    public RenderingConfig resolve(OcrConfig ocr) {
        RenderingConfig resolved = from(ocr);
        if (dpi != null) {
            resolved.dpi = dpi;
        }
        if (imageType != null) {
            resolved.imageType = imageType;
        }
        if (imageFormat != null) {
            resolved.imageFormat = imageFormat;
        }
        if (imageQuality != null) {
            resolved.imageQuality = imageQuality;
        }
        if (maxImagePixels != null) {
            resolved.maxImagePixels = maxImagePixels;
        }
        return resolved;
    }

    /** True when a page rendered with either block is the same image. */
    public boolean rendersSameImageAs(RenderingConfig other) {
        return Objects.equals(dpi, other.dpi) && imageType == other.imageType
                && imageFormat == other.imageFormat
                && Objects.equals(imageQuality, other.imageQuality);
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

    public OcrConfig.ImageType getImageType() {
        return imageType;
    }

    public void setImageType(OcrConfig.ImageType imageType) {
        this.imageType = imageType;
    }

    public OcrConfig.ImageFormat getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(OcrConfig.ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
    }

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

    /** Pages that would render larger than this many pixels are skipped; -1 for no limit. */
    public void setMaxImagePixels(Long maxImagePixels) {
        if (maxImagePixels != null && maxImagePixels < 1 && maxImagePixels != -1) {
            throw new IllegalArgumentException(
                    "maxImagePixels must be -1 (no limit) or at least 1, got: " + maxImagePixels);
        }
        this.maxImagePixels = maxImagePixels;
    }
}
