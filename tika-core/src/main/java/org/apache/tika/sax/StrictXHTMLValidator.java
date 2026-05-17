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
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A SAX content handler decorator that enforces XHTML well-formedness on the
 * incoming event stream. Any parser that emits an event sequence that would
 * produce malformed XHTML triggers a {@link SAXException} synchronously — the
 * stack trace points at the parser code that made the offending call, instead
 * of surfacing later as a parse error on the serialized output.
 * <p>
 * Invariants enforced:
 * <ul>
 *   <li>{@code startDocument} is called at most once.</li>
 *   <li>No SAX events arrive after {@code endDocument}.</li>
 *   <li>Every {@code endElement} matches the topmost open {@code startElement}
 *       (no cross-nesting like {@code &lt;a&gt;&lt;b&gt;&lt;/a&gt;&lt;/b&gt;}).</li>
 *   <li>The element stack is empty when {@code endDocument} fires (no unclosed
 *       elements left dangling by an exception path).</li>
 *   <li>Within a single {@code startElement}, no two attributes share the same
 *       (namespaceURI, localName) pair (the bug class that produces
 *       {@code &lt;div class="x" class="y"&gt;}).</li>
 * </ul>
 * Use as a decorator wrapping the real handler. It passes every event through
 * to the downstream handler after validation, so any normal text/XHTML capture
 * still works.
 */
public class StrictXHTMLValidator extends ContentHandlerDecorator {

    private final Deque<QName> openElements = new ArrayDeque<>();
    private boolean documentStarted;
    private boolean documentEnded;

    public StrictXHTMLValidator(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void startDocument() throws SAXException {
        if (documentStarted) {
            throw new SAXException("StrictXHTMLValidator: startDocument called twice");
        }
        if (documentEnded) {
            throw new SAXException(
                    "StrictXHTMLValidator: startDocument after endDocument");
        }
        documentStarted = true;
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        if (documentEnded) {
            throw new SAXException("StrictXHTMLValidator: endDocument called twice");
        }
        if (!openElements.isEmpty()) {
            throw new SAXException(
                    "StrictXHTMLValidator: endDocument with " + openElements.size()
                            + " unclosed element(s); topmost was <"
                            + openElements.peek().qOrLocal() + ">");
        }
        documentEnded = true;
        super.endDocument();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {
        ensureNotEnded("startElement <" + display(qName, localName) + ">");
        checkAttributesUnique(qName, localName, attrs);
        openElements.push(new QName(uri, localName, qName));
        super.startElement(uri, localName, qName, attrs);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        ensureNotEnded("endElement </" + display(qName, localName) + ">");
        if (openElements.isEmpty()) {
            throw new SAXException(
                    "StrictXHTMLValidator: endElement </" + display(qName, localName)
                            + "> with no matching startElement");
        }
        QName top = openElements.pop();
        if (!top.matches(uri, localName, qName)) {
            throw new SAXException(
                    "StrictXHTMLValidator: endElement </" + display(qName, localName)
                            + "> does not match topmost open element <"
                            + top.qOrLocal() + ">");
        }
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        ensureNotEnded("characters");
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        ensureNotEnded("ignorableWhitespace");
        super.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        ensureNotEnded("processingInstruction");
        super.processingInstruction(target, data);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        ensureNotEnded("startPrefixMapping");
        super.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        ensureNotEnded("endPrefixMapping");
        super.endPrefixMapping(prefix);
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        ensureNotEnded("skippedEntity");
        super.skippedEntity(name);
    }

    private void ensureNotEnded(String event) throws SAXException {
        if (documentEnded) {
            throw new SAXException(
                    "StrictXHTMLValidator: " + event + " arrived after endDocument");
        }
    }

    private void checkAttributesUnique(String elementQName, String elementLocalName,
                                       Attributes attrs) throws SAXException {
        int n = attrs.getLength();
        if (n < 2) {
            return;
        }
        // (uri, localName) pairs must be unique per the XML namespaces spec.
        // We also check raw qnames because Tika's serializers emit by qname and
        // duplicate qnames produce malformed XHTML even when localnames differ.
        Set<String> seenUriLocal = new HashSet<>(n);
        Set<String> seenQNames = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            String uri = nullSafe(attrs.getURI(i));
            String local = nullSafe(attrs.getLocalName(i));
            String qn = nullSafe(attrs.getQName(i));
            String key = uri + "" + local;
            if (!seenUriLocal.add(key)) {
                throw new SAXException(
                        "StrictXHTMLValidator: duplicate attribute on <"
                                + display(elementQName, elementLocalName) + ">: "
                                + (uri.isEmpty() ? local : ("{" + uri + "}" + local)));
            }
            if (!qn.isEmpty() && !seenQNames.add(qn)) {
                throw new SAXException(
                        "StrictXHTMLValidator: duplicate attribute qname on <"
                                + display(elementQName, elementLocalName) + ">: " + qn);
            }
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String display(String qName, String localName) {
        if (qName != null && !qName.isEmpty()) {
            return qName;
        }
        return localName == null ? "" : localName;
    }

    private static final class QName {
        final String uri;
        final String localName;
        final String qName;

        QName(String uri, String localName, String qName) {
            this.uri = nullSafe(uri);
            this.localName = nullSafe(localName);
            this.qName = nullSafe(qName);
        }

        boolean matches(String u, String l, String q) {
            // SAX parsers can vary in which fields they populate. Accept a
            // match on either (uri, localName) or qName, whichever is present.
            String otherU = nullSafe(u);
            String otherL = nullSafe(l);
            String otherQ = nullSafe(q);
            boolean uriLocalMatch = uri.equals(otherU) && localName.equals(otherL)
                    && !localName.isEmpty();
            boolean qNameMatch = !qName.isEmpty() && qName.equals(otherQ);
            return uriLocalMatch || qNameMatch;
        }

        String qOrLocal() {
            return qName.isEmpty() ? localName : qName;
        }
    }
}
