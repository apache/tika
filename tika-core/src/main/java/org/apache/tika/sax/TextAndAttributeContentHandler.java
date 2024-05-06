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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/*
 * extends TextContentHandler, will also handle element attributes
 */
public class TextAndAttributeContentHandler extends TextContentHandler {

    public TextAndAttributeContentHandler(ContentHandler delegate) {
        this(delegate, false);
    }

    public TextAndAttributeContentHandler(
            ContentHandler delegate, boolean addSpaceBetweenElements) {
        super(delegate, addSpaceBetweenElements);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        // output element name and attributes if attributes length larger than 0.
        int attributeLength = attributes.getLength();
        if (attributeLength > 0) {
            // output element name
            char[] elementName = (localName.trim() + " ").toCharArray();
            characters(elementName, 0, elementName.length);

            // output attributes
            for (int i = 0; i < attributeLength; i++) {
                char[] attributeName = (attributes.getLocalName(i).trim() + " ").toCharArray();
                char[] attributeValue = (attributes.getValue(i).trim() + " ").toCharArray();
                characters(attributeName, 0, attributeName.length);
                characters(attributeValue, 0, attributeValue.length);
            }
        }
    }
}
