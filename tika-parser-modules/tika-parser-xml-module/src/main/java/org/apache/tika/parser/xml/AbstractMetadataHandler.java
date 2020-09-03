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

import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Base class for SAX handlers that map SAX events into document metadata.
 *
 * @since Apache Tika 0.10
 */
class AbstractMetadataHandler extends DefaultHandler {

    private final Metadata metadata;
    private final Property property;
    private final String name;

    protected AbstractMetadataHandler(Metadata metadata, String name) {
        this.metadata = metadata;
        this.property = null;
        this.name = name;
    }
    protected AbstractMetadataHandler(Metadata metadata, Property property) {
       this.metadata = metadata;
       this.property = property;
       this.name = property.getName();
   }

    /**
     * Adds the given metadata value. The value is ignored if it is
     * <code>null</code> or empty. If the metadata entry already exists,
     * then the given value is appended to it with a comma as the separator.
     *
     * @param value metadata value
     */
    protected void addMetadata(String value) {
        if (value != null && value.length() > 0) {
            if (metadata.isMultiValued(name)) {
                // Add the value, assuming it's not already there
                List<String> previous = Arrays.asList(metadata.getValues(name));
                if (!previous.contains(value)) {
                    if (property != null) {
                       metadata.add(property, value);
                    } else {
                       metadata.add(name, value);
                    }
                }
            } else {
                // Set the value, assuming it's not already there
                String previous = metadata.get(name);
                if (previous != null && previous.length() > 0) {
                    if (!previous.equals(value)) {
                       if (property != null) {
                          if (property.isMultiValuePermitted()) {
                              metadata.add(property, value);
                          } else {
                              // Replace the existing value if isMultiValuePermitted is false
                              metadata.set(property, value);
                          }
                       } else {
                          metadata.add(name, value);
                       }
                    }
                } else {
                   if (property != null) {
                      metadata.set(property, value);
                   } else {
                      metadata.set(name, value);
                   }
                }
            }
        }
    }
}
