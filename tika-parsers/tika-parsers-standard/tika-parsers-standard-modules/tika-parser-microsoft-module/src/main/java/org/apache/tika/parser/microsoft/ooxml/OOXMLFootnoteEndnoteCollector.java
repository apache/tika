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
package org.apache.tika.parser.microsoft.ooxml;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import java.io.StringWriter;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler that collects raw XML content for each footnote or endnote
 * by ID from footnotes.xml or endnotes.xml. The collected XML can then be
 * re-parsed through the main handler when a footnote/endnote reference is
 * encountered in the document body.
 */
class OOXMLFootnoteEndnoteCollector extends DefaultHandler {

    private static final String W_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String FOOTNOTE = "footnote";
    private static final String ENDNOTE = "endnote";

    private final Map<String, byte[]> contentMap = new HashMap<>();

    private String currentId = null;
    private StringWriter currentWriter = null;
    private XMLStreamWriter currentXmlWriter = null;
    private int depth = 0;

    Map<String, byte[]> getContentMap() {
        return contentMap;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) throws SAXException {
        if (currentId != null) {
            depth++;
            try {
                if (qName != null && !qName.isEmpty()) {
                    currentXmlWriter.writeStartElement(qName);
                } else {
                    currentXmlWriter.writeStartElement(localName);
                }
                for (int i = 0; i < atts.getLength(); i++) {
                    String attQName = atts.getQName(i);
                    if (attQName != null && !attQName.isEmpty()) {
                        currentXmlWriter.writeAttribute(attQName, atts.getValue(i));
                    } else {
                        currentXmlWriter.writeAttribute(
                                atts.getLocalName(i), atts.getValue(i));
                    }
                }
            } catch (XMLStreamException e) {
                throw new SAXException(e);
            }
            return;
        }

        if ((FOOTNOTE.equals(localName) || ENDNOTE.equals(localName))) {
            String id = atts.getValue(W_NS, "id");
            // skip separator/continuation footnotes (ids 0 and -1)
            if (id != null && !id.equals("0") && !id.equals("-1")) {
                currentId = id;
                currentWriter = new StringWriter();
                try {
                    currentXmlWriter = XMLOutputFactory.newInstance()
                            .createXMLStreamWriter(currentWriter);
                    currentXmlWriter.writeStartDocument();
                    // wrap content in a root element
                    currentXmlWriter.writeStartElement("body");
                } catch (XMLStreamException e) {
                    throw new SAXException(e);
                }
                depth = 0;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (currentId == null) {
            return;
        }

        if (depth == 0) {
            // end of the footnote/endnote element itself
            try {
                currentXmlWriter.writeEndElement(); // close <body>
                currentXmlWriter.writeEndDocument();
                currentXmlWriter.flush();
                currentXmlWriter.close();
            } catch (XMLStreamException e) {
                throw new SAXException(e);
            }
            contentMap.put(currentId,
                    currentWriter.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            currentId = null;
            currentWriter = null;
            currentXmlWriter = null;
            return;
        }

        depth--;
        try {
            currentXmlWriter.writeEndElement();
        } catch (XMLStreamException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentId != null && currentXmlWriter != null) {
            try {
                currentXmlWriter.writeCharacters(new String(ch, start, length));
            } catch (XMLStreamException e) {
                throw new SAXException(e);
            }
        }
    }
}
