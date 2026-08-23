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
package org.apache.tika.parser.geogebra;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * SAX handler for {@code geogebra.xml} and {@code geogebra_macro.xml}.
 * <p>
 * Extracts the document metadata from the {@code &lt;geogebra&gt;} root and
 * the {@code &lt;construction&gt;} element (when a {@link Metadata} object is
 * given), and emits the user-visible text as XHTML paragraphs: string-literal
 * {@code &lt;expression&gt;}s of text objects, the rich text of
 * {@code &lt;content&gt;} elements (ink notes, inline text), element
 * {@code &lt;caption&gt;}s and macro names/help texts.
 */
class GeoGebraXMLHandler extends DefaultHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;
    private int depth = 0;

    /**
     * @param xhtml    the handler paragraphs are written to
     * @param metadata the metadata to fill from the root and construction
     *                 elements, or {@code null} to extract text only
     */
    GeoGebraXMLHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (depth == 0 && "geogebra".equals(localName)) {
            if (metadata != null) {
                setIfNotBlank(GeoGebraParser.APP_NAME, attributes.getValue("app"));
                setIfNotBlank(GeoGebraParser.APP_VERSION, attributes.getValue("version"));
                setIfNotBlank(GeoGebraParser.FORMAT_VERSION, attributes.getValue("format"));
                setIfNotBlank(GeoGebraParser.ID, attributes.getValue("id"));
            }
        } else if ("construction".equals(localName)) {
            if (metadata != null) {
                setIfNotBlank(TikaCoreProperties.TITLE, attributes.getValue("title"));
                setIfNotBlank(TikaCoreProperties.CREATOR, attributes.getValue("author"));
                setIfNotBlank(GeoGebraParser.DATE, attributes.getValue("date"));
            }
        } else if ("expression".equals(localName)) {
            handleExpression(attributes.getValue("exp"));
        } else if ("content".equals(localName)) {
            handleContent(attributes.getValue("val"));
        } else if ("caption".equals(localName)) {
            paragraph(attributes.getValue("val"));
        } else if ("macro".equals(localName)) {
            String toolName = attributes.getValue("toolName");
            if (isBlank(toolName)) {
                toolName = attributes.getValue("cmdName");
            }
            if (metadata != null && !isBlank(toolName)) {
                metadata.add(GeoGebraParser.TOOL_NAME, toolName);
            }
            paragraph(toolName);
            paragraph(attributes.getValue("toolHelp"));
        }
        depth++;
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        depth--;
    }

    /**
     * Emits a text object's expression, which is a GeoGebra string literal
     * like {@code "some text"}. Computational expressions (anything not
     * quoted) are geometry, not text, and are skipped.
     */
    private void handleExpression(String exp) throws SAXException {
        if (exp == null || exp.length() < 2 || exp.charAt(0) != '"' ||
                exp.charAt(exp.length() - 1) != '"') {
            return;
        }
        paragraph(exp.substring(1, exp.length() - 1).replace("\\\"", "\""));
    }

    /**
     * Emits the text runs of a rich-text {@code content} value, a JSON array
     * of text runs like {@code [{"text":"Hello\n"}]}. All {@code text} fields
     * are collected recursively (tables and mind maps nest them), joined, and
     * emitted one paragraph per line.
     */
    private void handleContent(String val) throws SAXException {
        if (val == null || val.isEmpty()) {
            return;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(val);
        } catch (IOException e) {
            //not JSON; ignore
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String text : root.findValuesAsText("text")) {
            sb.append(text);
        }
        for (String line : sb.toString().split("\n")) {
            paragraph(line);
        }
    }

    private void paragraph(String text) throws SAXException {
        if (!isBlank(text)) {
            xhtml.element("p", text.trim());
        }
    }

    private void setIfNotBlank(Property property, String value) {
        if (!isBlank(value)) {
            metadata.set(property, value.trim());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
