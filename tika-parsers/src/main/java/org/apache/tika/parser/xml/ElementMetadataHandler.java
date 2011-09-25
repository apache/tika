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
package org.apache.tika.parser.xml;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.Attributes;

/**
 * SAX event handler that maps the contents of an XML element into
 * a metadata field.
 *
 * @since Apache Tika 0.10
 */
public class ElementMetadataHandler extends AbstractMetadataHandler {

    private final String uri;

    private final String localName;

    private final StringBuilder buffer = new StringBuilder();

    private int matchLevel = 0;

    public ElementMetadataHandler(
            String uri, String localName, Metadata metadata, String name) {
        super(metadata, name);
        this.uri = uri;
        this.localName = localName;
    }

    protected boolean isMatchingElement(String uri, String localName) {
        return uri.equals(this.uri) && localName.equals(this.localName);
    }

    @Override
    public void startElement(
            String uri, String localName, String name, Attributes attributes) {
        if (isMatchingElement(uri, localName)) {
            matchLevel++;
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) {
        if (isMatchingElement(uri, localName)) {
            matchLevel--;
            if (matchLevel == 0) {
                addMetadata(buffer.toString().trim());
                buffer.setLength(0);
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (matchLevel > 0) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        characters(ch, start, length);
    }

}
