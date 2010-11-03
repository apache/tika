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

    /**
     * The newline character that gets inserted after block elements.
     */
    private static final char[] NL = new char[] { '\n' };

    private ContentHandler delegate;
    private BoilerpipeExtractor extractor;

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
        this.delegate = delegate;
        this.extractor = extractor;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        TextDocument td = toTextDocument();
        try {
            extractor.process(td);
        } catch (BoilerpipeProcessingException e) {
            throw new SAXException(e);
        }
        
        Attributes emptyAttrs = new AttributesImpl();

        delegate.startDocument();
        delegate.startPrefixMapping("", XHTMLContentHandler.XHTML);

        delegate.startElement(XHTMLContentHandler.XHTML, "html", "html", emptyAttrs);
        delegate.startElement(XHTMLContentHandler.XHTML, "head", "head", emptyAttrs);
        delegate.startElement(XHTMLContentHandler.XHTML, "title", "title", emptyAttrs);
        
        if (td.getTitle() != null) {
            char[] titleChars = td.getTitle().toCharArray();
            delegate.characters(titleChars, 0, titleChars.length);
            delegate.ignorableWhitespace(NL, 0, NL.length);
        }
        
        delegate.endElement(XHTMLContentHandler.XHTML, "title", "title");
        delegate.endElement(XHTMLContentHandler.XHTML, "head", "head");
        
        delegate.startElement(XHTMLContentHandler.XHTML, "body", "body", emptyAttrs);

        for (TextBlock block : td.getTextBlocks()) {
            if (block.isContent()) {
                delegate.startElement(XHTMLContentHandler.XHTML, "p", "p", emptyAttrs);
                char[] chars = block.getText().toCharArray();
                delegate.characters(chars, 0, chars.length);
                delegate.endElement(XHTMLContentHandler.XHTML, "p", "p");
                delegate.ignorableWhitespace(NL, 0, NL.length);
            }
        }
        
        delegate.endElement(XHTMLContentHandler.XHTML, "body", "body");
        delegate.endElement(XHTMLContentHandler.XHTML, "html", "html");
        
        delegate.endPrefixMapping("");

        delegate.endDocument();
    }
}
