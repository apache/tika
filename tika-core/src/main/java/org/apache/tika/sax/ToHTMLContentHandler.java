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

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.SAXException;

/**
 * SAX event handler that serializes the HTML document to a character stream.
 * The incoming SAX events are expected to be well-formed (properly nested,
 * etc.) and valid HTML.
 *
 * @since Apache Tika 0.10
 */
public class ToHTMLContentHandler extends ToXMLContentHandler {

    private static final Set<String> EMPTY_ELEMENTS =
        new HashSet<String>(Arrays.asList(
            "area", "base", "basefont", "br", "col", "frame", "hr",
            "img", "input", "isindex", "link", "meta", "param"));

    public ToHTMLContentHandler(OutputStream stream, String encoding)
            throws UnsupportedEncodingException {
        super(stream, encoding);
    }

    public ToHTMLContentHandler() {
        super();
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (inStartElement) {
            write('>');
            inStartElement = false;

            if (EMPTY_ELEMENTS.contains(localName)) {
                namespaces.clear();
                return;
            }
        }

        super.endElement(uri, localName, qName);
    }

}
