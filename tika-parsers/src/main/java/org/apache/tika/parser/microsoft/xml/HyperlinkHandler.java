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
package org.apache.tika.parser.microsoft.xml;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

class HyperlinkHandler extends DefaultHandler {
    private final ContentHandler handler;
    private final String namespace;
    boolean inLink = false;
    StringBuilder linkCache = new StringBuilder();
    String href = null;

    public HyperlinkHandler(ContentHandler handler, String namespace) {
        this.handler = handler;
        this.namespace = namespace;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {
        if (AbstractXML2003Parser.WORD_ML_URL.equals(uri)) {
            if (AbstractXML2003Parser.HLINK.equals(localName)) {
                href = attrs.getValue(AbstractXML2003Parser.WORD_ML_URL,
                        AbstractXML2003Parser.HLINK_DEST);
                if (href != null) {
                    href = href.trim();
                }
                inLink = true;
            }
        }
    }

    @Override
    public void characters(char[] str , int offset, int len) throws SAXException {
        if (inLink) {
            linkCache.append(str, offset, len);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (! namespace.equals(uri)) {
            return;
        }
        if (AbstractXML2003Parser.HLINK.equals(localName)) {
            AttributesImpl attrs = new AttributesImpl();
            if (href != null) {
                attrs.addAttribute(XHTMLContentHandler.XHTML,
                        AbstractXML2003Parser.HREF,
                        AbstractXML2003Parser.HREF,
                        AbstractXML2003Parser.CDATA,
                        href);
            }
            handler.startElement(XHTMLContentHandler.XHTML,
                    AbstractXML2003Parser.A,
                    AbstractXML2003Parser.A,
                    attrs);
            String linkString = linkCache.toString();
            //can't be null I don't think
            if (linkString != null) {
                char[] chars = linkString.trim().toCharArray();
                handler.characters(chars, 0, chars.length);
            }
            handler.endElement(XHTMLContentHandler.XHTML,
                    AbstractXML2003Parser.A,
                    AbstractXML2003Parser.A);
            //reset link cache and inLink
            linkCache.setLength(0);
            inLink = false;
            href = null;

        }
    }

}

