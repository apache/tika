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
import org.apache.tika.metadata.TikaCoreProperties;
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
    private String tableId;
    private Integer numberOfColumns = null;
    private Integer currentColumn = null;

    private boolean inMetadata = false;
    private boolean inMetaDataTitle = false;
    private boolean inMetaDataAuthors = false;

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
        } else if ("key:master-slide".equals(qName)) {
            inSlide = true;
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
            metadata.set(TikaCoreProperties.TITLE, attributes.getValue("sfa:string"));
        } else if (inMetaDataAuthors && "key:string".equals(qName)) {
            metadata.add(TikaCoreProperties.CREATOR, attributes.getValue("sfa:string"));
        } else if (inSlide && "sf:tabular-model".equals(qName)) {
            tableId = attributes.getValue("sfa:ID");
            xhtml.startElement("table");
        } else if (tableId != null && "sf:columns".equals(qName)) {
            numberOfColumns = Integer.parseInt(attributes.getValue("sf:count"));
            currentColumn = 0;
        } else if (tableId != null && "sf:ct".equals(qName)) {
            parseTableData(attributes.getValue("sfa:s"));
        } else if (tableId != null && "sf:n".equals(qName)) {
            parseTableData(attributes.getValue("sf:v"));
        } else if ("sf:p".equals(qName)) {
            xhtml.startElement("p");
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
        } else if ("key:master-slide".equals(qName)) {
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
        } else if (inSlide && "sf:tabular-model".equals(qName)) {
            xhtml.endElement("table");
            tableId = null;
            numberOfColumns = null;
            currentColumn = null;
        } else if ("sf:p".equals(qName)) {
            xhtml.endElement("p");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (inParsableText && inSlide && length != 0) {
            xhtml.characters(ch, start, length);
        }
    }

    private void parseTableData(String value) throws SAXException {
      if (currentColumn == 0) {
          xhtml.startElement("tr");
      }

      xhtml.element("td", value);

      if (currentColumn.equals(numberOfColumns)) {
          xhtml.endElement("tr");
      }
    }

}
