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
package org.apache.tika.parser.odf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.ContentHandlerDecorator;

/**
 * Writes an ODF table cell or row that carries a repeat count
 * ({@code table:number-columns-repeated}, {@code table:number-rows-repeated}) once per
 * repeat.
 * <p>
 * ODF stores runs of identical cells or rows once with a count. One with content is
 * replayed. Empty ones, repeated or not, are held back and written only if a sibling
 * with content follows before the parent closes, so the filler that pads a sheet out to
 * its last column or row (often thousands of cells, a million rows) is dropped. Gaps
 * before content are written up to {@code maxEmptyWritten} empty elements per document:
 * cells pad out to the next value's column, as the Excel parsers do, and empty rows are
 * dropped unless asked for.
 * <p>
 * Padding and every replayed copy are charged to a shared {@link Budget} at one unit per
 * character and per element (each element writes a tab or newline).
 */
class RepeatedElementHandler extends ContentHandlerDecorator {

    /** Characters and elements a document may add by padding and replaying. */
    static final long MAX_REPLAYED_CHARS = 10_000_000L;

    /** Replay budget shared by the cell and row handlers of one document. */
    static final class Budget {
        private final Metadata metadata;
        private long replayedChars;
        private boolean warned;

        Budget(Metadata metadata) {
            this.metadata = metadata;
        }

        /** Whether {@code extra} more characters fit; warns once when they don't. */
        boolean take(long extra) {
            if (replayedChars + extra <= MAX_REPLAYED_CHARS) {
                replayedChars += extra;
                return true;
            }
            warn();
            return false;
        }

        /** Takes as much of {@code extra} as fits; warns once when not all of it does. */
        long takeUpTo(long extra) {
            long granted = Math.min(extra, MAX_REPLAYED_CHARS - replayedChars);
            replayedChars += granted;
            if (granted < extra) {
                warn();
            }
            return granted;
        }

