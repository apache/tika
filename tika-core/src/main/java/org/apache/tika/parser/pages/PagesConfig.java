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
package org.apache.tika.parser.pages;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.renderer.RenderSettings;

/**
 * What a parser does with a document's pages when the document has to be rendered to get
 * pixels: how it renders ({@code render}), where a page's text comes from ({@code text}), the
 * recognizer budget and verdict ({@code ocr}), what is released to inference bindings
 * ({@code inference}), and whether renders are emitted as embedded documents ({@code emit}).
 * One block for every rendering parser; the PDF parser and the EMF/WMF parsers read it.
 * <p>
 * Every field is nullable so an instance can be an overlay, and the same class is read at four
 * layers, later ones winning per field: the config's {@code "parse-context": {"pages": ...}},
 * a request's {@code "pages"} (which replaces the config's, as every parse-context key does),
 * a parser's own {@code "pages"} under {@code "parsers"}, and that parser's {@code "pages"} in
 * the request. {@link #resolve} folds them onto {@link #defaults()}.
 *
 * @since Apache Tika 4.1
 */
@TikaComponent(name = "pages", spi = false)
public class PagesConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String KEY = "pages";

    private RenderSettings render;
    private TextPolicy text;
    private Ocr ocr;
    private List<InputKind> inference;
    private Emit emit;

    /** Every field set: default render, AUTO, no OCR budget, TEXT released, nothing emitted. */
    public static PagesConfig defaults() {
        PagesConfig config = new PagesConfig();
        config.render = RenderSettings.defaults();
        config.text = TextPolicy.AUTO;
        config.ocr = Ocr.defaults();
        config.inference = List.of(InputKind.TEXT);
        config.emit = Emit.defaults();
        return config;
    }

    /**
     * The effective block for a parse: {@link #defaults()}, the context's {@code "pages"}
     * (JSON, or a {@code PagesConfig} set on the context by class), then each overlay in order.
     */
    public static PagesConfig resolve(ParseContext context, PagesConfig... overlays)
            throws TikaConfigException, IOException {
        PagesConfig effective = defaults();
        PagesConfig top = ParseContextConfig.getConfig(context, KEY, PagesConfig.class, null);
        effective = effective.over(top);
        for (PagesConfig overlay : overlays) {
            effective = effective.over(overlay);
        }
        return effective;
    }

    /** A copy of this with every set field of {@code overlay} applied; null overlay is this. */
    public PagesConfig over(PagesConfig overlay) {
        PagesConfig result = new PagesConfig();
        result.render = render;
        result.text = text;
        result.ocr = ocr;
        result.inference = inference;
        result.emit = emit;
        if (overlay == null) {
            return result;
        }
        if (overlay.render != null) {
            result.render = render == null ? overlay.render.copy() : render.over(overlay.render);
        }
        if (overlay.text != null) {
            result.text = overlay.text;
        }
        if (overlay.ocr != null) {
            result.ocr = ocr == null ? overlay.ocr.over(null) : ocr.over(overlay.ocr);
        }
        if (overlay.inference != null) {
            result.inference = overlay.inference;
        }
        if (overlay.emit != null) {
            result.emit = emit == null ? overlay.emit.over(null) : emit.over(overlay.emit);
        }
        return result;
    }

    /** The settings emitted renders use: {@code render} with {@code emit.render} applied. */
    public RenderSettings emittedRender() {
        return render.over(emit == null ? null : emit.render);
    }

    /** Whether emitted renders are the same image as the render OCR and inference see. */
    public boolean emitsSameImage() {
        return emittedRender().rendersSameImageAs(render);
    }

    public RenderSettings getRender() {
        return render;
    }

    public void setRender(RenderSettings render) {
        this.render = render;
    }

    /** The render overlay, created if unset: {@code pages.render().setDpi(96)}. */
    public RenderSettings render() {
        if (render == null) {
            render = new RenderSettings();
        }
        return render;
    }

    public TextPolicy getText() {
        return text;
    }

    public void setText(TextPolicy text) {
        this.text = text;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public void setOcr(Ocr ocr) {
        this.ocr = ocr;
    }

    /** The OCR overlay, created if unset. */
    public Ocr ocr() {
        if (ocr == null) {
            ocr = new Ocr();
        }
        return ocr;
    }

    public List<InputKind> getInference() {
        return inference;
    }

    /** {@code TEXT}, {@code PAGES}, or both; what the parser releases to inference bindings. */
    public void setInference(List<InputKind> inference) {
        if (inference == null) {
            this.inference = null;
            return;
        }
        for (InputKind kind : inference) {
            if (kind != InputKind.TEXT && kind != InputKind.PAGES) {
                throw new IllegalArgumentException(
                        "pages.inference takes TEXT and PAGES, got: " + kind);
            }
        }
        this.inference = Collections.unmodifiableList(new ArrayList<>(inference));
    }

    public Emit getEmit() {
        return emit;
    }

    public void setEmit(Emit emit) {
        this.emit = emit;
    }

    /** The emit overlay, created if unset: {@code pages.emit().setEnabled(true)}. */
    public Emit emit() {
        if (emit == null) {
            emit = new Emit();
        }
        return emit;
    }

    /** The recognizer side of {@code text}: how many pages may be OCR'd, how AUTO decides. */
    public static class Ocr implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer maxPages;
        private Auto auto;

        public static Ocr defaults() {
            Ocr ocr = new Ocr();
            ocr.maxPages = -1;
            ocr.auto = Auto.defaults();
            return ocr;
        }

        public Ocr over(Ocr overlay) {
            Ocr result = new Ocr();
            result.maxPages = maxPages;
            result.auto = auto;
            if (overlay == null) {
                return result;
            }
            if (overlay.maxPages != null) {
                result.maxPages = overlay.maxPages;
            }
            if (overlay.auto != null) {
                result.auto = auto == null ? overlay.auto.over(null) : auto.over(overlay.auto);
            }
            return result;
        }

        public Integer getMaxPages() {
            return maxPages;
        }

        /** Pages of one document that may be OCR'd, counted from the first; -1 for no limit. */
        public void setMaxPages(Integer maxPages) {
            if (maxPages != null && maxPages < 1 && maxPages != -1) {
                throw new IllegalArgumentException(
                        "ocr.maxPages must be -1 (no limit) or at least 1, got: " + maxPages);
            }
            this.maxPages = maxPages;
        }

        public Auto getAuto() {
            return auto;
        }

        public void setAuto(Auto auto) {
            this.auto = auto;
        }

        /** The verdict overlay, created if unset. */
        public Auto auto() {
            if (auto == null) {
                auto = new Auto();
            }
            return auto;
        }
    }

    /**
     * When {@link TextPolicy#AUTO} sends a page to OCR: too few characters, or too many glyphs
     * without a Unicode mapping. The unmapped count is a PDF font fact; a format without the
     * signal never trips it.
     */
    public static class Auto implements Serializable {

        private static final long serialVersionUID = 1L;

        private Float unmappedUnicodeCharsPerPage;
        private Integer totalCharsPerPage;

        public static Auto defaults() {
            Auto auto = new Auto();
            auto.unmappedUnicodeCharsPerPage = 10f;
            auto.totalCharsPerPage = 10;
            return auto;
        }

        public Auto over(Auto overlay) {
            Auto result = new Auto();
            result.unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage;
            result.totalCharsPerPage = totalCharsPerPage;
            if (overlay == null) {
                return result;
            }
            if (overlay.unmappedUnicodeCharsPerPage != null) {
                result.unmappedUnicodeCharsPerPage = overlay.unmappedUnicodeCharsPerPage;
            }
            if (overlay.totalCharsPerPage != null) {
                result.totalCharsPerPage = overlay.totalCharsPerPage;
            }
            return result;
        }

        public Float getUnmappedUnicodeCharsPerPage() {
            return unmappedUnicodeCharsPerPage;
        }

        /** A count when 1 or more, a fraction of the page's characters when below 1. */
        public void setUnmappedUnicodeCharsPerPage(Float unmappedUnicodeCharsPerPage) {
            this.unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage;
        }

        public Integer getTotalCharsPerPage() {
            return totalCharsPerPage;
        }

        /** A page with fewer characters than this is sent to OCR. */
        public void setTotalCharsPerPage(Integer totalCharsPerPage) {
            this.totalCharsPerPage = totalCharsPerPage;
        }
    }

    /** Whether, and which, renders are emitted as RENDERING embedded documents. */
    public static class Emit implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean enabled;
        private Integer maxPages;
        private Set<String> resourceTypes;
        private RenderSettings render;

        public static Emit defaults() {
            Emit emit = new Emit();
            emit.enabled = false;
            emit.maxPages = -1;
            emit.resourceTypes = Collections.emptySet();
            emit.render = new RenderSettings();
            return emit;
        }

        public Emit over(Emit overlay) {
            Emit result = new Emit();
            result.enabled = enabled;
            result.maxPages = maxPages;
            result.resourceTypes = resourceTypes;
            result.render = render;
            if (overlay == null) {
                return result;
            }
            if (overlay.enabled != null) {
                result.enabled = overlay.enabled;
            }
            if (overlay.maxPages != null) {
                result.maxPages = overlay.maxPages;
            }
            if (overlay.resourceTypes != null) {
                result.resourceTypes = overlay.resourceTypes;
            }
            if (overlay.render != null) {
                result.render = render == null ? overlay.render.copy() : render.over(overlay.render);
            }
            return result;
        }

        /**
         * Whether the document with this metadata has its renders emitted: on, and its
         * {@code tk:embedded-resource-type} is listed when the list is not empty. A top-level
         * document has no resource type, so a non-empty list never matches it.
         */
        public boolean applies(Metadata metadata) {
            if (enabled == null || !enabled) {
                return false;
            }
            if (resourceTypes == null || resourceTypes.isEmpty()) {
                return true;
            }
            String type = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            return type != null && resourceTypes.contains(type);
        }

        /** Whether the 1-based page is within {@code maxPages}. */
        public boolean withinBudget(int page) {
            return maxPages == null || maxPages < 1 || page <= maxPages;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getMaxPages() {
            return maxPages;
        }

        /** Pages of one document emitted, counted from the first; -1 for no limit. */
        public void setMaxPages(Integer maxPages) {
            if (maxPages != null && maxPages < 1 && maxPages != -1) {
                throw new IllegalArgumentException(
                        "emit.maxPages must be -1 (no limit) or at least 1, got: " + maxPages);
            }
            this.maxPages = maxPages;
        }

        public Set<String> getResourceTypes() {
            return resourceTypes;
        }

        /**
         * Emit only for documents that are embedded documents of one of these
         * {@code tk:embedded-resource-type}s, e.g. {@code ["THUMBNAIL"]}; empty for every
         * document.
         */
        public void setResourceTypes(Set<String> resourceTypes) {
            if (resourceTypes == null) {
                this.resourceTypes = null;
                return;
            }
            Set<String> types = new LinkedHashSet<>();
            for (String type : resourceTypes) {
                // a typo would silently disable emission
                types.add(TikaCoreProperties.EmbeddedResourceType.valueOf(type).name());
            }
            this.resourceTypes = Collections.unmodifiableSet(types);
        }

        public RenderSettings getRender() {
            return render;
        }

        /** Overlay on {@code pages.render} for emitted renders only. */
        public void setRender(RenderSettings render) {
            this.render = render;
        }

        /** The emitted-render overlay, created if unset. */
        public RenderSettings render() {
            if (render == null) {
                render = new RenderSettings();
            }
            return render;
        }
    }
}
