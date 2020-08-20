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
import org.apache.tika.metadata.Property;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This adds Metadata entries with a specified name for
 *  the textual content of a node (if present), and 
 *  all attribute values passed through the matcher
 *  (but not their names). 
 *
 * @deprecated Use the {@link AttributeMetadataHandler} and
 *             {@link ElementMetadataHandler} classes instead
 */
public class MetadataHandler extends DefaultHandler {

    private final Metadata metadata;

    private final Property property;
    private final String name;

    private final StringBuilder buffer = new StringBuilder();

    public MetadataHandler(Metadata metadata, String name) {
        this.metadata = metadata;
        this.property = null;
        this.name = name;
    }
    public MetadataHandler(Metadata metadata, Property property) {
       this.metadata = metadata;
       this.property = property;
       this.name = property.getName();
   }

    public void addMetadata(String value) {
        if (value.length() > 0) {
            String previous = metadata.get(name);
            if (previous != null && previous.length() > 0) {
                value = previous + ", " + value;
            }
            
            if (this.property != null) {
               metadata.set(property, value);
            } else {
               metadata.set(name, value);
            }
        }
    }

    public void endElement(String uri, String localName, String name) {
        addMetadata(buffer.toString());
        buffer.setLength(0);
    }

    public void startElement(
            String uri, String localName, String name, Attributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++) {
            addMetadata(attributes.getValue(i));
        }
    }

    
    public void characters(char[] ch, int start, int length) {
        buffer.append(ch, start, length);
    }

}
