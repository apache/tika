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
package org.apache.tika.parser.mif;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Content handler for MIF Content and Metadata.
 */
public class MIFContentHandler extends DefaultHandler {

    private final static Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    private final ContentHandler handler;
    private final Metadata metadata;
    private boolean inContent = false;
    private boolean inPage = false;
    private int bodyPageCount = 0;
    private int masterPageCount = 0;
    private int referencePageCount = 0;

    /**
     * Default content handler for MIF Content and Metadata.
     *
     * @param handler the base content handler to use.
     * @param metadata the metadata collection to populate.
     */
    MIFContentHandler(ContentHandler handler, Metadata metadata) {
        this.handler = handler;
        this.metadata = metadata;
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

        if ("PageType".equals(localName)) {
            inPage = true;
        }

        // Write our body content
        if ("Para".equals(localName)) {
            handler.startElement(XHTMLContentHandler.XHTML, "p", "p", EMPTY_ATTRIBUTES);
        }

        // Write our body content
        if ("String".equals(localName)) {
            inContent = true;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inContent) {
            handler.characters(ch, start, length);
        }

        if (inPage) {
            switch (String.valueOf(ch)) {
                case "BodyPage" :
                    bodyPageCount++;
                    break;
                case "LeftMasterPage":
                case "RightMasterPage":
                case "OtherMasterPage":
                    masterPageCount++;
                    break;
                case "ReferencePage":
                    referencePageCount++;
                    break;
                default:
                    break;
            }
        }

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("String".equals(localName)) {
            inContent = false;
        }

        if ("Para".equals(localName)) {
            handler.endElement(XHTMLContentHandler.XHTML, "p", "p");
        }

        if ("PageType".equals(localName)) {
            inPage = false;
        }
    }

    @Override
    public void endDocument() {
        metadata.set("PageCount", String.valueOf(bodyPageCount));
        metadata.set("MasterPageCount", String.valueOf(masterPageCount));
        metadata.set("ReferencePageCount", String.valueOf(referencePageCount));
        metadata.set("TotalPageCount", String.valueOf(bodyPageCount + referencePageCount + masterPageCount));
    }

}