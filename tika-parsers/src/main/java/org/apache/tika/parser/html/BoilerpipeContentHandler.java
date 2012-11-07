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
package org.apache.tika.parser.html;

import java.io.Writer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.WriteOutContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import de.l3s.boilerpipe.BoilerpipeExtractor;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.sax.BoilerpipeHTMLContentHandler;

/**
 * Uses the <a href="http://code.google.com/p/boilerpipe/">boilerpipe</a>
 * library to automatically extract the main content from a web page.
 *
 * Use this as a {@link ContentHandler} object passed to
 * {@link HtmlParser#parse(java.io.InputStream, ContentHandler, Metadata, org.apache.tika.parser.ParseContext)}
 */
public class BoilerpipeContentHandler extends BoilerpipeHTMLContentHandler {

    private static class RecordedElement {
        public enum ElementType {
            START,
            END,
            CONTINUE
        }

        private String uri;
        private String localName;
        private String qName;
        private Attributes attrs;
        private List<char[]> characters;
        private ElementType elementType;

        public RecordedElement(String uri, String localName, String qName, Attributes attrs) {
            this(uri, localName, qName, attrs, ElementType.START);
        }

        public RecordedElement(String uri, String localName, String qName) {
            this(uri, localName, qName, null, ElementType.END);
        }

        public RecordedElement() {
            this(null, null, null, null, ElementType.CONTINUE);
        }

        protected RecordedElement(String uri, String localName, String qName, Attributes attrs, RecordedElement.ElementType elementType) {
            this.uri = uri;
            this.localName = localName;
            this.qName = qName;
            this.attrs = attrs;
            this.elementType = elementType;
            this.characters = new ArrayList<char[]>();
        }

        @Override
        public String toString() {
            return String.format("<%s> of type %s", localName, elementType);
        };

        public String getUri() {
            return uri;
        }

        public String getLocalName() {
            return localName;
        }

        public String getQName() {
            return qName;
        }

        public Attributes getAttrs() {
            return attrs;
        }

        public List<char[]> getCharacters() {
            return characters;
        }

        public RecordedElement.ElementType getElementType() {
            return elementType;
        }
    }

    /**
     * The newline character that gets inserted after block elements.
     */
    private static final char[] NL = new char[] { '\n' };

    private ContentHandler delegate;
    private BoilerpipeExtractor extractor;

    private boolean includeMarkup;
    private boolean inHeader;
    private boolean inFooter;
    private int headerCharOffset;
    private List<RecordedElement> elements;
    private TextDocument td;

