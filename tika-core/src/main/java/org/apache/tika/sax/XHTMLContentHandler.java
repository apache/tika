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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that simplifies the task of producing XHTML
 * events for Tika content parsers.
 */
public class XHTMLContentHandler extends SafeContentHandler {

    /**
     * The XHTML namespace URI
     */
    public static final String XHTML = "http://www.w3.org/1999/xhtml";

    /**
     * The newline character that gets inserted after block elements.
     */
    private static final char[] NL = new char[] { '\n' };

    /**
     * The tab character gets inserted before table cells and list items.
     */
    private static final char[] TAB = new char[] { '\t' };

    /**
     * The elements that are in the <head> section.
     */
    private static final Set<String> HEAD =
        unmodifiableSet("title", "link", "base", "meta");

    /**
     * The elements that are automatically emitted by lazyStartHead, so
     * skip them if they get sent to startElement/endElement by mistake.
     */
    private static final Set<String> AUTO =
        unmodifiableSet("html", "head", "body", "frameset");

    /**
     * The elements that get prepended with the {@link #TAB} character.
     */
    private static final Set<String> INDENT =
        unmodifiableSet("li", "dd", "dt", "td", "th", "frame");

    /**
     * The elements that get appended with the {@link #NL} character.
     */
    public static final Set<String> ENDLINE = unmodifiableSet(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "ul", "ol", "dl",
            "pre", "hr", "blockquote", "address", "fieldset", "table", "form",
            "noscript", "li", "dt", "dd", "noframes", "br", "tr", "select", "option");

    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    private static Set<String> unmodifiableSet(String... elements) {
        return Collections.unmodifiableSet(
                new HashSet<String>(Arrays.asList(elements)));
    }

    /**
     * Metadata associated with the document. Used to fill in the
     * &lt;head/&gt; section.
     */
    private final Metadata metadata;

    /**
     * Flags to indicate whether the document head element has been started/ended.
     */
    private boolean headStarted = false;
    private boolean headEnded = false;
    private boolean useFrameset = false;
    
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
    private void lazyStartHead() throws SAXException {
        if (!headStarted) {
            headStarted = true;
            
            // Call directly, so we don't go through our startElement(), which will
            // ignore these elements.
            super.startElement(XHTML, "html", "html", EMPTY_ATTRIBUTES);
            newline();
            super.startElement(XHTML, "head", "head", EMPTY_ATTRIBUTES);
            newline();
        }
    }

    /**
     * Generates the following XHTML prefix when called for the first time:
     * <pre>
     * &lt;html&gt;
     *   &lt;head&gt;
     *     &lt;title&gt;...&lt;/title&gt;
     *   &lt;/head&gt;
     *   &lt;body&gt; (or &lt;frameset&gt;
     * </pre>
     */
    private void lazyEndHead(boolean isFrameset) throws SAXException {
        lazyStartHead();
        
        if (!headEnded) {
            headEnded = true;
            useFrameset = isFrameset;
            
            // TIKA-478: Emit all metadata values (other than title). We have to call
            // startElement() and characters() directly to avoid recursive problems.
            for (String name : metadata.names()) {
                if (name.equals("title")) {
                    continue;
                }
                
                for (String value : metadata.getValues(name)) {
                    // Putting null values into attributes causes problems, but is
                    // allowed by Metadata, so guard against that.
                    if (value != null) {
                        AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", "name", "name", "CDATA", name);
                        attributes.addAttribute("", "content", "content", "CDATA", value);
                        super.startElement(XHTML, "meta", "meta", attributes);
                        super.endElement(XHTML, "meta", "meta");
                        newline();
                    }
                }
            }
            
            super.startElement(XHTML, "title", "title", EMPTY_ATTRIBUTES);
            String title = metadata.get(TikaCoreProperties.TITLE);
            if (title != null && title.length() > 0) {
                char[] titleChars = title.toCharArray();
                super.characters(titleChars, 0, titleChars.length);
            } else {
                // TIKA-725: Prefer <title></title> over <title/>
                super.characters(new char[0], 0, 0);
            }
            super.endElement(XHTML, "title", "title");
            newline();
            
            super.endElement(XHTML, "head", "head");
            newline();
            
            if (useFrameset) {
                super.startElement(XHTML, "frameset", "frameset", EMPTY_ATTRIBUTES);
            } else {
                super.startElement(XHTML, "body", "body", EMPTY_ATTRIBUTES);
            }
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
        lazyEndHead(useFrameset);
        
        if (useFrameset) {
            super.endElement(XHTML, "frameset", "frameset");
        } else {
            super.endElement(XHTML, "body", "body");
        }
        
        super.endElement(XHTML, "html", "html");
        
        endPrefixMapping("");
        super.endDocument();
    }

    /**
     * Starts the given element. Table cells and list items are automatically
     * indented by emitting a tab character as ignorable whitespace.
     */
    @Override
    public void startElement(
            String uri, String local, String name, Attributes attributes)
            throws SAXException {
        
        if (name.equals("frameset")) {
            lazyEndHead(true);
        } else if (!AUTO.contains(name)) {
            if (HEAD.contains(name)) {
                lazyStartHead();
            } else {
                lazyEndHead(false);
            }

            if (XHTML.equals(uri) && INDENT.contains(name)) {
                ignorableWhitespace(TAB, 0, TAB.length);
            }
            
            super.startElement(uri, local, name, attributes);
        }
    }

    /**
     * Ends the given element. Block elements are automatically followed
     * by a newline character.
     */
    @Override
    public void endElement(String uri, String local, String name) throws SAXException {
        if (!AUTO.contains(name)) {
            super.endElement(uri, local, name);
            if (XHTML.equals(uri) && ENDLINE.contains(name)) {
                newline();
            }
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        lazyEndHead(useFrameset);
        super.characters(ch, start, length);
    }

    //------------------------------------------< public convenience methods >

    public void startElement(String name) throws SAXException {
        startElement(XHTML, name, name, EMPTY_ATTRIBUTES);
    }

    public void startElement(String name, String attribute, String value)
            throws SAXException {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", attribute, attribute, "CDATA", value);
        startElement(XHTML, name, name, attributes);
    }

    public void startElement(String name, AttributesImpl attributes)
            throws SAXException {
        startElement(XHTML, name, name, attributes);
    }

    public void endElement(String name) throws SAXException {
        endElement(XHTML, name, name);
    }

    public void characters(String characters) throws SAXException {
        if (characters != null && characters.length() > 0) {
            characters(characters.toCharArray(), 0, characters.length());
        }
    }

    public void newline() throws SAXException {
        ignorableWhitespace(NL, 0, NL.length);
    }

    /**
     * Emits an XHTML element with the given text content. If the given
     * text value is null or empty, then the element is not written.
     *
     * @param name XHTML element name
     * @param value element value, possibly <code>null</code>
     * @throws SAXException if the content element could not be written
     */
    public void element(String name, String value) throws SAXException {
        if (value != null && value.length() > 0) {
            startElement(name);
            characters(value);
            endElement(name);
        }
    }

}
