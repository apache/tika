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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.text.TextPosition;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.renderer.Renderer;

/**
 * Text extraction that follows a tagged PDF's structure tree.
 * <p>
 * The text stripper still does what it does in {@link PDF2XHTML}: it assembles words and lines
 * from glyph positions, with the same spacing heuristics and config. This class only adds a
 * marked-content stack over the content stream, so every glyph carries the MCID it was drawn
 * under, and records the stripper's word and separator calls for the page instead of writing
 * them. Once the page is assembled it either emits the words through the structure tree, or
 * replays them exactly as {@link PDF2XHTML} would have written them. A page falls back when
 * it has no tagged text, or under {@link MarkedContentConfig.Strategy#AUTO} when the tree
 * claims too little of the page's text or references content the page does not have.
 * <p>
 * Text the tree does not reference is not lost: untagged text and /Artifact content (running
 * headers, footers, page numbers) follow the tagged content on each page in their own divs.
 *
 * @since 1.24
 */
public class PDFMarkedContent2XHTML extends PDF2XHTML {

    private static final Pattern CLASS_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");
    private static final int MAX_CLASS_NAME = 64;
    /**
     * Most elements open at once. Tika's SecureContentHandler treats 100 levels of XML
     * nesting as a zip bomb, and real trees go deeper (an Antenna House manual: 601); grouping
     * containers beyond the budget are written transparently, innermost elements kept.
     */
    static final int MAX_OPEN_ELEMENTS = 60;
    private static final Set<String> GROUPING_TYPES = Set.of("Part", "Art", "Sect", "Div",
            "Aside", "DocumentFragment", "Private", "NonStruct", "Index");
    private static final Object NEWLINE = new Object();
    private static final Object PARAGRAPH_BREAK = new Object();
    /** Elements whose nesting is structure, kept wherever the tree puts them. */
    private static final Set<String> STRUCTURAL_TAGS = Set.of("table", "thead", "tbody", "tfoot",
            "tr", "td", "th", "caption", "ul", "ol", "li");
    private static final Set<String> TABLE_TAGS = Set.of("table", "thead", "tbody", "tfoot", "tr");
    private static final Set<String> ORDERED_LIST_NUMBERING = Set.of(
            PDListAttributeObject.LIST_NUMBERING_DECIMAL,
            PDListAttributeObject.LIST_NUMBERING_LOWER_ALPHA,
            PDListAttributeObject.LIST_NUMBERING_UPPER_ALPHA,
            PDListAttributeObject.LIST_NUMBERING_LOWER_ROMAN,
            PDListAttributeObject.LIST_NUMBERING_UPPER_ROMAN);

    private enum State {
        /** Between pages, or in startPage/endPage. */
        IDLE,
        /** Inside the stripper's writePage: record, write nothing. */
        ROUTING,
        /** Replaying the recorded page as PDF2XHTML would have written it. */
        FLAT,
        /** Emitting the recorded page through the structure tree. */
        TAGGED
    }

    private enum EventKind {
        PARA_START, PARA_END, WORD, WORD_SEP, LINE_SEP
    }

    /** What a glyph was drawn under: a tagged MCID in a content stream, nothing, or an artifact. */
    private static final class Label {
        static final Label UNTAGGED = new Label(null, -1);
        static final Label ARTIFACT = new Label(null, -2);

        final COSBase scope;
        final int mcid;

        Label(COSBase scope, int mcid) {
            this.scope = scope;
            this.mcid = mcid;
        }

