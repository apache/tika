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
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PagesContentHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;

    private boolean inMetaDataPart = false;
    private boolean parseProperty = false;
    private boolean inParsableText = false;
    private int pageCount = 0;

    private Map<String, List<List<String>>> tableData =
        new HashMap<String, List<List<String>>>();
    private String activeTableId;
    private int numberOfColumns = 0;
    private List<String> activeRow = new ArrayList<String>();

    private String metaDataLocalName;
    private String metaDataQName;

    PagesContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void endDocument() throws SAXException {
        metadata.set(Metadata.PAGE_COUNT, String.valueOf(pageCount));
        if (pageCount > 0) {
            xhtml.endElement("div");
        }
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (parseProperty) {
            String value = parsePrimitiveElementValue(qName, attributes);
            if (value != null) {
                Object metaDataKey = resolveMetaDataKey(metaDataLocalName);
                if(metaDataKey instanceof Property) {
                    metadata.set((Property)metaDataKey, value);
                } else {
                    metadata.add((String)metaDataKey, value);
                }
            }
        }

        if ("sl:publication-info".equals(qName)) {
            inMetaDataPart = true;
        } else if ("sf:metadata".equals(qName)) {
            inMetaDataPart = true;
        } else if ("sf:page-start".equals(qName)) {
            if (pageCount > 0) {
                xhtml.endElement("div");
            }
            xhtml.startElement("div");
            pageCount++;
        } else if ("sf:p".equals(qName) && pageCount > 0) {
            inParsableText = true;
            xhtml.startElement("p");
        } else if ("sf:attachment".equals(qName)) {
            String kind = attributes.getValue("sf:kind");
            if ("tabular-attachment".equals(kind)) {
                activeTableId = attributes.getValue("sfa:ID");
                tableData.put(activeTableId, new ArrayList<List<String>>());
            }
        } else if ("sf:attachment-ref".equals(qName)) {
            String idRef = attributes.getValue("sfa:IDREF");
            outputTable(idRef);
        }

        if (activeTableId != null) {
            parseTableData(qName, attributes);
        }

        if (inMetaDataPart) {
            metaDataLocalName = localName;
            metaDataQName = qName;
            parseProperty = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (metaDataLocalName != null && metaDataLocalName.equals(localName)) {
            metaDataLocalName = null;
            parseProperty = false;
        }

        if ("sl:publication-info".equals(qName)) {
            inMetaDataPart = false;
        } else if ("sf:metadata".equals(qName)) {
            inMetaDataPart = false;
        } else if ("sf:p".equals(qName) && pageCount > 0) {
            inParsableText = false;
            xhtml.endElement("p");
        } else if ("sf:attachment".equals(qName)) {
            activeTableId = null;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inParsableText && length > 0) {
            xhtml.characters(ch, start, length);
        }
    }

    private void parseTableData(String qName, Attributes attributes) {
        if ("sf:grid".equals(qName)) {
            String numberOfColumns = attributes.getValue("sf:numcols");
            this.numberOfColumns = Integer.parseInt(numberOfColumns);
        } else if ("sf:ct".equals(qName)) {
            activeRow.add(attributes.getValue("sfa:s"));

            if (activeRow.size() >= 3) {
                tableData.get(activeTableId).add(activeRow);
                activeRow = new ArrayList<String>();
            }
        }
    }

    private void outputTable(String idRef) throws SAXException {
        List<List<String>> tableData = this.tableData.get(idRef);
        if (tableData != null) {
            xhtml.startElement("table");
            for (List<String> row : tableData) {
                xhtml.startElement("tr");
                for (String cell : row) {
                    xhtml.element("td", cell);
                }
                xhtml.endElement("tr");
            }
            xhtml.endElement("table");
        }
    }

    /**
     * Returns a resolved key that is common in other document types or
     * returns the specified metaDataLocalName if no common key could be found.
     * The key could be a simple String key, or could be a {@link Property}
     *
     * @param metaDataLocalName The localname of the element containing metadata
     * @return a resolved key that is common in other document types
     */
    private Object resolveMetaDataKey(String metaDataLocalName) {
        Object metaDataKey = metaDataLocalName;
        if ("sf:authors".equals(metaDataQName)) {
            metaDataKey = Metadata.AUTHOR;
        } else if ("sf:title".equals(metaDataQName)) {
            metaDataKey = Metadata.TITLE;
        } else if ("sl:SLCreationDateProperty".equals(metaDataQName)) {
            metaDataKey = Metadata.CREATION_DATE;
        } else if ("sl:SLLastModifiedDateProperty".equals(metaDataQName)) {
            metaDataKey = Metadata.LAST_MODIFIED;
        } else if ("sl:language".equals(metaDataQName)) {
            metaDataKey = Metadata.LANGUAGE;
        }
        return metaDataKey;
    }

    /**
     * Returns the value of a primitive element e.g.:
     * &lt;sl:number sfa:number="0" sfa:type="f"/&gt; - the number attribute
     * &lt;sl:string sfa:string="en"/&gt; = the string attribute
     * <p>
     * Returns <code>null</code> if the value could not be extracted from
     * the list of attributes.
     *
     * @param qName      The fully qualified name of the element containing
     *                   the value to extract
     * @param attributes The list of attributes of which one contains the
     *                   value to be extracted
     * @return the value of a primitive element
     */
    private String parsePrimitiveElementValue(
            String qName, Attributes attributes) {
        if ("sl:string".equals(qName) || "sf:string".equals(qName)) {
            return attributes.getValue("sfa:string");
        } else if ("sl:number".equals(qName)) {
            return attributes.getValue("sfa:number");
        } else if ("sl:date".equals(qName)) {
            return attributes.getValue("sf:val");
        }

        return null;
    }

}