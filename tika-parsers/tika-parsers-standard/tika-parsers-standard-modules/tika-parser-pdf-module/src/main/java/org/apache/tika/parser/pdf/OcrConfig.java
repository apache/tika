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

/**
 * Configuration for OCR processing in PDF parsing.
 * Groups all OCR-related settings together.
 */
public class OcrConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The 4.0 spelling of {@link PDFParserConfig.TextPolicy}, kept so {@code ocr.strategy}
     * still loads.
     *
     * @deprecated since 4.1.0; set {@code "text"} on the parser instead.
     */
    @Deprecated
    public enum Strategy {
        AUTO(PDFParserConfig.TextPolicy.AUTO),
        NO_OCR(PDFParserConfig.TextPolicy.EXTRACT),
        OCR_ONLY(PDFParserConfig.TextPolicy.OCR),
        OCR_AND_TEXT_EXTRACTION(PDFParserConfig.TextPolicy.EXTRACT_AND_OCR);

        private final PDFParserConfig.TextPolicy text;

        Strategy(PDFParserConfig.TextPolicy text) {
            this.text = text;
        }

        public PDFParserConfig.TextPolicy toText() {
            return text;
        }
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

    /** The alias, null unless a 4.0 config set it; no getter so a dump writes "text" only. */
    private Strategy strategy;
    private StrategyAuto strategyAuto = StrategyAuto.BETTER;
    private RenderingStrategy renderingStrategy = RenderingStrategy.ALL;
    private int dpi = 300;
    private ImageType imageType = ImageType.GRAY;
    private ImageFormat imageFormat = ImageFormat.PNG;
    /**
     * Compression quality handed to ImageIO when writing rendered pages. For PNG this is
     * an inverted effort knob, not fidelity: 1.0 writes an uncompressed file, 0.0 spends
     * ~10x the time of 0.5 for a few percent smaller output. PNG is always lossless.
     */
    private float imageQuality = 0.5f;

    /**
     * Maximum total pixels (width &times; height) allowed for a rendered
     * page image before OCR is skipped for that page. This prevents OOM
     * from rendering pathologically large PDF pages (e.g., architectural
     * drawings, maps) via PDFBox's in-process renderer.
     * <p>
     * When using the Poppler renderer, prefer {@code maxScaleTo} on
     * {@code PopplerRenderer} instead — it prevents the large image from
     * ever being created. This limit is the safety net for the PDFBox
     * rendering path.
     * <p>
     * Default is 100,000,000 (100 megapixels, roughly 10,000 &times;
     * 10,000). Set to {@code -1} for no limit (not recommended).
     */
    private long maxImagePixels = 100_000_000L;

    /**
     * Maximum number of pages to OCR per document. Pages beyond this
     * limit are processed for text extraction only (if applicable)
     * but not rendered or sent to OCR.
     * <p>
     * Default is {@code -1} (no limit — all pages are eligible for OCR).
     */
    private int maxPagesToOcr = -1;

    /**
     * @deprecated since 4.1.0; use {@link PDFParserConfig#setText}. Ignored when
     * {@code "text"} is set.
     */
    @Deprecated
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    PDFParserConfig.TextPolicy legacyText() {
        return strategy == null ? null : strategy.toText();
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

    public float getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(float imageQuality) {
        this.imageQuality = imageQuality;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    /**
     * Set the maximum total pixels (width &times; height) for a rendered
     * page image. Pages exceeding this limit are skipped for OCR.
     * Default is 100,000,000. Set to {@code -1} for no limit (not recommended).
     */
    public void setMaxImagePixels(long maxImagePixels) {
        if (maxImagePixels < 1 && maxImagePixels != -1) {
            throw new IllegalArgumentException(
                    "maxImagePixels must be -1 (no limit) or at least 1, got: "
                            + maxImagePixels);
        }
        this.maxImagePixels = maxImagePixels;
    }

    public int getMaxPagesToOcr() {
        return maxPagesToOcr;
    }

    /**
     * Set the maximum number of pages to OCR per document.
     * Default is {@code -1} (no limit). Must be {@code -1} or at least {@code 1}.
     */
    public void setMaxPagesToOcr(int maxPagesToOcr) {
        if (maxPagesToOcr < 1 && maxPagesToOcr != -1) {
            throw new IllegalArgumentException(
                    "maxPagesToOcr must be -1 (no limit) or at least 1, got: "
                            + maxPagesToOcr);
        }
        this.maxPagesToOcr = maxPagesToOcr;
    }
}
