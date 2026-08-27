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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * SAX handler for {@code geogebra.xml} and {@code geogebra_macro.xml}.
 * <p>
 * Extracts the document metadata from the {@code <geogebra>} root and its
 * {@code <construction>} child (when asked to), and emits the user-visible
 * text as XHTML paragraphs: the string literals of text object
 * {@code <expression>}s, the text runs of {@code <content>} elements (inline
 * text, tables, mind maps), element {@code <caption>}s and macro names and
 * help texts.
 */
class GeoGebraXMLHandler extends DefaultHandler {

    /**
     * A GeoGebra string literal. GeoGebra writes strings between plain
     * double quotes without any escaping, so a literal never contains one.
     */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    /**
     * Longest content JSON that is parsed; a real inline text, table or mind
     * map is a few kilobytes, anything far beyond that is not worth a tree.
     */
    private static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;
    private final boolean documentMetadata;
    private int depth = 0;

    /**
     * @param xhtml            the handler paragraphs are written to
     * @param metadata         the metadata tool names are added to
     * @param documentMetadata whether to also fill the document metadata from
     *                         the root and construction elements
     */
    GeoGebraXMLHandler(XHTMLContentHandler xhtml, Metadata metadata, boolean documentMetadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
        this.documentMetadata = documentMetadata;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (depth == 0 && "geogebra".equals(localName)) {
            if (documentMetadata) {
                setIfNotBlank(GeoGebraParser.APP_NAME, attributes.getValue("app"));
                setIfNotBlank(GeoGebraParser.APP_VERSION, attributes.getValue("version"));
                setIfNotBlank(GeoGebraParser.FORMAT_VERSION, attributes.getValue("format"));
                setIfNotBlank(GeoGebraParser.ID, attributes.getValue("id"));
            }
        } else if (depth == 1 && "construction".equals(localName)) {
            //only the document's own construction; a macro's construction is
            //nested one level deeper inside its <macro> element
            if (documentMetadata) {
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
            if (StringUtils.isBlank(toolName)) {
                toolName = attributes.getValue("cmdName");
            }
            if (!StringUtils.isBlank(toolName)) {
                metadata.add(GeoGebraParser.TOOL_NAME, toolName.trim());
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
     * Emits the string literals of an expression. A text object's expression
     * is either a single literal like {@code "some text"} or, for a dynamic
     * text, literals combined with values like {@code "Area = " + a}; the
     * literals are the user's text, everything else is geometry and skipped.
     */
    private void handleExpression(String exp) throws SAXException {
        if (exp == null || exp.indexOf('"') < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = STRING_LITERAL.matcher(exp);
        while (m.find()) {
            sb.append(m.group(1));
        }
        paragraph(sb.toString());
    }

    /**
     * Emits the text runs of a rich-text {@code content} value, a JSON array
     * of text runs like {@code [{"text":"Hello\n"}]}. All {@code text} fields
     * are collected recursively (tables and mind maps nest them), joined, and
     * emitted one paragraph per line.
     */
    private void handleContent(String val) throws SAXException {
        if (val == null) {
            return;
        }
        String trimmed = val.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH
                || (trimmed.charAt(0) != '[' && trimmed.charAt(0) != '{')) {
            //not a JSON document; a plain string carries no text runs
            return;
        }
        JsonNode root;
        try {
            root = GeoGebraParser.OBJECT_MAPPER.readTree(trimmed);
        } catch (IOException e) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String text : root.findValuesAsText("text")) {
            sb.append(text);
        }
        for (String line : sb.toString().split("\r\n|[\r\n]")) {
            paragraph(line);
        }
    }

    private void paragraph(String text) throws SAXException {
        if (!StringUtils.isBlank(text)) {
            xhtml.element("p", text.trim());
        }
    }

    private void setIfNotBlank(Property property, String value) {
        if (!StringUtils.isBlank(value)) {
            metadata.set(property, value.trim());
        }
    }
}
