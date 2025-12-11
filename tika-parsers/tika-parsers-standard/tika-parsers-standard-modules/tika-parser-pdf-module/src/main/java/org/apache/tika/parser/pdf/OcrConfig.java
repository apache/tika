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
import java.util.Locale;

import org.apache.pdfbox.rendering.ImageType;

/**
 * Configuration for OCR processing in PDF parsing.
 * Groups all OCR-related settings together.
 */
public class OcrConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Strategy {
        AUTO,
        NO_OCR,
        OCR_ONLY,
        OCR_AND_TEXT_EXTRACTION
    }

    public enum RenderingStrategy {
        NO_TEXT,
        TEXT_ONLY,
        VECTOR_GRAPHICS_ONLY,
        ALL
    }

    public enum ImageFormat {
        PNG, TIFF, JPEG;

        public String getFormatName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ImageType {
        RGB(org.apache.pdfbox.rendering.ImageType.RGB),
        GRAY(org.apache.pdfbox.rendering.ImageType.GRAY);

        private final org.apache.pdfbox.rendering.ImageType pdfBoxImageType;

        ImageType(org.apache.pdfbox.rendering.ImageType pdfBoxImageType) {
            this.pdfBoxImageType = pdfBoxImageType;
        }

        public org.apache.pdfbox.rendering.ImageType getPdfBoxImageType() {
            return pdfBoxImageType;
        }
    }

    /**
     * Configuration for AUTO strategy behavior.
     * Controls when OCR is triggered based on character analysis.
     */
    public static class StrategyAuto implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final StrategyAuto BETTER = new StrategyAuto(10, 10);
        public static final StrategyAuto FASTER = new StrategyAuto(0.1f, 10);

        private float unmappedUnicodeCharsPerPage;
        private int totalCharsPerPage;

        public StrategyAuto() {
            this(10, 10);
        }

        public StrategyAuto(float unmappedUnicodeCharsPerPage, int totalCharsPerPage) {
            this.unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage;
            this.totalCharsPerPage = totalCharsPerPage;
        }

        public float getUnmappedUnicodeCharsPerPage() {
            return unmappedUnicodeCharsPerPage;
        }

        public void setUnmappedUnicodeCharsPerPage(float unmappedUnicodeCharsPerPage) {
            this.unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage;
        }

        public int getTotalCharsPerPage() {
            return totalCharsPerPage;
        }

        public void setTotalCharsPerPage(int totalCharsPerPage) {
            this.totalCharsPerPage = totalCharsPerPage;
        }

        @Override
        public String toString() {
            String unmappedString;
            if (unmappedUnicodeCharsPerPage < 1.0) {
                unmappedString = String.format(Locale.US, "%.03f",
                        unmappedUnicodeCharsPerPage * 100) + "%";
            } else {
                unmappedString = String.format(Locale.US, "%.0f", unmappedUnicodeCharsPerPage);
            }
            return unmappedString + "," + totalCharsPerPage;
        }
    }

    private Strategy strategy = Strategy.AUTO;
    private StrategyAuto strategyAuto = StrategyAuto.BETTER;
    private RenderingStrategy renderingStrategy = RenderingStrategy.ALL;
    private int dpi = 300;
    private ImageType imageType = ImageType.GRAY;
    private ImageFormat imageFormat = ImageFormat.PNG;
    private float imageQuality = 1.0f;

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public StrategyAuto getStrategyAuto() {
        return strategyAuto;
    }

    public void setStrategyAuto(StrategyAuto strategyAuto) {
        this.strategyAuto = strategyAuto;
    }

    public RenderingStrategy getRenderingStrategy() {
        return renderingStrategy;
    }

    public void setRenderingStrategy(RenderingStrategy renderingStrategy) {
        this.renderingStrategy = renderingStrategy;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
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
     * @return lowercase format name for use with image writers
     */
    public String getImageFormatName() {
        return imageFormat.getFormatName();
    }

    public float getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(float imageQuality) {
        this.imageQuality = imageQuality;
    }
}
