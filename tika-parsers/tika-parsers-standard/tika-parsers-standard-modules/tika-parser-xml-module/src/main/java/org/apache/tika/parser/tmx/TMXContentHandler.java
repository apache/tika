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
package org.apache.tika.parser.tmx;

import java.util.HashSet;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;


/**
 * Content Handler for Translation Memory eXchange (TMX) files.
 */
public class TMXContentHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;
    private String sourceLang;
    private final Set<String> targetLanguages = new HashSet<>();
    private int numberOfTUs = 0;
    private int numberOfTUVs = 0;
    private boolean inSegment = false;

    TMXContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
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

        if ("header".equals(localName)) {
            metadata.add("creation-tool", attributes.getValue("creationtool"));
            metadata.add("creation-tool-version", attributes.getValue("creationtoolversion"));
            metadata.add("segment-type", attributes.getValue("segtype"));
            metadata.add("original-format-type", attributes.getValue("o-tmf"));
            metadata.add("data-type", attributes.getValue("datatype"));
            sourceLang = attributes.getValue("srclang");
            metadata.add("source-language", sourceLang);
            metadata.add("admin-language", attributes.getValue("adminlang"));
        }

        if ("tu".equals(localName)) {
            numberOfTUs++;
            AttributesImpl attrs = extractAttributes(attributes);
            xhtml.startElement("div", attrs);
        }

        if ("tuv".equals(localName)) {
            numberOfTUVs++;
            AttributesImpl attrs = extractAttributes(attributes);
            xhtml.startElement("p", attrs);
        }

        if ("seg".equals(localName)) {
            inSegment = true;
        }

    }

    private AttributesImpl extractAttributes(Attributes attributes) {
        AttributesImpl attrs = new AttributesImpl();
        if (null != attributes.getValue("xml:lang")) {
            String lang = attributes.getValue("xml:lang");
            attrs.addAttribute("", "lang", "lang", "", lang);
            if (!lang.equalsIgnoreCase(sourceLang)) {
                targetLanguages.add(lang);
            }
        }
        if (null != attributes.getValue("o-encoding")) {
            attrs.addAttribute("", "original-encoding", "original-encoding", "", attributes.getValue("o-encoding"));
        }
        if (null != attributes.getValue("datatype")) {
            attrs.addAttribute("", "datatype", "datatype", "", attributes.getValue("datatype"));
        }
        if (null != attributes.getValue("usagecount")) {
            attrs.addAttribute("", "usagecount", "usagecount", "", attributes.getValue("usagecount"));
        }
        if (null != attributes.getValue("lastusagedate")) {
            attrs.addAttribute("", "lastusagedate", "lastusagedate", "", attributes.getValue("lastusagedate"));
        }
        if (null != attributes.getValue("creationdate")) {
            attrs.addAttribute("", "creationdate", "creationdate", "", attributes.getValue("creationdate"));
        }
        if (null != attributes.getValue("creationid")) {
            attrs.addAttribute("", "creationid", "creationid", "", attributes.getValue("creationid"));
        }
        if (null != attributes.getValue("changedate")) {
            attrs.addAttribute("", "changedate", "changedate", "", attributes.getValue("changedate"));
        }
        if (null != attributes.getValue("changeid")) {
            attrs.addAttribute("", "changeid", "changeid", "", attributes.getValue("changeid"));
        }
        if (null != attributes.getValue("tuid")) {
            attrs.addAttribute("", "tuid", "tuid", "", attributes.getValue("tuid"));
        }
        if (null != attributes.getValue("o-tmf")) {
            attrs.addAttribute("", "o-tmf", "o-tmf", "", attributes.getValue("o-tmf"));
        }
        return attrs;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if ("tu".equals(localName)) {
            xhtml.endElement("div");
        }

        if ("tuv".equals(localName)) {
            xhtml.endElement("p");
        }

        if ("seg".equals(localName)) {
            inSegment = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inSegment && length != 0) {
            xhtml.characters(ch, start, length);
        }
    }

    @Override
    public void endDocument() {
        targetLanguages.forEach(s -> metadata.add("target-language", s));
        metadata.set("tu-count", String.valueOf(numberOfTUs));
        metadata.set("tuv-count", String.valueOf(numberOfTUVs));
    }

}