        boolean tagged() {
            return mcid >= 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Label)) {
                return false;
            }
            Label other = (Label) o;
            return scope == other.scope && mcid == other.mcid;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(scope) * 31 + mcid;
        }
    }

    private static final class MarkedContentFrame {
        final boolean artifact;
        final int mcid;

        MarkedContentFrame(boolean artifact, int mcid) {
            this.artifact = artifact;
            this.mcid = mcid;
        }
    }

    /** A word's glyphs under one label; a word drawn across MCIDs has several. */
    private static final class Segment {
        final Label label;
        final String text;
        final int positions;

        Segment(Label label, String text, int positions) {
            this.label = label;
            this.text = text;
            this.positions = positions;
        }
    }

    private static final class Event {
        static final Event PARA_START = new Event(EventKind.PARA_START, null, null);
        static final Event PARA_END = new Event(EventKind.PARA_END, null, null);
        static final Event WORD_SEP = new Event(EventKind.WORD_SEP, null, null);
        static final Event LINE_SEP = new Event(EventKind.LINE_SEP, null, null);

        final EventKind kind;
        /** The word as the stripper wrote it; what a flat replay writes. */
        final String text;
        final List<Segment> segments;

        Event(EventKind kind, String text, List<Segment> segments) {
            this.kind = kind;
            this.text = text;
            this.segments = segments;
        }
    }

    /** Consecutive words under one label, with the separators the stripper put between them. */
    private static final class Run {
        final Label label;
        /** Position among the page's runs: two runs written together but not adjacent here
         *  had other content between them. */
        final int ordinal;
        final List<Object> pieces = new ArrayList<>();
        boolean hasText;

        Run(Label label, int ordinal) {
            this.label = label;
            this.ordinal = ordinal;
        }

        boolean endsWithSeparator() {
            if (pieces.isEmpty()) {
                return false;
            }
            Object last = pieces.get(pieces.size() - 1);
            return last == NEWLINE || last == PARAGRAPH_BREAK || ((String) last).isBlank();
        }
    }

    /** How a structure element is written: its element name (null for transparent) and attributes. */
    static final class ElementSpec {
        static final ElementSpec TRANSPARENT = new ElementSpec(null, null, false, null, null, false);

        final String tag;
        final AttributesImpl attributes;
        final boolean inline;
        /** A figure's alternate description, written as its text. */
        final String alt;
        /** An element written around this one: the item a list's stray child sits in. */
        final String outer;
        /** A block written as a span; its edges still separate words. */
        final boolean demoted;

        ElementSpec(String tag, AttributesImpl attributes, boolean inline) {
            this(tag, attributes, inline, null, null, false);
        }

        ElementSpec(String tag, AttributesImpl attributes, boolean inline, String alt,
                    String outer, boolean demoted) {
            this.tag = tag;
            this.attributes = attributes;
            this.inline = inline;
            this.alt = alt;
            this.outer = outer;
            this.demoted = demoted;
        }

        /** The same element as a span, classed by what it was. */
        ElementSpec asSpan() {
            AttributesImpl attrs = attributes == null ? new AttributesImpl() : attributes;
            if (attrs.getIndex("class") < 0) {
                addAttribute(attrs, "class", tag);
            }
            return new ElementSpec("span", attrs, true, alt, outer, true);
        }

        ElementSpec within(String outerTag) {
            return new ElementSpec(tag, attributes, inline, alt, outerTag, demoted);
        }

        int elements() {
            return tag == null ? 0 : outer == null ? 1 : 2;
        }
    }

    /**
     * The element loose text is wrapped in when the tree leaves it straight in a container that
     * holds no text of its own in XHTML: a paragraph in a division, a caption in a table, a cell
     * in a row, an item in a list. The stripper's paragraph breaks split it as they do a flat page.
     */
    private static final class Wrapper {
        final String tag;
        /** Whether it sits inside the group's block, or in its place when that is not a block. */
        final boolean inside;
        /** Path index of the first node inside the wrapper, set per run. */
        int at;
        boolean open;
        /** A paragraph break arrived while an inline element was open; applied once it closes. */
        boolean breakPending;

        Wrapper(String tag, boolean inside) {
            this.tag = tag;
            this.inside = inside;
        }
    }

    private static final class PageStats {
        int tagged;
        /** Tagged text inside a block that holds text (a paragraph, cell, heading), not a bare
         *  container. */
        int inBlock;
        int untagged;
        int artifact;
        int leaves;
        int dangling;
    }

    private final MarkedContentConfig markedContentConfig;
    /** Null when the document's tree is unusable: every page is then written flat. */
    private final StructureIndex index;

    private final Deque<MarkedContentFrame> markedContentStack = new ArrayDeque<>();
    /** Form XObjects with their own /StructParents, innermost first. */
    private final Deque<COSBase> scopeStack = new ArrayDeque<>();
    private final Set<COSBase> pageScopes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TextPosition, Label> labels = new IdentityHashMap<>();
    /** Every (scope, MCID) the page's content streams opened, with or without text. */
    private final Set<Label> openedMcids = new HashSet<>();
    private final List<Event> events = new ArrayList<>();
    private final Set<StructureIndex.Node> altWritten =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** Whitespace after the last word, written only if more text follows in the same block. */
    private final List<Object> held = new ArrayList<>();
    /** The tree path currently open, root first, with each node's element state. */
    private final List<StructureIndex.Node> open = new ArrayList<>();
    private final List<Integer> openState = new ArrayList<>();
    private static final int NOT_WRITTEN = 0;
    private static final int OPEN = 1;
    /** A paragraph or heading closed early because a block opened inside it, as HTML does. */
    private static final int SUSPENDED = 2;
    /** Not written because the nesting budget was spent. */
    private static final int DROPPED = 3;
    private boolean atBlockStart;
    private Label currentLabel;
    private State state = State.IDLE;
    /** startPage's paragraph start, held until the page knows whether it is tagged. */
    private boolean pendingPageStart;
    private boolean inStartPage;
    private boolean pageTagged;
    private int pagesTagged;
    private int pagesFallback;

    private PDFMarkedContent2XHTML(PDDocument document, ContentHandler handler,
                                   ParseContext context, Metadata metadata, PDFParserConfig config,
                                   Renderer renderer,
                                   CompositeContentEnricher contentEnrichers)
            throws IOException {
        super(document, handler, context, metadata, config, renderer, contentEnrichers);
        this.markedContentConfig = config.getMarkedContent();
        StructureIndex loaded = StructureIndex.load(document);
        if (loaded.reason() != null) {
            metadata.add(PDF.MARKED_CONTENT_REJECTIONS, "document:" + loaded.reason());
            this.index = null;
        } else {
            this.index = loaded;
        }
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param pdDocument PDF document
     * @param handler    SAX content handler
     * @param context    parse context
     * @param metadata   PDF metadata
     * @param config     PDF parser config
     * @param renderer   the renderer to use for rendering pages
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(PDDocument pdDocument, ContentHandler handler,
                               ParseContext context,
                               Metadata metadata, PDFParserConfig config, Renderer renderer,
                               CompositeContentEnricher contentEnrichers)
            throws SAXException, TikaException {

        PDFMarkedContent2XHTML pdfMarkedContent2XHTML = null;
        try {
            pdfMarkedContent2XHTML =
                    new PDFMarkedContent2XHTML(pdDocument, handler, context, metadata, config,
                            renderer, contentEnrichers);
            config.configure(pdfMarkedContent2XHTML);
        } catch (IOException e) {
            throw new TikaException("couldn't initialize PDFMarkedContent2XHTML", e);
        }
        try {
            pdfMarkedContent2XHTML.writeText(pdDocument, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (!pdfMarkedContent2XHTML.exceptions.isEmpty()) {
            //throw the first
            throw new TikaException("Unable to extract PDF content",
                    pdfMarkedContent2XHTML.exceptions.get(0));
        }
    }

    // ---- content stream hooks: which MCID is each glyph drawn under ----

    @Override
    public void beginMarkedContentSequence(COSName tag, COSDictionary properties) {
        int mcid = properties == null ? -1 : properties.getInt(COSName.MCID, -1);
        markedContentStack.push(new MarkedContentFrame(COSName.ARTIFACT.equals(tag), mcid));
        currentLabel = null;
        if (index != null && mcid >= 0) {
            COSBase scope = scopeStack.isEmpty() ?
                    (getCurrentPage() == null ? null : getCurrentPage().getCOSObject()) :
                    scopeStack.peek();
            if (scope != null) {
                openedMcids.add(new Label(scope, mcid));
            }
        }
        super.beginMarkedContentSequence(tag, properties);
    }

    @Override
    public void endMarkedContentSequence() {
        // PDFBox skips a BDC whose property dict does not resolve but still fires its EMC
        if (!markedContentStack.isEmpty()) {
            markedContentStack.pop();
        }
        currentLabel = null;
        super.endMarkedContentSequence();
    }

    @Override
    public void showForm(PDFormXObject form) throws IOException {
        boolean pushed = pushScope(form);
        try {
            super.showForm(form);
        } finally {
            popScope(pushed);
        }
    }

    @Override
    public void showTransparencyGroup(PDTransparencyGroup form) throws IOException {
        boolean pushed = pushScope(form);
        try {
            super.showTransparencyGroup(form);
        } finally {
            popScope(pushed);
        }
    }

    private boolean pushScope(PDFormXObject form) {
        if (index == null || form.getStructParents() < 0) {
            return false;
        }
        scopeStack.push(form.getCOSObject());
        pageScopes.add(form.getCOSObject());
        currentLabel = null;
        return true;
    }

    private void popScope(boolean pushed) {
        if (pushed) {
            scopeStack.pop();
            currentLabel = null;
        }
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        if (index != null) {
            labels.put(text, label());
        }
        super.processTextPosition(text);
    }

    private Label label() {
        if (currentLabel != null) {
            return currentLabel;
        }
        int mcid = -1;
        boolean artifact = false;
        for (MarkedContentFrame frame : markedContentStack) {
            if (frame.artifact) {
                artifact = true;
                break;
            }
            if (mcid < 0 && frame.mcid >= 0) {
                mcid = frame.mcid;
            }
        }
        if (artifact) {
            currentLabel = Label.ARTIFACT;
        } else if (mcid < 0) {
            currentLabel = Label.UNTAGGED;
        } else if (scopeStack.isEmpty() && getCurrentPage() == null) {
            currentLabel = Label.UNTAGGED;
        } else {
            COSBase scope = scopeStack.isEmpty() ? getCurrentPage().getCOSObject() :
                    scopeStack.peek();
            currentLabel = new Label(scope, mcid);
        }
        return currentLabel;
    }

    // ---- page lifecycle ----

    @Override
    protected void startPage(PDPage page) throws IOException {
        markedContentStack.clear();
        scopeStack.clear();
        pageScopes.clear();
        pageScopes.add(page.getCOSObject());
        labels.clear();
        openedMcids.clear();
        events.clear();
        currentLabel = null;
        pendingPageStart = false;
        pageTagged = false;
        inStartPage = true;
        try {
            super.startPage(page);
        } finally {
            inStartPage = false;
        }
    }

    @Override
    protected void writePage() throws IOException {
        if (index == null) {
            state = State.FLAT;
            try {
                flushPendingPageStart();
                super.writePage();
            } finally {
                state = State.IDLE;
            }
            return;
        }
        state = State.ROUTING;
        try {
            super.writePage();
        } finally {
            state = State.IDLE;
            labels.clear();
        }
        PageStats stats = stats();
        String rejection = gate(stats);
        if (rejection == null) {
            pagesTagged++;
            pageTagged = true;
            pendingPageStart = false;
            state = State.TAGGED;
            try {
                emitTagged();
            } catch (SAXException e) {
                throw new IOException("Unable to write tagged page", e);
            } finally {
                state = State.IDLE;
                events.clear();
            }
        } else {
            pagesFallback++;
            metadata.add(PDF.MARKED_CONTENT_REJECTIONS, getCurrentPageNo() + ":" + rejection);
            state = State.FLAT;
            try {
                flushPendingPageStart();
                replayFlat();
            } finally {
                state = State.IDLE;
                events.clear();
            }
        }
    }

    @Override
    protected void endDocument(PDDocument pdf) throws IOException {
        metadata.set(PDF.MARKED_CONTENT_PAGES_TAGGED, pagesTagged);
        metadata.set(PDF.MARKED_CONTENT_PAGES_FALLBACK, pagesFallback);
        super.endDocument(pdf);
    }

    // ---- the stripper's write calls: recorded while routing, PDF2XHTML's otherwise ----

    @Override
    protected void writeParagraphStart() throws IOException {
        if (state == State.ROUTING) {
            events.add(Event.PARA_START);
        } else if (inStartPage) {
            // a tagged page does not want startPage's <p>; decided once the page is assembled
            pendingPageStart = true;
        } else if (state != State.TAGGED) {
            super.writeParagraphStart();
        }
    }

    @Override
    protected void writeParagraphEnd() throws IOException {
        if (state == State.ROUTING) {
            events.add(Event.PARA_END);
        } else if (state == State.IDLE && pageTagged) {
            // endPage's close of startPage's <p>, which a tagged page never opened
            return;
        } else if (state != State.TAGGED) {
            flushPendingPageStart();
            super.writeParagraphEnd();
        }
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions)
            throws IOException {
        if (state != State.ROUTING) {
            super.writeString(text, textPositions);
            return;
        }
        int n = textPositions == null ? 0 : textPositions.size();
        Label first = n == 0 ? Label.UNTAGGED :
                labels.getOrDefault(textPositions.get(0), Label.UNTAGGED);
        boolean straddled = false;
        for (int i = 1; i < n && !straddled; i++) {
            straddled = !first.equals(labels.getOrDefault(textPositions.get(i), Label.UNTAGGED));
        }
        List<Segment> segments = new ArrayList<>(1);
        if (!straddled) {
            segments.add(new Segment(first, text, n));
        } else if (!text.equals(rawText(textPositions))) {
            // the stripper reordered or normalized this word (bidi, presentation forms), so
            // its glyphs no longer map onto the text: keep it whole under the label that
            // drew most of it
            segments.add(new Segment(majorityLabel(textPositions), text, n));
        } else {
            // a word drawn across MCIDs (cells separated by space glyphs, a link inside a
            // sentence): split it by glyph so each part lands in its own element
            Label label = first;
            StringBuilder part = new StringBuilder();
            int count = 0;
            for (TextPosition position : textPositions) {
                Label l = labels.getOrDefault(position, Label.UNTAGGED);
                if (!l.equals(label)) {
                    segments.add(new Segment(label, part.toString(), count));
                    label = l;
                    part.setLength(0);
                    count = 0;
                }
                if (position.getUnicode() != null) {
                    part.append(position.getUnicode());
                }
                count++;
            }
            segments.add(new Segment(label, part.toString(), count));
        }
        events.add(new Event(EventKind.WORD, text, segments));
    }

    private static String rawText(List<TextPosition> textPositions) {
        StringBuilder sb = new StringBuilder();
        for (TextPosition position : textPositions) {
            if (position.getUnicode() != null) {
                sb.append(position.getUnicode());
            }
        }
        return sb.toString();
    }

    private Label majorityLabel(List<TextPosition> textPositions) {
        Map<Label, Integer> counts = new HashMap<>();
        Label best = Label.UNTAGGED;
        int bestCount = 0;
        for (TextPosition position : textPositions) {
            Label l = labels.getOrDefault(position, Label.UNTAGGED);
            int c = counts.merge(l, 1, Integer::sum);
            if (c > bestCount) {
                bestCount = c;
                best = l;
            }
        }
        return best;
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        if (state == State.ROUTING) {
            events.add(Event.WORD_SEP);
        } else {
            super.writeWordSeparator();
        }
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        if (state == State.ROUTING) {
            events.add(Event.LINE_SEP);
        } else {
            super.writeLineSeparator();
        }
    }

    private void flushPendingPageStart() throws IOException {
        if (pendingPageStart) {
            pendingPageStart = false;
            super.writeParagraphStart();
        }
    }

    /** The same virtual calls in the same order that PDF2XHTML would have made. */
    private void replayFlat() throws IOException {
        for (Event event : events) {
            switch (event.kind) {
                case PARA_START:
                    super.writeParagraphStart();
                    break;
                case PARA_END:
                    super.writeParagraphEnd();
                    break;
                case WORD:
                    super.writeString(event.text);
                    break;
                case WORD_SEP:
                    super.writeWordSeparator();
                    break;
                case LINE_SEP:
                    super.writeLineSeparator();
                    break;
                default:
                    break;
            }
        }
    }

    // ---- the per-page gate ----

    private PageStats stats() {
        PageStats stats = new PageStats();
        for (Event event : events) {
            if (event.kind != EventKind.WORD) {
                continue;
            }
            for (Segment segment : event.segments) {
                StructureIndex.Leaf leaf = segment.label.tagged() ?
                        index.leaf(segment.label.scope, segment.label.mcid) : null;
                if (segment.label == Label.ARTIFACT) {
                    stats.artifact += segment.positions;
                } else if (leaf != null) {
                    stats.tagged += segment.positions;
                    if (wrapperTag(blockOf(leaf.node)) == null) {
                        stats.inBlock += segment.positions;
                    }
                } else {
                    stats.untagged += segment.positions;
                }
            }
        }
        for (COSBase scope : pageScopes) {
            for (Map.Entry<Integer, StructureIndex.Leaf> e : index.leaves(scope).entrySet()) {
                stats.leaves++;
                if (!openedMcids.contains(new Label(scope, e.getKey()))) {
                    stats.dangling++;
                }
            }
        }
        return stats;
    }

    /** Null to use the tree on this page, else why not. */
    private String gate(PageStats stats) {
        if (stats.tagged == 0) {
            return "no-tagged-text";
        }
        if (markedContentConfig.getStrategy() == MarkedContentConfig.Strategy.TAGS) {
            return null;
        }
        // a tree of bare spans or containers gives the page no paragraphs; the stripper's are
        // better
        if (stats.inBlock == 0) {
            return "no-block-structure";
        }
        // artifacts count: a producer that marks the body /Artifact and tags fragments has
        // not described the page, and the stripper does better with it
        float coverage = (float) stats.tagged / (stats.tagged + stats.untagged + stats.artifact);
        if (coverage < markedContentConfig.getMinCoverage()) {
            return "coverage=" + String.format(Locale.ROOT, "%.2f", coverage);
        }
        if (stats.leaves > 0) {
            float dangling = (float) stats.dangling / stats.leaves;
            if (dangling > markedContentConfig.getMaxDanglingRatio()) {
                return "dangling=" + String.format(Locale.ROOT, "%.2f", dangling);
            }
        }
        return null;
    }

    // ---- tagged emission ----

    /**
     * Blocks (paragraphs, headings, cells) go in tree order, since that is the reading order a
     * tagged PDF promises; the runs inside one block go in content order, so inline elements
     * (a Span or Link inside a P) keep their place in the sentence.
     */
    private void emitTagged() throws SAXException {
        List<Run> runs = runs();
        Map<StructureIndex.Node, BlockGroup> groups = new IdentityHashMap<>();
        List<BlockGroup> ordered = new ArrayList<>();
        List<Run> untagged = new ArrayList<>();
        List<Run> artifacts = new ArrayList<>();
        for (Run run : runs) {
            if (run.label == Label.ARTIFACT) {
                artifacts.add(run);
                continue;
            }
            StructureIndex.Leaf leaf =
                    run.label.tagged() ? index.leaf(run.label.scope, run.label.mcid) : null;
            if (leaf == null) {
                untagged.add(run);
                continue;
            }
            group(groups, ordered, leaf).add(run, leaf);
        }
        // a figure's image has no words; its element still takes its place, with its /Alt
        for (COSBase scope : pageScopes) {
            for (StructureIndex.Leaf leaf : index.objectLeaves(scope)) {
                group(groups, ordered, leaf).add(null, leaf);
            }
        }
        ordered.sort((a, b) -> Integer.compare(a.firstLeafOrder, b.firstLeafOrder));
        // loose text (no block above it) that nothing else interrupts is one stretch of text
        List<BlockGroup> merged = new ArrayList<>(ordered.size());
        for (BlockGroup group : ordered) {
            BlockGroup last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.loose && group.loose && last.root() == group.root()) {
                last.absorb(group);
            } else {
                merged.add(group);
            }
        }
        ordered = merged;

        List<StructureIndex.Node> path = new ArrayList<>();
        open.clear();
        openState.clear();
        altWritten.clear();
        held.clear();
        atBlockStart = false;
        for (BlockGroup group : ordered) {
            if (!group.hasText && !group.hasObject && !inTableCell(group.block)) {
                continue;
            }
            Wrapper wrapper = group.hasText ? wrapperFor(group.block) : null;
            Run previous = null;
            for (int r = 0; r < group.runs.size(); r++) {
                path.clear();
                for (StructureIndex.Node n = group.leaves.get(r).node; n != null; n = n.parent) {
                    path.add(n);
                }
                Collections.reverse(path);
                int common = 0;
                while (common < open.size() && common < path.size()
                        && open.get(common) == path.get(common)) {
                    common++;
                }
                boolean[] writable = writable(path);
                // a paragraph passed over on the way to a block inside it, now reached for its
                // own text: reopened, as HTML reopens it after the block
                for (int i = 0; i < common; i++) {
                    if (openState.get(i) == NOT_WRITTEN && writable[i]) {
                        common = i;
                        break;
                    }
                }
                closeOpenFrom(common, wrapper);
                if (wrapper != null) {
                    wrapper.at = wrapperStart(path, group.block, wrapper.inside);
                }
                boolean[] write = withinBudget(path, common, writable);
                for (int i = common; i < path.size(); i++) {
                    if (wrapper != null && !wrapper.open && i >= wrapper.at && write[i - common]) {
                        openWrapper(wrapper);
                    }
                    boolean wrote = write[i - common] && openElement(path.get(i));
                    open.add(path.get(i));
                    openState.add(wrote ? OPEN : writable[i] ? DROPPED : NOT_WRITTEN);
                }
                Run run = group.runs.get(r);
                if (run == null) {
                    continue;
                }
                // other content sat between these two runs; the separator went with it
                if (previous != null && run.ordinal != previous.ordinal + 1
                        && !previous.endsWithSeparator() && (wrapper == null || wrapper.open)) {
                    sinkText(" ");
                }
                previous = run;
                writePieces(run.pieces, wrapper);
            }
            if (wrapper != null) {
                // the block's other children are not the wrapper's
                closeOpenFrom(wrapper.at, wrapper);
                if (wrapper.open) {
                    closeWrapper(wrapper);
                }
            }
        }
        closeOpenFrom(0, null);
        held.clear();
        emitBlocks("untagged", untagged);
        emitBlocks("artifact", artifacts);
    }

    /** Closes the open path from the innermost node down to index {@code from}. */
    private void closeOpenFrom(int from, Wrapper wrapper) throws SAXException {
        for (int i = open.size() - 1; i >= from; i--) {
            // leaving the block ends its paragraph; loose text's paragraph ends on its own
            if (wrapper != null && wrapper.open && wrapper.inside && i == wrapper.at - 1) {
                closeWrapper(wrapper);
            }
            int state = openState.remove(i);
            if (state == OPEN) {
                close(open.get(i));
            } else if (state == SUSPENDED) {
                closeOuter(open.get(i));
            }
            open.remove(i);
        }
        if (wrapper != null && wrapper.open && wrapper.breakPending
                && !writtenInside(wrapper.at)) {
            closeWrapper(wrapper);
        }
    }

    /** Whether an element is open below path index {@code at}. */
    private boolean writtenInside(int at) {
        for (int i = at; i < openState.size(); i++) {
            if (openState.get(i) == OPEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * A block cannot sit in a paragraph or heading: as an HTML parser would, the open one ends
     * before the block starts. Its text was all written already, since a block's runs are
     * written together, so it is never reopened.
     */
    private void suspendTextBlock() throws SAXException {
        for (int i = open.size() - 1; i >= 0; i--) {
            if (openState.get(i) != OPEN) {
                continue;
            }
            ElementSpec spec = spec(open.get(i));
            if (spec.inline) {
                continue;
            }
            if (isTextBlock(spec.tag)) {
                sinkClose(spec.tag, false);
                openState.set(i, SUSPENDED);
            }
            return;
        }
    }

    /** A demoted block ends when a block or another demoted block opens inside it. */
    private void suspendDemoted() throws SAXException {
        for (int i = open.size() - 1; i >= 0; i--) {
            if (openState.get(i) != OPEN) {
                continue;
            }
            ElementSpec spec = spec(open.get(i));
            if (spec.demoted) {
                sinkClose(spec.tag, true);
                sinkBoundary();
                openState.set(i, SUSPENDED);
                return;
            }
            if (!spec.inline) {
                return;
            }
        }
    }

    private static boolean isTextBlock(String tag) {
        return "p".equals(tag) || tag.length() == 2 && tag.charAt(0) == 'h';
    }

    private void openWrapper(Wrapper wrapper) throws SAXException {
        sinkOpen(wrapper.tag, null, false);
        wrapper.open = true;
        wrapper.breakPending = false;
    }

    private void closeWrapper(Wrapper wrapper) throws SAXException {
        sinkClose(wrapper.tag, false);
        wrapper.open = false;
        wrapper.breakPending = false;
    }

    /**
     * Where on the path the wrapper's content begins: right inside the block, or, when nothing
     * on the path is a block, at the first element on it, so a link or span around loose text
     * sits inside the paragraph rather than around it.
     */
    private int wrapperStart(List<StructureIndex.Node> path, StructureIndex.Node block,
                             boolean inside) {
        if (inside) {
            return path.indexOf(block) + 1;
        }
        for (int i = 0; i < path.size(); i++) {
            if (spec(path.get(i)).tag != null) {
                return i;
            }
        }
        return path.size() - 1;
    }

    private Wrapper wrapperFor(StructureIndex.Node block) {
        String tag = wrapperTag(block);
        if (tag == null) {
            return null;
        }
        ElementSpec spec = spec(block);
        return new Wrapper(tag, spec.tag != null && !spec.inline);
    }

    /** The wrapper text straight under this block needs, or null when the block holds text. */
    private String wrapperTag(StructureIndex.Node block) {
        ElementSpec spec = spec(block);
        if (spec.tag == null || spec.inline) {
            // nothing above the leaf's own element is a block: the page division holds it
            return "p";
        }
        switch (spec.tag) {
            case "div":
                return "p";
            case "table":
            case "thead":
            case "tbody":
            case "tfoot":
                return "caption";
            case "tr":
                return "td";
            case "ul":
            case "ol":
                return "li";
            default:
                return null;
        }
    }

    // ---- the text sink: block edges trimmed, separators kept outside inline elements ----

    private void sinkText(String text) throws SAXException {
        String s = text;
        if (atBlockStart) {
            int start = 0;
            while (start < s.length() && Character.isWhitespace(s.charAt(start))) {
                start++;
            }
            if (start == s.length()) {
                return;
            }
            s = s.substring(start);
            atBlockStart = false;
        }
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            held.add(s);
            return;
        }
        flushHeld();
        xhtml.characters(s.substring(0, end));
        if (end < s.length()) {
            held.add(s.substring(end));
        }
    }

    private void sinkNewline() {
        if (!atBlockStart) {
            held.add(NEWLINE);
        }
    }

    /** The edge of a block written inline: a newline, unless one is already waiting. */
    private void sinkBoundary() {
        if (held.isEmpty() || held.get(held.size() - 1) != NEWLINE) {
            sinkNewline();
        }
    }

    private void flushHeld() throws SAXException {
        for (Object piece : held) {
            if (piece == NEWLINE) {
                xhtml.newline();
            } else {
                xhtml.characters((String) piece);
            }
        }
        held.clear();
    }

    private void sinkOpen(String tag, AttributesImpl attributes, boolean inline)
            throws SAXException {
        // whitespace before a child element separates it from the text before it
        flushHeld();
        if (attributes == null) {
            xhtml.startElement(tag);
        } else {
            xhtml.startElement(tag, attributes);
        }
        if (!inline) {
            atBlockStart = true;
        }
    }

    private void sinkClose(String tag, boolean inline) throws SAXException {
        if (!inline) {
            held.clear();
            atBlockStart = false;
        }
        xhtml.endElement(tag);
        if ("caption".equals(tag)) {
            // XHTMLContentHandler ends lines after p, li, tr... but not after a caption
            xhtml.newline();
        }
    }

    /**
     * Which nodes on the path write an element, before the nesting budget: those with an
     * element, except a paragraph or heading that a block further down the path would close
     * at once (nothing to say, so not written rather than written empty) and a demoted block
     * with a block or another demoted block below it.
     */
    private boolean[] writable(List<StructureIndex.Node> path) {
        boolean[] writable = new boolean[path.size()];
        boolean blockBelow = false;
        boolean demotedBelow = false;
        for (int i = path.size() - 1; i >= 0; i--) {
            ElementSpec spec = spec(path.get(i));
            writable[i] = spec.tag != null;
            if (spec.tag == null) {
                continue;
            }
            if (spec.demoted) {
                if (blockBelow || demotedBelow) {
                    writable[i] = false;
                }
                demotedBelow = true;
            } else if (!spec.inline) {
                if (blockBelow && isTextBlock(spec.tag)) {
                    writable[i] = false;
                }
                blockBelow = true;
            }
        }
        return writable;
    }

    /**
     * Which of the nodes about to open (path from {@code from}) may write an element without
     * exceeding {@link #MAX_OPEN_ELEMENTS} together with the elements already open. Grouping
     * containers give way first, outermost first; then anything else, outermost first.
     */
    private boolean[] withinBudget(List<StructureIndex.Node> path, int from, boolean[] writable) {
        boolean[] write = Arrays.copyOfRange(writable, from, path.size());
        int count = 0;
        boolean textBlockOpen = false;
        boolean demotedOpen = false;
        for (int i = 0; i < from; i++) {
            if (openState.get(i) == OPEN) {
                ElementSpec spec = spec(open.get(i));
                count += spec.elements();
                if (spec.demoted) {
                    demotedOpen = true;
                } else if (!spec.inline) {
                    textBlockOpen = isTextBlock(spec.tag);
                }
            }
        }
        for (int i = from; i < path.size(); i++) {
            if (!write[i - from]) {
                continue;
            }
            ElementSpec spec = spec(path.get(i));
            count += spec.elements();
            if (spec.demoted || !spec.inline) {
                if (demotedOpen) {
                    count--;
                    demotedOpen = false;
                }
            }
            if (!spec.inline) {
                if (textBlockOpen) {
                    count--;
                }
                textBlockOpen = isTextBlock(spec.tag);
            }
        }
        for (int pass = 0; pass < 2 && count > MAX_OPEN_ELEMENTS; pass++) {
            for (int i = from; i < path.size() && count > MAX_OPEN_ELEMENTS; i++) {
                if (write[i - from] && (pass == 1 || GROUPING_TYPES.contains(path.get(i).type)
                        || !StructureIndex.isStandardType(path.get(i).type))) {
                    write[i - from] = false;
                    count -= spec(path.get(i)).elements();
                }
            }
        }
        return write;
    }

    private BlockGroup group(Map<StructureIndex.Node, BlockGroup> groups, List<BlockGroup> ordered,
                             StructureIndex.Leaf leaf) {
        StructureIndex.Node block = blockOf(leaf.node);
        BlockGroup group = groups.get(block);
        if (group == null) {
            ElementSpec spec = spec(block);
            group = new BlockGroup(block, spec.tag == null || spec.inline);
            groups.put(block, group);
            ordered.add(group);
        }
        return group;
    }

    /** The runs under one block-level element, in content order; a null run is an object. */
    private static final class BlockGroup {
        final StructureIndex.Node block;
        /** No element above the text is a block: the block is the leaf's own node. */
        final boolean loose;
        final List<Run> runs = new ArrayList<>();
        final List<StructureIndex.Leaf> leaves = new ArrayList<>();
        int firstLeafOrder = Integer.MAX_VALUE;
        boolean hasText;
        boolean hasObject;

        BlockGroup(StructureIndex.Node block, boolean loose) {
            this.block = block;
            this.loose = loose;
        }

        StructureIndex.Node root() {
            StructureIndex.Node n = block;
            while (n.parent != null) {
                n = n.parent;
            }
            return n;
        }

        /** Takes another group's runs, keeping content order. */
        void absorb(BlockGroup other) {
            int i = 0;
            for (int j = 0; j < other.runs.size(); j++) {
                Run run = other.runs.get(j);
                if (run == null) {
                    i = runs.size();
                } else {
                    while (i < runs.size() && runs.get(i) != null
                            && runs.get(i).ordinal < run.ordinal) {
                        i++;
                    }
                }
                runs.add(i, run);
                leaves.add(i, other.leaves.get(j));
                i++;
            }
            hasText |= other.hasText;
            hasObject |= other.hasObject;
        }

        void add(Run run, StructureIndex.Leaf leaf) {
            runs.add(run);
            leaves.add(leaf);
            firstLeafOrder = Math.min(firstLeafOrder, leaf.order);
            if (run == null) {
                hasObject = true;
            } else {
                hasText |= run.hasText;
            }
        }
    }

    /** The nearest element written as a block; the leaf's own element if none is. */
    private StructureIndex.Node blockOf(StructureIndex.Node leafNode) {
        for (StructureIndex.Node n = leafNode; n != null; n = n.parent) {
            ElementSpec spec = spec(n);
            if (spec.tag != null && !spec.inline) {
                return n;
            }
        }
        return leafNode;
    }

    private static boolean inTableCell(StructureIndex.Node node) {
        for (StructureIndex.Node n = node; n != null; n = n.parent) {
            if ("TD".equals(n.type) || "TH".equals(n.type)) {
                return true;
            }
        }
        return false;
    }

    private List<Run> runs() {
        List<Run> runs = new ArrayList<>();
        Run current = null;
        for (Event event : events) {
            switch (event.kind) {
                case WORD:
                    for (Segment segment : event.segments) {
                        if (current == null || !current.label.equals(segment.label)) {
                            current = new Run(segment.label, runs.size());
                            runs.add(current);
                        }
                        current.pieces.add(segment.text);
                        if (!segment.text.isBlank()) {
                            current.hasText = true;
                        }
                    }
                    break;
                case WORD_SEP:
                    if (current != null) {
                        current.pieces.add(getWordSeparator());
                    }
                    break;
                case LINE_SEP:
                    if (current != null) {
                        current.pieces.add(NEWLINE);
                    }
                    break;
                case PARA_START:
                case PARA_END:
                    if (current != null) {
                        current.pieces.add(PARAGRAPH_BREAK);
                    }
                    break;
                default:
                    break;
            }
        }
        return runs;
    }

    private void writePieces(List<Object> pieces, Wrapper wrapper) throws SAXException {
        for (Object piece : pieces) {
            if (piece == PARAGRAPH_BREAK) {
                if (wrapper != null && wrapper.open) {
                    if (!"p".equals(wrapper.tag)) {
                        // one caption, cell or item holds all the text, whatever the breaks
                        sinkNewline();
                    } else if (writtenInside(wrapper.at)) {
                        wrapper.breakPending = true;
                    } else {
                        closeWrapper(wrapper);
                    }
                }
            } else if (piece == NEWLINE) {
                if (wrapper == null || wrapper.open) {
                    sinkNewline();
                }
            } else {
                String text = (String) piece;
                if (wrapper != null && !wrapper.open) {
                    if (text.isBlank()) {
                        continue;
                    }
                    openWrapper(wrapper);
                }
                sinkText(text);
            }
        }
    }

    /** Text the tree does not account for, as paragraphs the stripper's breaks delimit. */
    private void emitBlocks(String cls, List<Run> runs) throws SAXException {
        boolean any = false;
        for (Run run : runs) {
            any |= run.hasText;
        }
        if (!any) {
            return;
        }
        AttributesImpl divAttrs = new AttributesImpl();
        addAttribute(divAttrs, "class", cls);
        sinkOpen("div", divAttrs, false);
        boolean inParagraph = false;
        Run previous = null;
        for (Run run : runs) {
            // a run of only a space glyph still separates its neighbours, inside a paragraph
            if (!run.hasText && !inParagraph) {
                continue;
            }
            if (inParagraph && previous != null && run.ordinal != previous.ordinal + 1
                    && !previous.endsWithSeparator()) {
                sinkText(" ");
            }
            previous = run;
            for (Object piece : run.pieces) {
                if (piece == PARAGRAPH_BREAK) {
                    if (inParagraph) {
                        sinkClose("p", false);
                        inParagraph = false;
                    }
                    continue;
                }
                if (!inParagraph) {
                    sinkOpen("p", null, false);
                    inParagraph = true;
                }
                if (piece == NEWLINE) {
                    sinkNewline();
                } else {
                    sinkText((String) piece);
                }
            }
        }
        if (inParagraph) {
            sinkClose("p", false);
        }
        sinkClose("div", false);
    }

    /** Writes the node's element and returns whether one was written. */
    private boolean openElement(StructureIndex.Node node) throws SAXException {
        ElementSpec spec = spec(node);
        if (spec.tag == null) {
            return false;
        }
        if (spec.demoted || !spec.inline) {
            suspendDemoted();
        }
        if (!spec.inline) {
            suspendTextBlock();
        }
        if (spec.outer != null) {
            sinkOpen(spec.outer, null, false);
        }
        if (spec.demoted) {
            sinkBoundary();
        }
        sinkOpen(spec.tag, spec.attributes, spec.inline);
        // the accessibility text of an image is its text, once per page
        if (spec.alt != null && altWritten.add(node)) {
            sinkText(spec.alt);
            sinkNewline();
        }
        return true;
    }

    private void close(StructureIndex.Node node) throws SAXException {
        ElementSpec spec = spec(node);
        if (spec.tag != null) {
            sinkClose(spec.tag, spec.inline);
            if (spec.demoted) {
                sinkBoundary();
            }
            closeOuter(node);
        }
    }

    private void closeOuter(StructureIndex.Node node) throws SAXException {
        ElementSpec spec = spec(node);
        if (spec.outer != null) {
            sinkClose(spec.outer, false);
        }
    }

    // ---- structure type -> XHTML ----

    /** Builds missing specs from the topmost ancestor down, so a deep chain never recurses. */
    private ElementSpec spec(StructureIndex.Node node) {
        if (node.spec != null) {
            return node.spec;
        }
        List<StructureIndex.Node> chain = new ArrayList<>();
        for (StructureIndex.Node n = node; n != null && n.spec == null; n = n.parent) {
            chain.add(n);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            chain.get(i).spec = buildSpec(chain.get(i));
        }
        return node.spec;
    }

    private ElementSpec buildSpec(StructureIndex.Node node) {
        try {
            return buildSpec(node, new AttributesImpl());
        } catch (RuntimeException e) {
            // PDFBox's attribute model casts what it finds (PDFBOX: /Headers holding strings)
            return buildSpec(node, null);
        }
    }

    private ElementSpec buildSpec(StructureIndex.Node node, AttributesImpl attrs) {
        ElementSpec spec = mapType(node, attrs);
        if (spec.tag == null) {
            return spec;
        }
        // a block inside a link or span is inline, so the link stays one; inside a table or
        // row it is inline too, and its text takes the caption or cell the wrapper gives it;
        // tables and lists keep their structure, and a block inside a paragraph or heading
        // closes it instead (see suspendTextBlock)
        if (!spec.inline && !STRUCTURAL_TAGS.contains(spec.tag)
                && (inlineContext(node) || tableContext(node))) {
            spec = spec.asSpan();
        }
        // everything straight under a list sits in an item; an item outside a list and a row
        // outside a table get the container the tree left out
        StructureIndex.Node container = writtenAncestor(node);
        String containerTag = container == null ? null : container.spec.tag;
        boolean inList = "ul".equals(containerTag) || "ol".equals(containerTag);
        boolean inTable = containerTag != null && TABLE_TAGS.contains(containerTag)
                && !"tr".equals(containerTag);
        if (inList && !"li".equals(spec.tag)) {
            spec = spec.within("li");
        } else if ("li".equals(spec.tag) && !inList) {
            spec = spec.within("ul");
        } else if ("tr".equals(spec.tag) && !inTable) {
            spec = spec.within("table");
        }
        return spec;
    }

    /** The nearest ancestor written as an element; ancestors' specs exist, built top-down. */
    private static StructureIndex.Node writtenAncestor(StructureIndex.Node node) {
        for (StructureIndex.Node p = node.parent; p != null; p = p.parent) {
            if (p.spec != null && p.spec.tag != null) {
                return p;
            }
        }
        return null;
    }

    private ElementSpec mapType(StructureIndex.Node node, AttributesImpl attrs) {
        String type = node.type;
        PDStructureElement element = new PDStructureElement(node.dict);
        boolean readAttributes = attrs != null;
        if (attrs == null) {
            attrs = new AttributesImpl();
        }
        addAttribute(attrs, "lang", element.getLanguage());
        String title = element.getTitle();
        addAttribute(attrs, "title", title == null ? element.getExpandedForm() : title);
        // element-level /ActualText is not written: some producers put it on every Span, where
        // it repeats the text; the marked-content form is applied by the stripper already
        switch (type) {
            case "Document":
            case "NonStruct":
            case "LBody":
            case "Sub":
                return ElementSpec.TRANSPARENT;
            case "Span":
                // a Span with nothing to say is transparent; one with a language or title is not
                return attrs.getLength() == 0 ? ElementSpec.TRANSPARENT :
                        new ElementSpec("span", attrs, true);
            case "P":
                return block("p", attrs);
            case "H":
                return block("h" + Math.min(6, 1 + node.countAncestors("Sect")), attrs);
            case "H1":
            case "H2":
            case "H3":
            case "H4":
            case "H5":
            case "H6":
                return block(type.toLowerCase(Locale.ROOT), attrs);
            case "Title":
                addAttribute(attrs, "class", "title");
                return block("h1", attrs);
            case "BlockQuote":
                return block("blockquote", attrs);
            case "Caption": {
                StructureIndex.Node a = writtenAncestor(node);
                if (a != null && "table".equals(a.spec.tag)) {
                    return block("caption", attrs);
                }
                break;
            }
            case "TOC":
                addAttribute(attrs, "class", "toc");
                return block("ul", attrs);
            case "TOCI":
            case "LI":
                return block("li", attrs);
            case "L":
                return block(readAttributes && orderedList(node) ? "ol" : "ul", attrs);
            case "Table":
                if (readAttributes) {
                    tableAttributes(node, attrs);
                }
                return block("table", attrs);
            case "TR":
            case "THead":
            case "TBody":
            case "TFoot":
                return block(type.toLowerCase(Locale.ROOT), attrs);
            case "TH":
            case "TD":
                if (readAttributes) {
                    cellAttributes(node, attrs, "TH".equals(type));
                }
                return block(type.toLowerCase(Locale.ROOT), attrs);
            case "Quote":
                return new ElementSpec("q", attrs, true);
            case "Code":
                return new ElementSpec("code", attrs, true);
            case "Em":
            case "Strong":
            case "Ruby":
            case "RB":
            case "RT":
            case "RP":
                return new ElementSpec(type.toLowerCase(Locale.ROOT), attrs, true);
            case "Link": {
                String uri = StructureIndex.linkUri(node.objr);
                if (uri != null) {
                    addAttribute(attrs, "href", uri);
                    return new ElementSpec("a", attrs, true);
                }
                addAttribute(attrs, "class", "link");
                return new ElementSpec("span", attrs, true);
            }
            case "Lbl":
            case "Reference":
            case "Annot":
            case "Warichu":
            case "WT":
            case "WP":
                addAttribute(attrs, "class", type.toLowerCase(Locale.ROOT));
                return new ElementSpec("span", attrs, true);
            case "Figure":
            case "Formula": {
                String alt = element.getAlternateDescription();
                addAttribute(attrs, "alt", alt);
                addAttribute(attrs, "class", type.toLowerCase(Locale.ROOT));
                return new ElementSpec("div", attrs, false,
                        alt == null || alt.isBlank() ? null : alt.trim(), null, false);
            }
            default:
                break;
        }
        String cls = className(type);
        if (StructureIndex.isStandardType(type) || !inlineContext(node)) {
            addAttribute(attrs, "class", cls);
            return block("div", attrs);
        }
        addAttribute(attrs, "class", cls);
        return new ElementSpec("span", attrs, true);
    }

    private static ElementSpec block(String tag, AttributesImpl attrs) {
        return new ElementSpec(tag, attrs.getLength() == 0 ? null : attrs, false);
    }

    /** True when the nearest written ancestor is an inline element. */
    private static boolean inlineContext(StructureIndex.Node node) {
        StructureIndex.Node a = writtenAncestor(node);
        return a != null && a.spec.inline;
    }

    /** True when the nearest written ancestor is a table, a table section or a row. */
    private static boolean tableContext(StructureIndex.Node node) {
        StructureIndex.Node a = writtenAncestor(node);
        return a != null && TABLE_TAGS.contains(a.spec.tag);
    }

    private static boolean orderedList(StructureIndex.Node node) {
        for (PDAttributeObject attribute : StructureIndex.attributes(node.dict)) {
            if (attribute instanceof PDListAttributeObject) {
                String numbering = ((PDListAttributeObject) attribute).getListNumbering();
                if (numbering != null && ORDERED_LIST_NUMBERING.contains(numbering)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void tableAttributes(StructureIndex.Node node, AttributesImpl attrs) {
        for (PDAttributeObject attribute : StructureIndex.attributes(node.dict)) {
            if (attribute instanceof PDTableAttributeObject) {
                addAttribute(attrs, "summary", ((PDTableAttributeObject) attribute).getSummary());
            }
        }
    }

    private static void cellAttributes(StructureIndex.Node node, AttributesImpl attrs,
                                       boolean header) {
        for (PDAttributeObject attribute : StructureIndex.attributes(node.dict)) {
            if (!(attribute instanceof PDTableAttributeObject)) {
                continue;
            }
            PDTableAttributeObject table = (PDTableAttributeObject) attribute;
            if (table.getRowSpan() > 1) {
                addAttribute(attrs, "rowspan", Integer.toString(table.getRowSpan()));
            }
            if (table.getColSpan() > 1) {
                addAttribute(attrs, "colspan", Integer.toString(table.getColSpan()));
            }
            String[] headers = getTableHeaders(table); //TODO table.getHeaders() with 3.0.9 release
            if (headers != null && headers.length > 0) {
                addAttribute(attrs, "headers", String.join(" ", headers));
            }
            if (header) {
                addAttribute(attrs, "scope", table.getScope());
            }
        }
    }

    //TODO remove this with 3.0.9 release (PDFBOX-6261)
    private static String[] getTableHeaders(PDTableAttributeObject table) {
        COSBase v = table.getCOSObject().getDictionaryObject("Headers");
        if (v instanceof COSArray) {
            COSArray array = (COSArray) v;
            String[] strings = new String[array.size()];
            for (int i = 0; i < array.size(); i++) {
                strings[i] = array.getString(i);
            }
            return strings;
        }
        return null;
    }

    private static void addAttribute(AttributesImpl attrs, String name, String value) {
        if (value == null || value.isBlank() || attrs.getIndex("", name) >= 0) {
            return;
        }
        attrs.addAttribute("", name, name, "CDATA", value);
    }

    private static String className(String type) {
        String cls = CLASS_NAME_CHARS.matcher(type.toLowerCase(Locale.ROOT)).replaceAll("_");
        if (cls.length() > MAX_CLASS_NAME) {
            cls = cls.substring(0, MAX_CLASS_NAME);
        }
        return cls.isEmpty() ? "unknown" : cls;
    }
}
