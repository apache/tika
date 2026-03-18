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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.sax.XHTMLContentHandler;

/**
 * SAX handler that parses PPTX comments XML and emits comment content
 * with author attribution to an XHTML stream.
 */
class XSLFCommentsHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final CommentAuthors commentAuthors;

    private String commentAuthorId = null;
    private final StringBuilder commentBuffer = new StringBuilder();

    XSLFCommentsHandler(XHTMLContentHandler xhtml, CommentAuthors commentAuthors) {
        this.xhtml = xhtml;
        this.commentAuthors = commentAuthors;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        if ("cm".equals(localName)) {
            commentAuthorId = atts.getValue("", "authorId");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        //TODO: require that we're in <p:text>?
        commentBuffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("cm".equals(localName)) {
            xhtml.startElement("p", "class", "slide-comment");

            String authorString = commentAuthors.getName(commentAuthorId);
            String authorInitials = commentAuthors.getInitials(commentAuthorId);
            if (authorString != null || authorInitials != null) {
                xhtml.startElement("b");
                boolean authorExists = false;
                if (authorString != null) {
                    xhtml.characters(authorString);
                    authorExists = true;
                }
                if (authorExists && authorInitials != null) {
                    xhtml.characters(" (");
                }
                if (authorInitials != null) {
                    xhtml.characters(authorInitials);
                }
                if (authorExists && authorInitials != null) {
                    xhtml.characters(")");
                }
                xhtml.endElement("b");
            }
            xhtml.characters(commentBuffer.toString());
            xhtml.endElement("p");

            commentBuffer.setLength(0);
            commentAuthorId = null;
        }
    }
}
