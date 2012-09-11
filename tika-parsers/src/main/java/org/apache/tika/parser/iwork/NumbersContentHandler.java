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
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;

class NumbersContentHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;

    private boolean inSheet = false;

    private boolean inText = false;
    private boolean parseText = false;

    private boolean inMetadata = false;
    private Property metadataKey;
    private String metadataPropertyQName;

    private boolean inTable = false;
    private int numberOfSheets = 0;
    private int numberOfColumns = -1;
    private int currentColumn = 0;

    private Map<String, String> menuItems = new HashMap<String, String>();
    private String currentMenuItemId;

    NumbersContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void endDocument() throws SAXException {
        metadata.set(Metadata.PAGE_COUNT, String.valueOf(numberOfSheets));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ("ls:workspace".equals(qName)) {
            inSheet = true;
            numberOfSheets++;
            xhtml.startElement("div");
            String sheetName = attributes.getValue("ls:workspace-name");
            metadata.add("sheetNames", sheetName);
        }

        if ("sf:text".equals(qName)) {
            inText = true;
            xhtml.startElement("p");
        }

        if ("sf:p".equals(qName)) {
            parseText = true;
        }

        if ("sf:metadata".equals(qName)) {
            inMetadata = true;
            return;
        }

        if (inMetadata && metadataKey == null) {
            metadataKey = resolveMetadataKey(localName);
            metadataPropertyQName = qName;
        }

        if (inMetadata && metadataKey != null && "sf:string".equals(qName)) {
            metadata.add(metadataKey, attributes.getValue("sfa:string"));
        }

        if (!inSheet) {
            return;
        }

        if ("sf:tabular-model".equals(qName)) {
            String tableName = attributes.getValue("sf:name");
            xhtml.startElement("div");
            xhtml.characters(tableName);
            xhtml.endElement("div");
            inTable = true;
            xhtml.startElement("table");
            xhtml.startElement("tr");
            currentColumn = 0;
        }

        if ("sf:menu-choices".equals(qName)) {
            menuItems = new HashMap<String, String>();
        }

        if (inTable && "sf:grid".equals(qName)) {
            numberOfColumns = Integer.parseInt(attributes.getValue("sf:numcols"));
        }

        if (menuItems != null && "sf:t".equals(qName)) {
            currentMenuItemId = attributes.getValue("sfa:ID");
        }

        if (currentMenuItemId != null && "sf:ct".equals(qName)) {
            menuItems.put(currentMenuItemId, attributes.getValue("sfa:s"));
        }

        if (inTable && "sf:ct".equals(qName)) {
            if (currentColumn >= numberOfColumns) {
                currentColumn = 0;
                xhtml.endElement("tr");
                xhtml.startElement("tr");
            }

            xhtml.element("td", attributes.getValue("sfa:s"));
            currentColumn++;
        }

        if (inTable && ("sf:n".equals(qName) || "sf:rn".equals(qName))) {
            if (currentColumn >= numberOfColumns) {
                currentColumn = 0;
                xhtml.endElement("tr");
                xhtml.startElement("tr");
            }

            xhtml.element("td", attributes.getValue("sf:v"));
            currentColumn++;
        }

        if (inTable && "sf:proxied-cell-ref".equals(qName)) {
            if (currentColumn >= numberOfColumns) {
                currentColumn = 0;
                xhtml.endElement("tr");
                xhtml.startElement("tr");
            }

            xhtml.element("td", menuItems.get(attributes.getValue("sfa:IDREF")));
            currentColumn++;
        }

        if ("sf:chart-name".equals(qName)) {
            // Extract chart name:
            xhtml.startElement("div", "class", "chart");
            xhtml.startElement("h1");
            xhtml.characters(attributes.getValue("sfa:string"));
            xhtml.endElement("h1");
            xhtml.endElement("div");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (parseText && length > 0) {
            xhtml.characters(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("ls:workspace".equals(qName)) {
            inSheet = false;
            xhtml.endElement("div");
        }

        if ("sf:text".equals(qName)) {
            inText = false;
            xhtml.endElement("p");
        }

        if ("sf:p".equals(qName)) {
            parseText = false;
        }

        if ("sf:metadata".equals(qName)) {
            inMetadata = false;
        }

        if (inMetadata && qName.equals(metadataPropertyQName)) {
            metadataPropertyQName = null;
            metadataKey = null;
        }

        if (!inSheet) {
            return;
        }

        if ("sf:menu-choices".equals(qName)) {
        }

        if ("sf:tabular-model".equals(qName)) {
            inTable = false;
            xhtml.endElement("tr");
            xhtml.endElement("table");
        }

        if (currentMenuItemId != null && "sf:t".equals(qName)) {
            currentMenuItemId = null;
        }
    }

    private Property resolveMetadataKey(String localName) {
        if ("authors".equals(localName)) {
            return TikaCoreProperties.CREATOR;
        }
        if ("title".equals(localName)) {
            return TikaCoreProperties.TITLE;
        }
        if ("comment".equals(localName)) {
            return TikaCoreProperties.COMMENTS;
        }
        return Property.internalText(localName);
    }
}