    /**
     * Creates a new boilerpipe-based content extractor, using the
     * {@link DefaultExtractor} extraction rules and "delegate" as the content handler.
     *
     * @param delegate
     *            The {@link ContentHandler} object
     */
    public BoilerpipeContentHandler(ContentHandler delegate) {
        this(delegate, DefaultExtractor.INSTANCE);
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * the given writer.
     *
     * @param writer writer
     */
    public BoilerpipeContentHandler(Writer writer) {
        this(new WriteOutContentHandler(writer));
    }

    /**
     * Creates a new boilerpipe-based content extractor, using the given
     * extraction rules. The extracted main content will be passed to the
     * <delegate> content handler.
     *
     * @param delegate
     *            The {@link ContentHandler} object
     * @param extractor
     *            Extraction rules to use, e.g. {@link ArticleExtractor}
     */
    public BoilerpipeContentHandler(ContentHandler delegate, BoilerpipeExtractor extractor) {
        this.td = null;
        this.delegate = delegate;
        this.extractor = extractor;
    }

    public void setIncludeMarkup(boolean includeMarkup) {
        this.includeMarkup = includeMarkup;
    }

    public boolean isIncludeMarkup() {
        return includeMarkup;
    }

    /**
     * Retrieves the built TextDocument
     *
     * @return TextDocument
     */
    public TextDocument getTextDocument() {
        return td;
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();

        delegate.startDocument();

        inHeader = true;
        inFooter = false;
        headerCharOffset = 0;

        if (includeMarkup) {
            elements = new ArrayList<RecordedElement>();
        }
    };

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);
        delegate.startPrefixMapping(prefix, uri);
    };

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        super.startElement(uri, localName, qName, atts);

        if (inHeader) {
            delegate.startElement(uri, localName, qName, atts);
        } else if (inFooter) {
            // Do nothing
        } else if (includeMarkup) {
            elements.add(new RecordedElement(uri, localName, qName, atts));
        } else {
            // This happens for the <body> element, if we're not doing markup.
            delegate.startElement(uri, localName, qName, atts);
        }
    };

    @Override
    public void characters(char[] chars, int offset, int length) throws SAXException {
        super.characters(chars, offset, length);

        if (inHeader) {
            delegate.characters(chars, offset, length);
            headerCharOffset++;
        } else if (inFooter) {
            // Do nothing
        } else if (includeMarkup) {
            RecordedElement element = elements.get(elements.size() - 1);

            char[] characters = new char[length];
            System.arraycopy(chars, offset, characters, 0, length);
            element.getCharacters().add(characters);
        }
    };

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);

        if (inHeader) {
            delegate.endElement(uri, localName, qName);
            inHeader = !localName.equals("head");
        } else if (inFooter) {
            // Do nothing
        } else if (localName.equals("body")) {
            inFooter = true;
        } else if (includeMarkup) {
            // Add the end element, and the continuation from the previous element
            elements.add(new RecordedElement(uri, localName, qName));
            elements.add(new RecordedElement());
        }
    };

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        td = toTextDocument();
        try {
            extractor.process(td);
        } catch (BoilerpipeProcessingException e) {
            throw new SAXException(e);
        }

        Attributes emptyAttrs = new AttributesImpl();

        // At this point we have all the information we need to either emit N paragraphs
        // of plain text (if not including markup), or we have to replay our recorded elements
        // and only emit character runs that passed the boilerpipe filters.
        if (includeMarkup) {
            BitSet validCharacterRuns = new BitSet();
            for (TextBlock block : td.getTextBlocks()) {
                if (block.isContent()) {
                    BitSet bs = block.getContainedTextElements();
                    if (bs != null) {
                        validCharacterRuns.or(bs);
                    }
                }
            }

            // Now have bits set for all valid character runs. Replay our recorded elements,
            // but only emit character runs flagged as valid.
            int curCharsIndex = headerCharOffset;
            for (RecordedElement element : elements) {
                switch (element.getElementType()) {
                    case START:
                        delegate.startElement(element.getUri(), element.getLocalName(), element.getQName(), element.getAttrs());
                        // Fall through

                    case CONTINUE:
                        // Now emit characters that are valid. Note that boilerpipe pre-increments the character index, so
                        // we have to follow suit.
                        for (char[] chars : element.getCharacters()) {
                            curCharsIndex++;

                            if (validCharacterRuns.get(curCharsIndex)) {
                                delegate.characters(chars, 0, chars.length);
                            }
                        }
                        break;

                    case END:
                        delegate.endElement(element.getUri(), element.getLocalName(), element.getQName());
                        break;

                    default:
                        throw new RuntimeException("Unhandled element type: " + element.getElementType());
                }


            }
        } else {
            for (TextBlock block : td.getTextBlocks()) {
                if (block.isContent()) {
                    delegate.startElement(XHTMLContentHandler.XHTML, "p", "p", emptyAttrs);
                    char[] chars = block.getText().toCharArray();
                    delegate.characters(chars, 0, chars.length);
                    delegate.endElement(XHTMLContentHandler.XHTML, "p", "p");
                    delegate.ignorableWhitespace(NL, 0, NL.length);
                }
            }
        }

        delegate.endElement(XHTMLContentHandler.XHTML, "body", "body");
        delegate.endElement(XHTMLContentHandler.XHTML, "html", "html");

        // We defer ending any prefix mapping until here, which is why we don't pass this
        // through to the delegate in an overridden method.
        delegate.endPrefixMapping("");

        delegate.endDocument();
    }
}
