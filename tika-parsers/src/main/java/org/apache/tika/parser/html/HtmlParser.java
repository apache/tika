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
package org.apache.tika.parser.html;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * HTML parser. Uses CyberNeko to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class HtmlParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, Map<String, Object> context)
            throws IOException, SAXException, TikaException {
        // Protect the stream from being closed by CyberNeko
        stream = new CloseShieldInputStream(stream);

        // Prepare the input source using the encoding hint if available
        InputSource source = new InputSource(stream); 
        String encoding = metadata.get(Metadata.CONTENT_ENCODING); 
        if (encoding != null) { 
            source.setEncoding(encoding);
        }

        // Parse the HTML document
        org.ccil.cowan.tagsoup.Parser parser =
            new org.ccil.cowan.tagsoup.Parser();
        parser.setContentHandler(new XHTMLDowngradeHandler(
                new HtmlHandler(this, handler, metadata)));
        parser.parse(source);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        Map<String, Object> context = Collections.emptyMap();
        parse(stream, handler, metadata, context);
    }

    /**
     * Maps "safe" HTML element names to semantic XHTML equivalents. If the
     * given element is unknown or deemed unsafe for inclusion in the parse
     * output, then this method returns <code>null</code> and the element
     * will be ignored but the content inside it is still processed. See
     * the {@link #isDiscardElement(String)} method for a way to discard
     * the entire contents of an element.
     * <p>
     * Subclasses can override this method to customize the default mapping.
     *
     * @since Apache Tika 0.5
     * @param name HTML element name (upper case)
     * @return XHTML element name (lower case), or
     *         <code>null</code> if the element is unsafe 
     */
    protected String mapSafeElement(String name) {
        // Based on http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd

        if ("H1".equals(name)) return "h1";
        if ("H2".equals(name)) return "h2";
        if ("H3".equals(name)) return "h3";
        if ("H4".equals(name)) return "h4";
        if ("H5".equals(name)) return "h5";
        if ("H6".equals(name)) return "h6";

        if ("P".equals(name)) return "p";
        if ("PRE".equals(name)) return "pre";
        if ("BLOCKQUOTE".equals(name)) return "blockquote";

        if ("UL".equals(name)) return "ul";
        if ("OL".equals(name)) return "ol";
        if ("MENU".equals(name)) return "ul";
        if ("LI".equals(name)) return "li";
        if ("DL".equals(name)) return "dl";
        if ("DT".equals(name)) return "dt";
        if ("DD".equals(name)) return "dd";

        if ("TABLE".equals(name)) return "table";
        if ("THEAD".equals(name)) return "thead";
        if ("TBODY".equals(name)) return "tbody";
        if ("TR".equals(name)) return "tr";
        if ("TH".equals(name)) return "th";
        if ("TD".equals(name)) return "td";

        return null;
    }

    /**
     * Checks whether all content within the given HTML element should be
     * discarded instead of including it in the parse output. Subclasses
     * can override this method to customize the set of discarded elements.
     *
     * @since Apache Tika 0.5
     * @param name HTML element name (upper case)
     * @return <code>true</code> if content inside the named element
     *         should be ignored, <code>false</code> otherwise
     */
    protected boolean isDiscardElement(String name) {
        return "STYLE".equals(name) || "SCRIPT".equals(name);
    }

}
