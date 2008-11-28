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
import java.util.Set;
import java.util.HashSet;

import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;

/**
 * Content handler decorator that only passes the
 * {@link #characters(char[], int, int)} and
 * {@link #ignorableWhitespace(char[], int, int)} events to
 * the decorated content handler.
 * It additionally inserts a \n character at the end of each XHTML block element
 * (</p>, </div>,...).
 * This content handler should be used as delegate for {@link BodyContentHandler}.
 */
public class XHTMLToTextContentHandler extends TextContentHandler {

    public XHTMLToTextContentHandler(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (
                !"tr".equals(lastLocalName) && 
                ("td".equals(localName) || "th".equals(localName))
        ) characters(TAB,0,TAB.length);
        lastLocalName=localName;
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if (HTML_BLOCK_TAGS.contains(localName)) characters(NL,0,NL.length);
    }

    private String lastLocalName=null;

    private static final char[] NL=new char[]{'\n'};
    private static final char[] TAB=new char[]{'\t'};

    // special XHTML tags that start new lines
    private static final Set<String> HTML_BLOCK_TAGS=new HashSet<String>(Arrays.asList(
            "p","div","fieldset","table","form",
            "pre","blockquote","address",
            "ul","ol","dl","li","dt","dd",
            "h1","h2","h3","h4","h5","h6",
            "noscript","noframes",
            "hr","br","tr"
    ));

}
