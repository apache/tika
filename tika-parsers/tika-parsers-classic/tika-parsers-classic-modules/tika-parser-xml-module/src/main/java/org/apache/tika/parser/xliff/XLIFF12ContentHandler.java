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
package org.apache.tika.parser.xliff;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Content Handler for XLIFF 1.2 documents.
 */
public class XLIFF12ContentHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;
    private int numberOfFiles = 0;
    private int numberOfTUs = 0;
    private boolean inTransUnit = false;

    XLIFF12ContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void startDocument() throws SAXException {
        xhtml.startDocument();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

        final AttributesImpl attributeVals = new AttributesImpl();
        attributeVals.setAttributes(attributes);

        if ("file".equals(localName)) {
            numberOfFiles++;

            // Write out the original file name
            metadata.add("original", attributes.getValue("source-language"));

            xhtml.startElement("div");
            xhtml.startElement("h1");
            xhtml.characters(attributes.getValue("original"));
            xhtml.endElement("h1");

            // Add the files source (mandatory) and target (optional) languages
            metadata.add("source-language", attributes.getValue("source-language"));
            if (null != attributes.getValue("target-language")) {
                metadata.add("target-language", attributes.getValue("target-language"));
            }
        }

        if ("trans-unit".equals(localName)) {
            numberOfTUs++;
            inTransUnit = true;
            xhtml.startElement("div", attributeVals);
        }

        if ("source".equals(localName)) {
            AttributesImpl attrs = extractAttributes(attributes);
            xhtml.startElement("p", attrs);
        }

        if ("target".equals(localName)) {
            AttributesImpl attrs = extractAttributes(attributes);
            xhtml.startElement("p", attrs);
        }
    }

    private AttributesImpl extractAttributes(Attributes attributes) {
        AttributesImpl attrs = new AttributesImpl();
        if (null != attributes.getValue("xml:lang")) {
            attrs.addAttribute("", "lang", "lang", "", attributes.getValue("xml:lang"));
        }
        return attrs;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if ("file".equals(localName)) {
            xhtml.endElement("div");
        }

        if ("trans-unit".equals(localName)) {
            inTransUnit = false;
            xhtml.endElement("div");
        }

        if ("source".equals(localName)) {
            xhtml.endElement("p");
        }

        if ("target".equals(localName)) {
            xhtml.endElement("p");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inTransUnit && length != 0) {
            xhtml.characters(ch, start, length);
        }
    }

    @Override
    public void endDocument() {
        metadata.set("file-count", String.valueOf(numberOfFiles));
        metadata.set("tu-count", String.valueOf(numberOfTUs));
    }

}
