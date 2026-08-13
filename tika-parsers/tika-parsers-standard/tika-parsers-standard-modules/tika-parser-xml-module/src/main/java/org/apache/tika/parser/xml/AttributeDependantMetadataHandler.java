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

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.KeyPrefix;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * This adds a Metadata entry for a given node.
 * The textual content of the node is used as the
 * value, and the Metadata name is taken from an attribute, minted under the
 * given {@link KeyPrefix} (the name is document-controlled, so it must not be
 * a registered Property).
 */
public class AttributeDependantMetadataHandler extends DefaultHandler {

    private final Metadata metadata;

    private final String nameHoldingAttribute;
    private final KeyPrefix namePrefix;
    private final StringBuilder buffer = new StringBuilder();
    private Property property;

    public AttributeDependantMetadataHandler(Metadata metadata, String nameHoldingAttribute,
                                             KeyPrefix namePrefix) {
        this.metadata = metadata;
        this.nameHoldingAttribute = nameHoldingAttribute;
        this.namePrefix = namePrefix;
    }

    public void addMetadata(String value) {
        if (property == null) {
            // We didn't find the attribute which holds the name
            return;
        }
        if (value.length() > 0) {
            String previous = metadata.get(property);
            if (previous != null && previous.length() > 0) {
                value = previous + ", " + value;
            }
            metadata.set(property, value);
        }
    }

    public void endElement(String uri, String localName, String name) {
        addMetadata(buffer.toString());
        buffer.setLength(0);
    }

    public void startElement(String uri, String localName, String name, Attributes attributes) {
        String rawName = attributes.getValue(nameHoldingAttribute);
        if (rawName != null) {
            this.property = namePrefix.text(rawName);
        }
        // All other attributes are ignored
    }


    public void characters(char[] ch, int start, int length) {
        buffer.append(ch, start, length);
    }

}