        private void warn() {
            if (!warned && metadata != null) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "OpenDocument repeated cells/rows exceeded " + MAX_REPLAYED_CHARS
                                + " replayed characters; the rest were written once");
            }
            warned = true;
        }
    }

    /** A run of held-back empty elements sharing a start tag. */
    private static final class EmptyRun {
        final String uri;
        final String localName;
        final String name;
        final Attributes atts;
        int count;

        EmptyRun(String uri, String localName, String name, Attributes atts, int count) {
            this.uri = uri;
            this.localName = localName;
            this.name = name;
            this.atts = atts;
            this.count = count;
        }
    }

    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    private interface Event {
        void replay(ContentHandler h) throws SAXException;
    }

    private final ContentHandler out;
    private final String element;
    private final String parent;
    private final int maxRepeat;
    private long emptyLeft;
    private final String emptyChild;
    private final Budget budget;

    private int nextRepeat;
    private List<Event> buffer;
    private String bufferUri;
    private String bufferName;
    private Attributes bufferAtts;
    private int depth;
    private int repeat;
    private boolean hasContent;
    private long chars;
    private long elements;
    private final Deque<EmptyRun> pendingEmpty = new ArrayDeque<>();
    private long pendingCount;

    /**
     * @param element the XHTML element that repeats ({@code td} or {@code tr})
     * @param parent  the element whose end drops pending empty copies
     * @param maxEmptyWritten most empty elements written before content, per document
     * @param emptyChild      element written inside each empty one, or null
     */
    RepeatedElementHandler(ContentHandler out, String element, String parent, int maxRepeat,
                           long maxEmptyWritten, String emptyChild, Budget budget) {
        super(out);
        this.out = out;
        this.element = element;
        this.parent = parent;
        this.maxRepeat = maxRepeat;
        this.emptyLeft = maxEmptyWritten;
        this.emptyChild = emptyChild;
        this.budget = budget;
    }

    /** After a parse failure: writes a buffered element as far as it got, once. */
    void flushBuffered() throws SAXException {
        if (buffer != null) {
            List<Event> partial = buffer;
            buffer = null;
            for (Event e : partial) {
                e.replay(out);
            }
        }
    }

    Budget budget() {
        return budget;
    }

    /** Called before the start of an element whose repeat count is {@code n}. */
    void repeatNext(int n) {
        if (buffer == null) {
            nextRepeat = Math.min(n, maxRepeat);
        }
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes atts)
            throws SAXException {
        if (buffer != null) {
            depth++;
            elements++;
            if (localName.equals("img")) {
                hasContent = true;
            }
            AttributesImpl copy = new AttributesImpl(atts);
            buffer.add(h -> h.startElement(uri, localName, name, copy));
            return;
        }
        if (localName.equals(element)) {
            buffer = new ArrayList<>();
            depth = 0;
            repeat = Math.max(1, nextRepeat);
            hasContent = false;
            chars = 0;
            elements = 1;
            nextRepeat = 0;
            AttributesImpl copy = new AttributesImpl(atts);
            bufferUri = uri;
            bufferName = name;
            bufferAtts = copy;
            buffer.add(h -> h.startElement(uri, localName, name, copy));
            return;
        } else if (localName.equals(parent)) {
            clearPending();
        }
        nextRepeat = 0;
        super.startElement(uri, localName, name, atts);
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        if (buffer != null) {
            buffer.add(h -> h.endElement(uri, localName, name));
            if (depth-- > 0) {
                return;
            }
            List<Event> done = buffer;
            buffer = null;
            if (!hasContent) {
                hold(bufferUri, localName, bufferName, bufferAtts, repeat);
                return;
            }
            writePendingEmpty();
            int copies = repeat;
            if (copies > 1 && !budget.take((chars + elements) * (copies - 1))) {
                copies = 1;
            }
            for (int i = 0; i < copies; i++) {
                for (Event e : done) {
                    e.replay(out);
                }
            }
            return;
        }
        if (localName.equals(parent)) {
            clearPending();
        }
        super.endElement(uri, localName, name);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (buffer != null) {
            char[] copy = new char[length];
            System.arraycopy(ch, start, copy, 0, length);
            if (!hasContent) {
                for (char c : copy) {
                    if (!Character.isWhitespace(c)) {
                        hasContent = true;
                        break;
                    }
                }
            }
            chars += length;
            buffer.add(h -> h.characters(copy, 0, copy.length));
            return;
        }
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (buffer != null) {
            char[] copy = new char[length];
            System.arraycopy(ch, start, copy, 0, length);
            buffer.add(h -> h.ignorableWhitespace(copy, 0, copy.length));
            return;
        }
        super.ignorableWhitespace(ch, start, length);
    }

    private void hold(String uri, String localName, String name, Attributes atts, int count) {
        int n = (int) Math.min(count, Math.min(maxRepeat, emptyLeft) - pendingCount);
        if (n <= 0) {
            return;
        }
        EmptyRun last = pendingEmpty.peekLast();
        if (last != null && last.atts.getLength() == 0 && atts.getLength() == 0) {
            last.count += n;
        } else {
            pendingEmpty.addLast(new EmptyRun(uri, localName, name, atts, n));
        }
        pendingCount += n;
    }

    private void clearPending() {
        pendingEmpty.clear();
        pendingCount = 0;
    }

    private void writePendingEmpty() throws SAXException {
        int units = emptyChild == null ? 1 : 2;
        long n = budget.takeUpTo(pendingCount * units) / units;
        emptyLeft -= n;
        for (EmptyRun run : pendingEmpty) {
            for (int i = 0; i < run.count && n > 0; i++, n--) {
                super.startElement(run.uri, run.localName, run.name, run.atts);
                if (emptyChild != null) {
                    super.startElement(run.uri, emptyChild, emptyChild, EMPTY_ATTRIBUTES);
                    super.endElement(run.uri, emptyChild, emptyChild);
                }
                super.endElement(run.uri, run.localName, run.name);
            }
        }
        clearPending();
    }
}
