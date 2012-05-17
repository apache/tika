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
import org.xml.sax.SAXException;

/**
 * SAX event handler that maps the contents of an XML attribute into
 * a metadata field.
 *
 * @since Apache Tika 0.10
 */
public class AttributeMetadataHandler extends AbstractMetadataHandler {

    private final String uri;

    private final String localName;

    public AttributeMetadataHandler(
            String uri, String localName, Metadata metadata, String name) {
        super(metadata, name);
        this.uri = uri;
        this.localName = localName;
    }
    public AttributeMetadataHandler(
          String uri, String localName, Metadata metadata, Property property) {
      super(metadata, property);
      this.uri = uri;
      this.localName = localName;
  }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        for (int i = 0; i < attributes.getLength(); i++) {
            if (attributes.getURI(i).equals(this.uri)
                    && attributes.getLocalName(i).equals(this.localName)) {
                addMetadata(attributes.getValue(i).trim());
            }
        }
    }

}
