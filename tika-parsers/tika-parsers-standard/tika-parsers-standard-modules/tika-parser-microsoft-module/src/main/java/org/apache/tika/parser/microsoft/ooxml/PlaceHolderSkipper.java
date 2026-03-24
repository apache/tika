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
package org.apache.tika.parser.microsoft.ooxml;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler wrapper that skips content inside placeholder ({@code <ph>})
 * elements in PPTX slide masters and layouts. Delegates all non-placeholder
 * events to the wrapped handler.
 */
class PlaceHolderSkipper extends DefaultHandler {

    private final ContentHandler wrappedHandler;
    private boolean inPH = false;

    PlaceHolderSkipper(ContentHandler wrappedHandler) {
        this.wrappedHandler = wrappedHandler;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        if ("ph".equals(localName)) {
            inPH = true;
        }
        if (!inPH) {
            wrappedHandler.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (!inPH) {
            wrappedHandler.endElement(uri, localName, qName);
        }
        if ("sp".equals(localName)) {
            inPH = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!inPH) {
            wrappedHandler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (!inPH) {
            wrappedHandler.characters(ch, start, length);
        }
    }
}
