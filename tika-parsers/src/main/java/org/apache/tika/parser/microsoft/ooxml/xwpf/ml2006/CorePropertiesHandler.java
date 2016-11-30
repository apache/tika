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

package org.apache.tika.parser.microsoft.ooxml.xwpf.ml2006;


import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.opc.ContentTypes;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

class CorePropertiesHandler extends AbstractPartHandler {

    final static String DC_NS = "http://purl.org/dc/elements/1.1";
    final static String DC_TERMS_NS = "http://purl.org/dc/terms";
    final static String CP_NS = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";

    private final Metadata metadata;

    final StringBuilder buffer = new StringBuilder();
    final Map<String, Map<String, Property>> properties = new HashMap<>();

    public CorePropertiesHandler(Metadata metadata) {
        this.metadata = metadata;
        addProperties();
    }

    void addProperties() {
        Map<String, Property> dc = properties.get(DC_NS);
        if (dc == null) {
            dc = new HashMap<>();
        }
        dc.put("creator", TikaCoreProperties.CREATOR);
        dc.put("title", TikaCoreProperties.TITLE);
        dc.put("description", TikaCoreProperties.DESCRIPTION);
        properties.put(DC_NS, dc);

        Map<String, Property> dcTerms = properties.get(DC_TERMS_NS);
        if (dcTerms == null) {
            dcTerms = new HashMap<>();
        }
        dcTerms.put("created", TikaCoreProperties.CREATED);
        dcTerms.put("modified", TikaCoreProperties.MODIFIED);

        properties.put(DC_TERMS_NS, dcTerms);

        Map<String, Property> cp = properties.get(CP_NS);
        if (cp == null) {
            cp = new HashMap<>();
        }
        cp.put("category", OfficeOpenXMLCore.CATEGORY);
        cp.put("contentStatus", OfficeOpenXMLCore.CONTENT_STATUS);
        cp.put("lastModifiedBy", TikaCoreProperties.MODIFIER);
        cp.put("lastPrinted", OfficeOpenXMLCore.LAST_PRINTED);
        cp.put("revision", OfficeOpenXMLCore.REVISION);
        cp.put("subject", OfficeOpenXMLCore.SUBJECT);
        cp.put("version", OfficeOpenXMLCore.VERSION);
        properties.put(CP_NS, cp);
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
        buffer.setLength(0);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        Property prop = getProperty(uri, localName);
        if (prop != null) {

            if (prop.isMultiValuePermitted()) {
                metadata.add(prop, buffer.toString());
            } else {
                metadata.set(prop, buffer.toString());
            }
        }
        buffer.setLength(0);

    }

    private Property getProperty(String uri, String localName) {
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length()-1);
        }

        Map<String, Property> m = properties.get(uri);
        if (m != null) {
            return m.get(localName);
        }
        return null;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        buffer.append(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        buffer.append(ch, start, length);
    }

    @Override
    public String getContentType() {
        return ContentTypes.CORE_PROPERTIES_PART;
    }
}
