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

import org.apache.tika.parser.pages.PagesConfig;
import org.apache.tika.parser.pages.TextPolicy;

/**
 * The 4.0 {@code "pdf-parser": {"ocr": {...}}} block, kept so those configs still load. Every
 * field it sets is folded onto the parser's {@code "pages"} overlay by
 * {@link PDFParserConfig#setOcr}; a dump writes {@code "pages"} only.
 *
 * @deprecated since 4.1.0; configure {@code "pages"} (see {@link PagesConfig}).
 */
@Deprecated
public class OcrConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The 4.0 spelling of {@link TextPolicy}. */
    public enum Strategy {
        AUTO(TextPolicy.AUTO),
        NO_OCR(TextPolicy.EXTRACT),
        OCR_ONLY(TextPolicy.OCR),
        OCR_AND_TEXT_EXTRACTION(TextPolicy.EXTRACT_AND_OCR);

        private final TextPolicy text;

        Strategy(TextPolicy text) {
            this.text = text;
        }

        public TextPolicy toText() {
            return text;
        }
    }

    /** What PDFBox draws when it renders a page; PDF-only, so it stays on the parser. */
    public enum RenderingStrategy {
        NO_TEXT,
        TEXT_ONLY,
        VECTOR_GRAPHICS_ONLY,
        ALL
    }

    /** The 4.0 spelling of {@link org.apache.tika.renderer.ImageFormat}. */
    public enum ImageFormat {
        PNG, TIFF, JPEG;

        public String getFormatName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public org.apache.tika.renderer.ImageFormat toCore() {
            return org.apache.tika.renderer.ImageFormat.valueOf(name());
        }
    }

    /** The 4.0 spelling of {@link org.apache.tika.renderer.ImageType}. */
    public enum ImageType {
        RGB, GRAY;

        public org.apache.tika.renderer.ImageType toCore() {
            return org.apache.tika.renderer.ImageType.valueOf(name());
        }
    }

    /** The 4.0 spelling of {@link PagesConfig.Auto}. */
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

        public PagesConfig.Auto toAuto() {
            PagesConfig.Auto auto = new PagesConfig.Auto();
            auto.setUnmappedUnicodeCharsPerPage(unmappedUnicodeCharsPerPage);
            auto.setTotalCharsPerPage(totalCharsPerPage);
            return auto;
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

    // every field nullable: only what a config set is folded onto "pages"
    private Strategy strategy;
    private StrategyAuto strategyAuto;
    private RenderingStrategy renderingStrategy;
    private Integer dpi;
    private ImageType imageType;
    private ImageFormat imageFormat;
    private Float imageQuality;
    private Long maxImagePixels;
    private Integer maxPagesToOcr;

    /** Writes every set field onto the overlay under its {@code "pages"} name. */
    void applyTo(PagesConfig pages) {
        if (strategy != null) {
            pages.setText(strategy.toText());
        }
        if (strategyAuto != null) {
            pages.ocr().setAuto(strategyAuto.toAuto());
        }
        if (maxPagesToOcr != null) {
            pages.ocr().setMaxPages(maxPagesToOcr);
        }
        if (dpi != null) {
            pages.render().setDpi(dpi);
        }
        if (imageType != null) {
            pages.render().setImageType(imageType.toCore());
        }
        if (imageFormat != null) {
            pages.render().setImageFormat(imageFormat.toCore());
        }
        if (imageQuality != null) {
            pages.render().setImageQuality(imageQuality);
        }
        if (maxImagePixels != null) {
            pages.render().setMaxImagePixels(maxImagePixels);
        }
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
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

    public void setDpi(Integer dpi) {
        this.dpi = dpi;
    }

    public void setImageType(ImageType imageType) {
        this.imageType = imageType;
    }

    public void setImageFormat(ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
    }

    public void setImageQuality(Float imageQuality) {
        this.imageQuality = imageQuality;
    }

    public void setMaxImagePixels(Long maxImagePixels) {
        this.maxImagePixels = maxImagePixels;
    }

    public void setMaxPagesToOcr(Integer maxPagesToOcr) {
        this.maxPagesToOcr = maxPagesToOcr;
    }
}
