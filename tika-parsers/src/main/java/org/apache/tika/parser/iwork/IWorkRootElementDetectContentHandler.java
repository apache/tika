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
package org.apache.tika.parser.iwork;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * A handler that detects based on the root element which encapsulated handler to use.
 *
 * If during parsing no handler can be matched to the rootElement an exception is thrown.
 */
class IWorkRootElementDetectContentHandler extends ContentHandlerDecorator {

    private final XHTMLContentHandler xhtml;

    private final Metadata metadata;

    private boolean unknownType = true;

    IWorkRootElementDetectContentHandler(
            XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (unknownType) {
            if ("sl:document".equals(qName)) {
                metadata.set(
                        Metadata.CONTENT_TYPE, "application/vnd.apple.pages");
                setContentHandler(new PagesContentHandler(xhtml, metadata));
            } else if ("ls:document".equals(qName)) {
                metadata.set(
                        Metadata.CONTENT_TYPE, "application/vnd.apple.numbers");
                setContentHandler(new NumbersContentHandler(xhtml, metadata));
            } else {
                throw new RuntimeException(
                        "Could not find handler to parse document"
                        + " based on root element " + qName);
            }
            unknownType = false;
        }

        super.startElement(uri, localName, qName, attributes);
    }

}
