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
package org.apache.tika.sax;

import java.util.ArrayDeque;
import java.util.Deque;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * SAX decorator that tracks open elements so a parser can recover well-formed
 * XHTML when an exception interrupts the SAX stream mid-element.
 * <p>
 * The decorator is a thin passthrough on the happy path: it pushes and pops an
 * internal stack on {@code startElement}/{@code endElement} and otherwise forwards
 * every event to the wrapped handler unchanged. It deliberately does NOT mask
 * bad event sequences (mismatched or excess endElement, duplicate attributes,
 * etc.) -- those remain visible to {@link StrictXHTMLValidator} so parser bugs
 * still surface as test failures.
 * <p>
 * The unhappy path -- a per-part SAX parser throwing mid-element after emitting
 * one or more start tags -- is handled via {@link #drainOpenElements()}, which
 * emits a matching {@code endElement} (with the original uri/localName/qName)
 * for every element still on the stack, in reverse open order. The wrapped
 * handler is left in a well-formed state with no dangling elements from the
 * failed sub-parse.
 * <p>
 * Typical use wraps the handler that receives events from an inner SAX parser,
 * inside the catch arm that swallows the inner parser's exception:
 * <pre>{@code
 * XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(contentHandler);
 * try {
 *     XMLReaderUtils.parseSAX(stream, new EmbeddedContentHandler(balancer), context);
 * } catch (SAXException e) {
 *     balancer.drainOpenElements();
 *     // ... log and continue ...
 * }
 * }</pre>
 * This handler does not touch {@code startDocument}/{@code endDocument}; the
 * caller still owns the document lifecycle.
 */
public class XHTMLBalancingHandler extends ContentHandlerDecorator {

    private final Deque<QName> openElements = new ArrayDeque<>();

    public XHTMLBalancingHandler(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {
        openElements.push(new QName(uri, localName, qName));
        super.startElement(uri, localName, qName, attrs);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        // Pop best-effort: an unbalanced endElement (e.g., emitted after the
        // matching startElement was swallowed) still forwards downstream so a
        // wrapping StrictXHTMLValidator sees the violation.
        if (!openElements.isEmpty()) {
            openElements.pop();
        }
        super.endElement(uri, localName, qName);
    }

    /**
     * Emits a matching {@code endElement} for every element still on the open
     * stack, in reverse open order. After this call the stack is empty.
     * <p>
     * Intended for the catch arm of a caller that swallowed a
     * {@link SAXException} from an inner SAX parser: the inner parser may have
     * left one or more elements open mid-stream, and downstream serialization
     * needs matching closers before any further events.
     * <p>
     * Does NOT emit {@code endDocument} -- document lifecycle stays with the
     * caller.
     */
    public void drainOpenElements() throws SAXException {
        while (!openElements.isEmpty()) {
            QName q = openElements.pop();
            super.endElement(q.uri, q.localName, q.qName);
        }
    }

    /**
     * Number of elements currently open through this handler. Exposed for
     * tests and for callers that want to know whether
     * {@link #drainOpenElements()} would emit anything.
     */
    public int openElementCount() {
        return openElements.size();
    }

    private static final class QName {
        final String uri;
        final String localName;
        final String qName;

        QName(String uri, String localName, String qName) {
            this.uri = uri == null ? "" : uri;
            this.localName = localName == null ? "" : localName;
            this.qName = qName == null ? "" : qName;
        }
    }
}
