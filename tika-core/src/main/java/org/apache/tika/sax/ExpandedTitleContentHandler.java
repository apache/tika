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

import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Content handler decorator which wraps a {@link TransformerHandler} in order to 
 * allow the <code>TITLE</code> tag to render as <code>&lt;title&gt;&lt;/title&gt;</code>
 * rather than <code>&lt;title/&gt;</code> which is accomplished
 * by calling the {@link TransformerHandler#characters(char[], int, int)} method
 * with a <code>length</code> of 1 but a zero length char array.
 * <p>
 * This workaround is an unfortunate circumstance of the limitations imposed by the
 * implementation of the XML serialization code in the JDK brought over from
 * the xalan project which no longer allows for the specification of an 
 * alternate <code>content-handler</code> via xslt templates or other means.
 * 
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-725">TIKA-725</a>
 */
public class ExpandedTitleContentHandler extends ContentHandlerDecorator {
    
    private boolean isTitleTagOpen;
    private static final String TITLE_TAG = "TITLE";
    
    public ExpandedTitleContentHandler() {
        super();
    }

    public ExpandedTitleContentHandler(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        isTitleTagOpen = false;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) throws SAXException {
        super.startElement(uri, localName, qName, atts);
        if (TITLE_TAG.equalsIgnoreCase(localName) && XHTMLContentHandler.XHTML.equals(uri)) {
            isTitleTagOpen = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);
        if (TITLE_TAG.equalsIgnoreCase(localName) && XHTMLContentHandler.XHTML.equals(uri)) {
            isTitleTagOpen = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (isTitleTagOpen && length == 0) {
            // Hack to close the title tag
            try {
                super.characters(new char[0], 0, 1);
            } catch (ArrayIndexOutOfBoundsException e) {
                // Expected, just wanted to close the title tag
            }
        } else {
            super.characters(ch, start, length);
        }
    }

}
