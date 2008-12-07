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

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Content handler decorator that only passes the
 * {@link #characters(char[], int, int)} and
 * (@link {@link #ignorableWhitespace(char[], int, int)}
 * (plus {@link #startDocument()} and {@link #endDocument()} events to
 * the decorated content handler.
 */
public class TextContentHandler extends DefaultHandler {

    private final ContentHandler delegate;

    public TextContentHandler(ContentHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        delegate.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        delegate.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void startDocument() throws SAXException {
        delegate.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        delegate.endDocument();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
