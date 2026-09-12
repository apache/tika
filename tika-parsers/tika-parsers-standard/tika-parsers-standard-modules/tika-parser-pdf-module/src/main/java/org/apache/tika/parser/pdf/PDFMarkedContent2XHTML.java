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
    private static final Object NEWLINE = new Object();
    private static final Object PARAGRAPH_BREAK = new Object();
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
        final List<Object> pieces = new ArrayList<>();
        boolean hasText;

        Run(Label label) {
            this.label = label;
        }
    }

    /** How a structure element is written: its element name (null for transparent) and attributes. */
    static final class ElementSpec {
        static final ElementSpec TRANSPARENT = new ElementSpec(null, null, false);

        final String tag;
        final AttributesImpl attributes;
        final boolean inline;

        ElementSpec(String tag, AttributesImpl attributes, boolean inline) {
            this.tag = tag;
            this.attributes = attributes;
            this.inline = inline;
        }
    }

    private static final class PageStats {
        int tagged;
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
                if (segment.label == Label.ARTIFACT) {
                    stats.artifact += segment.positions;
                } else if (segment.label.tagged()
                        && index.leaf(segment.label.scope, segment.label.mcid) != null) {
                    stats.tagged += segment.positions;
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
            StructureIndex.Node block = blockOf(leaf.node);
            BlockGroup group = groups.get(block);
            if (group == null) {
                group = new BlockGroup(block);
                groups.put(block, group);
                ordered.add(group);
            }
            group.add(run, leaf);
        }
        ordered.sort((a, b) -> Integer.compare(a.firstLeafOrder, b.firstLeafOrder));

        List<StructureIndex.Node> open = new ArrayList<>();
        List<StructureIndex.Node> path = new ArrayList<>();
        // the separators after a run's last word belong between elements, not inside a link
        List<Object> carried = new ArrayList<>();
        for (BlockGroup group : ordered) {
            if (!group.hasText && !inTableCell(group.block)) {
                continue;
            }
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
                for (int i = open.size() - 1; i >= common; i--) {
                    close(open.remove(i));
                }
                writePieces(carried);
                carried.clear();
                for (int i = common; i < path.size(); i++) {
                    openElement(path.get(i));
                    open.add(path.get(i));
                }
                List<Object> pieces = group.runs.get(r).pieces;
                int end = pieces.size();
                while (end > 0 && isSeparator(pieces.get(end - 1))) {
                    end--;
                }
                writePieces(pieces.subList(0, end));
                carried.addAll(pieces.subList(end, pieces.size()));
            }
        }
        for (int i = open.size() - 1; i >= 0; i--) {
            close(open.get(i));
        }
        writePieces(carried);
        emitBlocks("untagged", untagged);
        emitBlocks("artifact", artifacts);
    }

    private static boolean isSeparator(Object piece) {
        return piece == NEWLINE || piece == PARAGRAPH_BREAK || ((String) piece).isBlank();
    }

    /** The runs under one block-level element, in content order. */
    private static final class BlockGroup {
        final StructureIndex.Node block;
        final List<Run> runs = new ArrayList<>();
        final List<StructureIndex.Leaf> leaves = new ArrayList<>();
        int firstLeafOrder = Integer.MAX_VALUE;
        boolean hasText;

        BlockGroup(StructureIndex.Node block) {
            this.block = block;
        }

        void add(Run run, StructureIndex.Leaf leaf) {
            runs.add(run);
            leaves.add(leaf);
            firstLeafOrder = Math.min(firstLeafOrder, leaf.order);
            hasText |= run.hasText;
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
                            current = new Run(segment.label);
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

    private void writePieces(List<Object> pieces) throws SAXException {
        for (Object piece : pieces) {
            if (piece == NEWLINE) {
                xhtml.newline();
            } else if (piece != PARAGRAPH_BREAK) {
                xhtml.characters((String) piece);
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
        xhtml.startElement("div", "class", cls);
        boolean inParagraph = false;
        for (Run run : runs) {
            if (!run.hasText) {
                continue;
            }
            for (Object piece : run.pieces) {
                if (piece == PARAGRAPH_BREAK) {
                    if (inParagraph) {
                        xhtml.endElement("p");
                        inParagraph = false;
                    }
                    continue;
                }
                if (!inParagraph) {
                    xhtml.startElement("p");
                    inParagraph = true;
                }
                if (piece == NEWLINE) {
                    xhtml.newline();
                } else {
                    xhtml.characters((String) piece);
                }
            }
        }
        if (inParagraph) {
            xhtml.endElement("p");
        }
        xhtml.endElement("div");
    }

    private void openElement(StructureIndex.Node node) throws SAXException {
        ElementSpec spec = spec(node);
        if (spec.tag == null) {
            return;
        }
        if (spec.attributes == null) {
            xhtml.startElement(spec.tag);
        } else {
            xhtml.startElement(spec.tag, spec.attributes);
        }
    }

    private void close(StructureIndex.Node node) throws SAXException {
        ElementSpec spec = spec(node);
        if (spec.tag != null) {
            xhtml.endElement(spec.tag);
        }
    }

    // ---- structure type -> XHTML ----

    private ElementSpec spec(StructureIndex.Node node) {
        if (node.spec == null) {
            node.spec = buildSpec(node);
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
        String type = node.type;
        PDStructureElement element = new PDStructureElement(node.dict);
        boolean readAttributes = attrs != null;
        if (attrs == null) {
            attrs = new AttributesImpl();
        }
        addAttribute(attrs, "lang", element.getLanguage());
        String title = element.getTitle();
        addAttribute(attrs, "title", title == null ? element.getExpandedForm() : title);
        addAttribute(attrs, "actualtext", element.getActualText());
        switch (type) {
            case "Document":
            case "NonStruct":
            case "LBody":
            case "Sub":
                return ElementSpec.TRANSPARENT;
            case "Span":
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
            case "Formula":
                addAttribute(attrs, "alt", element.getAlternateDescription());
                addAttribute(attrs, "class", type.toLowerCase(Locale.ROOT));
                return block("div", attrs);
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

    /** True under a paragraph, heading or inline element, where a block would not belong. */
    private boolean inlineContext(StructureIndex.Node node) {
        for (StructureIndex.Node p = node.parent; p != null; p = p.parent) {
            if ("P".equals(p.type) || p.type.startsWith("H") && p.type.length() <= 2) {
                return true;
            }
            if (spec(p).inline) {
                return true;
            }
        }
        return false;
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
            String[] headers = table.getHeaders();
            if (headers != null && headers.length > 0) {
                addAttribute(attrs, "headers", String.join(" ", headers));
            }
            if (header) {
                addAttribute(attrs, "scope", table.getScope());
            }
        }
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
