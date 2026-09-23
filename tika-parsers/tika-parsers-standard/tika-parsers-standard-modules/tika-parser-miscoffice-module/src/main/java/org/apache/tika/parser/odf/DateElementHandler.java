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
package org.apache.tika.parser.odf;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.utils.TikaDates;

/**
 * A date element in ODF meta.xml: stored in Tika's metadata form (zone-less stays zone-less);
 * values that aren't full-precision dates are dropped, never stored raw on a DATE property.
 */
class DateElementHandler extends DefaultHandler {

    private final String namespace;
    private final String localName;
    private final Metadata metadata;
    private final Property property;
    private final StringBuilder buffer = new StringBuilder();
    private boolean inElement;

    DateElementHandler(String namespace, String localName, Metadata metadata, Property property) {
        this.namespace = namespace;
        this.localName = localName;
        this.metadata = metadata;
        this.property = property;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (namespace.equals(uri) && this.localName.equals(localName)) {
            inElement = true;
            buffer.setLength(0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (inElement) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (inElement && namespace.equals(uri) && this.localName.equals(localName)) {
            inElement = false;
            String value = TikaDates.toMetadataString(buffer.toString());
            if (value != null) {
                metadata.set(property, value);
            }
        }
    }
}
