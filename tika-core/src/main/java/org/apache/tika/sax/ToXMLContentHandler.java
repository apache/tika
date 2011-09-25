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
package org.apache.tika.sax;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * SAX event handler that serializes the XML document to a character stream.
 * The incoming SAX events are expected to be well-formed (properly nested,
 * etc.) and to explicitly include namespace declaration attributes and
 * corresponding namespace prefixes in element and attribute names.
 *
 * @since Apache Tika 0.10
 */
public class ToXMLContentHandler extends ToTextContentHandler {

    private static class ElementInfo {

        private final ElementInfo parent;

        private final Map<String, String> namespaces;

        public ElementInfo(ElementInfo parent, Map<String, String> namespaces) {
            this.parent = parent;
            if (namespaces.isEmpty()) {
                this.namespaces = Collections.emptyMap();
            } else {
                this.namespaces = new HashMap<String, String>(namespaces);
            }
        }

        public String getPrefix(String uri) throws SAXException {
            String prefix = namespaces.get(uri);
            if (prefix != null) {
                return prefix;
            } else if (parent != null) {
                return parent.getPrefix(uri);
            } else if (uri == null || uri.length() == 0) {
                return "";
            } else {
                throw new SAXException("Namespace " + uri + " not declared");
            }
        }

        public String getQName(String uri, String localName)
                throws SAXException {
            String prefix = getPrefix(uri);
            if (prefix.length() > 0) {
                return prefix + ":" + localName;
            } else {
                return localName;
            }
        }

    }

    private final String encoding;

    protected boolean inStartElement = false;

    protected final Map<String, String> namespaces =
        new HashMap<String, String>();

    private ElementInfo currentElement;

    /**
     * Creates an XML serializer that writes to the given byte stream
     * using the given character encoding.
     *
     * @param stream output stream
     * @param encoding output encoding
     * @throws UnsupportedEncodingException if the encoding is unsupported
     */
    public ToXMLContentHandler(OutputStream stream, String encoding)
            throws UnsupportedEncodingException {
        super(stream, encoding);
        this.encoding = encoding;
    }

    public ToXMLContentHandler(String encoding) {
        super();
        this.encoding = encoding;
    }

    public ToXMLContentHandler() {
        super();
        this.encoding = null;
    }

    /**
     * Writes the XML prefix.
     */
    @Override
    public void startDocument() throws SAXException {
        if (encoding != null) {
            write("<?xml version=\"1.0\" encoding=\"");
            write(encoding);
            write("\"?>\n");
        }

        currentElement = null;
        namespaces.clear();
    }

    @Override
    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        namespaces.put(uri, prefix);
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        lazyCloseStartElement();

        currentElement = new ElementInfo(currentElement, namespaces);

        write('<');
        write(currentElement.getQName(uri, localName));

        for (int i = 0; i < atts.getLength(); i++) {
            write(' ');
            write(currentElement.getQName(atts.getURI(i), atts.getLocalName(i)));
            write('=');
            write('"');
            char[] ch = atts.getValue(i).toCharArray();
            writeEscaped(ch, 0, ch.length, true);
            write('"');
        }

        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            write(' ');
            write("xmlns");
            String prefix = entry.getValue();
            if (prefix.length() > 0) {
                write(':');
                write(prefix);
            }
            write('=');
            write('"');
            char[] ch = entry.getKey().toCharArray();
            writeEscaped(ch, 0, ch.length, true);
            write('"');
        }
        namespaces.clear();

        inStartElement = true;
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (inStartElement) {
            write(" />");
            inStartElement = false;
        } else {
            write("</");
            write(qName);
            write('>');
        }

        namespaces.clear();
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        lazyCloseStartElement();
        writeEscaped(ch, start, start + length, false);
    }

    private void lazyCloseStartElement() throws SAXException {
        if (inStartElement) {
            write('>');
            inStartElement = false;
        }
    }

    /**
     * Writes the given character as-is.
     *
     * @param ch character to be written
     * @throws SAXException if the character could not be written
     */
    protected void write(char ch) throws SAXException {
        super.characters(new char[] { ch }, 0, 1);
    }

    /**
     * Writes the given string of character as-is.
     *
     * @param string string of character to be written
     * @throws SAXException if the character string could not be written
     */
    protected void write(String string) throws SAXException {
        super.characters(string.toCharArray(), 0, string.length());
    }

    /**
     * Writes the given characters as-is followed by the given entity.
     *
     * @param ch character array
     * @param from start position in the array
     * @param to end position in the array
     * @param entity entity code
     * @return next position in the array,
     *         after the characters plus one entity
     * @throws SAXException if the characters could not be written
     */
    private int writeCharsAndEntity(char[] ch, int from, int to, String entity)
            throws SAXException {
        super.characters(ch, from, to - from);
        write('&');
        write(entity);
        write(';');
        return to + 1;
    }

    /**
     * Writes the given characters with XML meta characters escaped.
     *
     * @param ch character array
     * @param from start position in the array
     * @param to end position in the array
     * @param attribute whether the characters should be escaped as
     *                  an attribute value or normal character content
     * @throws SAXException if the characters could not be written
     */
    private void writeEscaped(char[] ch, int from, int to, boolean attribute)
            throws SAXException {
        int pos = from;
        while (pos < to) {
            if (ch[pos] == '<') {
                from = pos = writeCharsAndEntity(ch, from, pos, "lt");
            } else if (ch[pos] == '>') {
                from = pos = writeCharsAndEntity(ch, from, pos, "gt");
            } else if (ch[pos] == '&') {
                from = pos = writeCharsAndEntity(ch, from, pos, "amp");
            } else if (attribute && ch[pos] == '"') {
                from = pos = writeCharsAndEntity(ch, from, pos, "quot");
            } else {
                pos++;
            }
        }
        super.characters(ch, from, to - from);
    }

}
