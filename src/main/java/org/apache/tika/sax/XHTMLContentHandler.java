/**
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

import org.apache.tika.metadata.Metadata;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that simplifies the task of producing XHTML
 * events for Tika content parsers.
 */
public class XHTMLContentHandler extends ContentHandlerDecorator {

    /**
     * The XHTML namespace URI
     */
    public static final String XHTML = "http://www.w3.org/1999/xhtml";

    /**
     * Metadata associated with the document. Used to fill in the
     * &lt;head/&gt; section.
     */
    private final Metadata metadata;

    /**
     * Flag to indicate whether the document element has been started.
     */
    private boolean started = false;

    public XHTMLContentHandler(ContentHandler handler, Metadata metadata) {
        super(handler);
        this.metadata = metadata;
    }

    /**
     * Starts an XHTML document by setting up the namespace mappings.
     * The standard XHTML prefix is generated lazily when the first
     * element is started.
     */
    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        startPrefixMapping("", XHTML);
    }

    /**
     * Generates the following XHTML prefix when called for the first time:
     * <pre>
     * &lt;html&gt;
     *   &lt;head&gt;
     *     &lt;title&gt;...&lt;/title&gt;
     *   &lt;/head&gt;
     *   &lt;body&gt;
     * </pre>
     */
    private void lazyStartDocument() throws SAXException {
        if (!started) {
            started = true;
            startElement("html");
            startElement("head");
            startElement("title");
            String title = metadata.get(Metadata.TITLE);
            if (title != null && title.length() > 0) {
                characters(title);
            }
            endElement("title");
            endElement("head");
            startElement("body");
        }
    }

    /**
     * Ends the XHTML document by writing the following footer and
     * clearing the namespace mappings:
     * <pre>
     *   &lt;/body&gt;
     * &lt;/html&gt;
     * </pre>
     */
    @Override
    public void endDocument() throws SAXException {
        lazyStartDocument();
        endElement("body");
        endElement("html");
        endPrefixMapping("");
        super.endDocument();
    }

    @Override
    public void startElement(
            String uri, String local, String name, Attributes attributes)
            throws SAXException {
        lazyStartDocument();
        super.startElement(uri, local, name, attributes);
    }

    public void startElement(String name) throws SAXException {
        startElement(XHTML, name, name, new AttributesImpl());
    }

    public void startElement(String name, String attribute, String value)
            throws SAXException {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(XHTML, attribute, attribute, "CDATA", value);
        startElement(XHTML, name, name, attributes);
    }

    public void endElement(String name) throws SAXException {
        endElement(XHTML, name, name);
    }

    public void characters(String characters) throws SAXException {
        characters(characters.toCharArray(), 0, characters.length());
    }

    public void element(String name, String value) throws SAXException {
        startElement(name);
        characters(value);
        endElement(name);
    }

}
