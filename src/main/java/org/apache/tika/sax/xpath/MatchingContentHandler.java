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
package org.apache.tika.sax.xpath;

import java.util.LinkedList;

import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that only passes the elements, attributes,
 * and text nodes that match the given XPath expression.
 */
public class MatchingContentHandler extends ContentHandlerDecorator {

    private final LinkedList<Matcher> matchers = new LinkedList<Matcher>();

    private Matcher matcher;

    public MatchingContentHandler(ContentHandler delegate, Matcher matcher) {
        super(delegate);
        this.matcher = matcher;
    }

    public void startElement(
            String uri, String localName, String name, Attributes attributes)
            throws SAXException {
        matchers.addFirst(matcher);
        matcher = matcher.descend(uri, localName);

        AttributesImpl matches = new AttributesImpl();
        for (int i = 0; i < attributes.getLength(); i++) {
            String attributeURI = attributes.getURI(i);
            String attributeName = attributes.getLocalName(i);
            if (matcher.matchesAttribute(attributeURI, attributeName)) {
                matches.addAttribute(
                        attributeURI, attributeName, attributes.getQName(i),
                        attributes.getType(i), attributes.getValue(i));
            }
        }

        if (matcher.matchesElement() || matches.getLength() > 0) {
            super.startElement(uri, localName, name, matches);
            if (!matcher.matchesElement()) {
                // Force the matcher to match the current element, so the
                // endElement method knows to emit the correct event
                matcher =
                    new CompositeMatcher(matcher, ElementMatcher.INSTANCE);
            }
        }
    }

    public void endElement(String uri, String localName, String name)
            throws SAXException {
        if (matcher.matchesElement()) {
            super.endElement(uri, localName, name);
        }
        matcher = matchers.removeFirst();
    }

    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (matcher.matchesText()) {
            super.characters(ch, start, length);
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        if (matcher.matchesText()) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    public void processingInstruction(String target, String data) {
        // TODO: Support for matching processing instructions
    }

    public void skippedEntity(String name) throws SAXException {
        // TODO: Can skipped entities refer to more than text?
        if (matcher.matchesText()) {
            super.skippedEntity(name);
        }
    }

}
