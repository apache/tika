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
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class KeynoteContentHandler extends DefaultHandler {

    public final static String PRESENTATION_WIDTH = "slides-width";
    public final static String PRESENTATION_HEIGHT = "slides-height";

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;

    private boolean inSlide = false;
    private boolean inTheme = false;
    private boolean inTitle = false;
    private boolean inBody = false;

    private boolean inMetadata = false;
    private boolean inMetaDataTitle = false;
    private boolean inMetaDataAuthors = false;

    private boolean stickNote = false;
    private boolean notes = false;

    private boolean inParsableText = false;

    private int numberOfSlides = 0;

    KeynoteContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void endDocument() throws SAXException {
        metadata.set(Metadata.SLIDE_COUNT, String.valueOf(numberOfSlides));
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if ("key:theme".equals(qName)) {
            inTheme = true;
        } else if ("key:slide".equals(qName)) {
            inSlide = true;
            numberOfSlides++;
            xhtml.startElement("div");
        } else if ("key:title-placeholder".equals(qName) && inSlide) {
            inTitle = true;
            xhtml.startElement("h1");
        } else if ("sf:sticky-note".equals(qName) && inSlide) {
            xhtml.startElement("p");
        } else if ("key:notes".equals(qName) && inSlide) {
            xhtml.startElement("p");
        } else if ("key:body-placeholder".equals(qName) && inSlide) {
            xhtml.startElement("p");
            inBody = true;
        } else if ("key:size".equals(qName) && !inTheme) {
            String width = attributes.getValue("sfa:w");
            String height = attributes.getValue("sfa:h");
            metadata.set(PRESENTATION_WIDTH, width);
            metadata.set(PRESENTATION_HEIGHT, height);
        } else if ("sf:text-body".equals(qName)) {
            inParsableText = true;
        } else if ("key:metadata".equals(qName)) {
            inMetadata = true;
        } else if (inMetadata && "key:title".equals(qName)) {
            inMetaDataTitle = true;
        } else if (inMetadata && "key:authors".equals(qName)) {
            inMetaDataAuthors = true;
        } else if (inMetaDataTitle && "key:string".equals(qName)) {
            metadata.set(Metadata.TITLE, attributes.getValue("sfa:string"));
        } else if (inMetaDataAuthors && "key:string".equals(qName)) {
            metadata.add(Metadata.AUTHOR, attributes.getValue("sfa:string"));
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if ("key:theme".equals(qName)) {
            inTheme = false;
        } else if ("key:slide".equals(qName)) {
            inSlide = false;
            xhtml.endElement("div");
        } else if ("key:title-placeholder".equals(qName) && inSlide) {
            inTitle = false;
            xhtml.endElement("h1");
        } else if ("sf:sticky-note".equals(qName) && inSlide) {
            xhtml.endElement("p");
        } else if ("key:notes".equals(qName) && inSlide) {
            xhtml.endElement("p");
        } else if ("key:body-placeholder".equals(qName) && inSlide) {
            xhtml.endElement("p");
            inBody = false;
        } else if ("sf:text-body".equals(qName)) {
            inParsableText = false;
        } else if ("key:metadata".equals(qName)) {
            inMetadata = false;
        } else if (inMetadata && "key:title".equals(qName)) {
            inMetaDataTitle = false;
        } else if (inMetadata && "key:authors".equals(qName)) {
            inMetaDataAuthors = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (!inParsableText || !inSlide) {
            return;
        }

        String text = new String(ch, start, length).trim();
        if (text.length() != 0) {
            xhtml.characters(text);
        }
    }

}