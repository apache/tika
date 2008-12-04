/**
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

import java.util.Map;
import java.util.Collections;
import javax.xml.namespace.QName;

import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that maps element <code>QName</code>s using
 * a <code>Map</code>. Not mappable elements are not forwarded.
 * Attributes may also be mapped (for each element different using
 * a <code>Map</code> for attributes), not mappable attributes are not
 * forwarded. The default is to not map any attributes and therefore do
 * not forward any of them.
 */
public class ElementMappingContentHandler extends ContentHandlerDecorator {

    private final Map<QName, TargetElement> mappings;

    public ElementMappingContentHandler(
            ContentHandler handler, Map<QName, TargetElement> mappings) {
        super(handler);
        this.mappings = mappings;
    }

    @Override
    public void startElement(
            String namespaceURI, String localName, String qName,
            Attributes atts) throws SAXException {
        TargetElement mapping =
            mappings.get(new QName(namespaceURI, localName));
        if (mapping != null) {
            QName tag = mapping.getMappedTagName();
            super.startElement(
                    tag.getNamespaceURI(), tag.getLocalPart(),
                    getQNameAsString(tag), mapping.mapAttributes(atts));
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        TargetElement mapping =
            mappings.get(new QName(namespaceURI, localName));
        if (mapping != null) {
            QName tag=mapping.getMappedTagName();
            super.endElement(
                    tag.getNamespaceURI(), tag.getLocalPart(),
                    getQNameAsString(tag));
        }
    }

    protected static final String getQNameAsString(QName qname) {
        String prefix = qname.getPrefix();
        if (prefix.length() > 0) {
            return prefix + ":" + qname.getLocalPart();
        } else {
            return qname.getLocalPart(); 
        }
    }

    public static class TargetElement {

        /**
         * Creates an TargetElement, attributes of this element will
         * be mapped as specified
         */
        public TargetElement(
                QName mappedTagName, Map<QName, QName> attributesMapping) {
            this.mappedTagName = mappedTagName;
            this.attributesMapping = attributesMapping;
        }

        /**
         * A shortcut that automatically creates the QName object
         */
        public TargetElement(
                String mappedTagURI, String mappedTagLocalName,
                Map<QName, QName> attributesMapping) {
            this(new QName(mappedTagURI, mappedTagLocalName), attributesMapping);
        }

        /**
         * Creates an TargetElement with no attributes, all attributes
         * will be deleted from SAX stream
         */
        public TargetElement(QName mappedTagName) {
            this(mappedTagName, Collections.<QName,QName>emptyMap());
        }

        /** A shortcut that automatically creates the QName object */
        public TargetElement(String mappedTagURI, String mappedTagLocalName) {
            this(mappedTagURI, mappedTagLocalName,
                    Collections.<QName,QName>emptyMap());
        }

        public QName getMappedTagName() {
            return mappedTagName;
        }

        public Map<QName, QName> getAttributesMapping() {
            return attributesMapping;
        }

        public Attributes mapAttributes(final Attributes atts) {
            AttributesImpl natts = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                QName name = attributesMapping.get(
                        new QName(atts.getURI(i), atts.getLocalName(i)));
                if (name!=null) {
                    natts.addAttribute(
                            name.getNamespaceURI(), name.getLocalPart(),
                            getQNameAsString(name),
                            atts.getType(i), atts.getValue(i));
                }
            }
            return natts;
        }

        private final QName mappedTagName;

        private final Map<QName, QName> attributesMapping;

    }

}
