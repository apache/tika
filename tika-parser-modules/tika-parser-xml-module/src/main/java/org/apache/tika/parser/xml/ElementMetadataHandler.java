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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;

/**
 * SAX event handler that maps the contents of an XML element into
 * a metadata field.
 *
 * @since Apache Tika 0.10
 */
public class ElementMetadataHandler extends AbstractMetadataHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ElementMetadataHandler.class);

    private static final String LOCAL_NAME_RDF_BAG = "Bag";
    private static final String LOCAL_NAME_RDF_LI = "li";
    private static final String URI_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private final String uri;

    private final String localName;

    private final Metadata metadata;

    private final String name;
    private Property targetProperty;

    private final boolean allowDuplicateValues;
    private final boolean allowEmptyValues;

    /**
     * The buffer used to capture characters when inside a bag li element.
     */
    private final StringBuilder bufferBagged = new StringBuilder();

    /**
     * The buffer used to capture characters inside standard elements.
     */
    private final StringBuilder bufferBagless = new StringBuilder();

    /**
     * Whether or not the value was found in a standard element structure or inside a bag.
     */
    private boolean isBagless = true;

    private int matchLevel = 0;
    private int parentMatchLevel = 0;

    /**
     * Constructor for string metadata keys.
     *
     * @param uri the uri of the namespace of the element
     * @param localName the local name of the element
     * @param metadata the Tika metadata object to populate
     * @param name the Tika metadata field key
     */
    public ElementMetadataHandler(
            String uri, String localName, Metadata metadata, String name) {
        super(metadata, name);
        this.uri = uri;
        this.localName = localName;
        this.metadata = metadata;
        this.name = name;
        this.allowDuplicateValues = false;
        this.allowEmptyValues = false;
        LOG.trace("created simple handler for {}", this.name);
    }

    /**
     * Constructor for string metadata keys which allows change of behavior
     * for duplicate and empty entry values.
     *
     * @param uri the uri of the namespace of the element
     * @param localName the local name of the element
     * @param metadata the Tika metadata object to populate
     * @param name the Tika metadata field key
     * @param allowDuplicateValues add duplicate values to the Tika metadata
     * @param allowEmptyValues add empty values to the Tika metadata
     */
    public ElementMetadataHandler(
            String uri, String localName, Metadata metadata, String name, boolean allowDuplicateValues, boolean allowEmptyValues) {
        super(metadata, name);
        this.uri = uri;
        this.localName = localName;
        this.metadata = metadata;
        this.name = name;
        this.allowDuplicateValues = allowDuplicateValues;
        this.allowEmptyValues = allowEmptyValues;
        LOG.trace("created simple handler for {}", this.name);
    }

    /**
     * Constructor for Property metadata keys.
     *
     * @param uri the uri of the namespace of the element
     * @param localName the local name of the element
     * @param metadata the Tika metadata object to populate
     * @param targetProperty the Tika metadata Property key
     */
    public ElementMetadataHandler(
            String uri, String localName, Metadata metadata, Property targetProperty) {
        super(metadata, targetProperty);
        this.uri = uri;
        this.localName = localName;
        this.metadata = metadata;
        this.targetProperty = targetProperty;
        this.name = targetProperty.getName();
        this.allowDuplicateValues = false;
        this.allowEmptyValues = false;
        LOG.trace("created property handler for {}", this.name);
    }

    /**
     * Constructor for Property metadata keys which allows change of behavior
     * for duplicate and empty entry values.
     *
     * @param uri the uri of the namespace of the element
     * @param localName the local name of the element
     * @param metadata the Tika metadata object to populate
     * @param targetProperty the Tika metadata Property key
     * @param allowDuplicateValues add duplicate values to the Tika metadata
     * @param allowEmptyValues add empty values to the Tika metadata
     */
    public ElementMetadataHandler(
            String uri, String localName, Metadata metadata, Property targetProperty, boolean allowDuplicateValues, boolean allowEmptyValues) {
        super(metadata, targetProperty);
        this.uri = uri;
        this.localName = localName;
        this.metadata = metadata;
        this.targetProperty = targetProperty;
        this.name = targetProperty.getName();
        this.allowDuplicateValues = allowDuplicateValues;
        this.allowEmptyValues = allowEmptyValues;
        LOG.trace("created property handler for {}", this.name);
    }

    protected boolean isMatchingParentElement(String uri, String localName) {
        return (uri.equals(this.uri) && localName.equals(this.localName));
    }

    protected boolean isMatchingElement(String uri, String localName) {
        // match if we're inside the parent element or within some bag element
        return (uri.equals(this.uri) && localName.equals(this.localName)) ||
                (parentMatchLevel > 0 &&
                        ((uri.equals(URI_RDF) && localName.equals(LOCAL_NAME_RDF_BAG)) ||
                        (uri.equals(URI_RDF) && localName.equals(LOCAL_NAME_RDF_LI))
                )
        );
    }

    @Override
    public void startElement(
            String uri, String localName, String name, Attributes attributes) {
        if (isMatchingElement(uri, localName)) {
            matchLevel++;
        }
        if (isMatchingParentElement(uri, localName)) {
            parentMatchLevel++;
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) {
        if (isMatchingParentElement(uri, localName)) {
            parentMatchLevel--;
        }
        if (isMatchingElement(uri, localName)) {
            matchLevel--;
            if (matchLevel == 2) {
                // we're inside a bag li element, add the bagged buffer
                addMetadata(bufferBagged.toString().trim());
                bufferBagged.setLength(0);
                isBagless = false;
            }
            if (matchLevel == 0 && isBagless) {
                String valueBagless = bufferBagless.toString();
                if (valueBagless.length() > 0 && !valueBagless.contains(LOCAL_NAME_RDF_BAG)) {
                    // we're in a standard element, add the bagless buffer
                    addMetadata(valueBagless.trim());
                    bufferBagless.setLength(0);
                }
                isBagless = true;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        // We need to append to both buffers since we don't if we're inside a bag until we're done
        if (parentMatchLevel > 0 && matchLevel > 2) {
            bufferBagged.append(ch, start, length);
        }
        if (parentMatchLevel > 0 && matchLevel > 0) {
            bufferBagless.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        characters(ch, start, length);
    }

    @Override
    protected void addMetadata(String value) {
        LOG.trace("adding {}={}", name, value);
        if (targetProperty != null && targetProperty.isMultiValuePermitted()) {
            if ((value != null && value.length() > 0) || allowEmptyValues) {
                if (value == null || value.length() == 0 && allowEmptyValues) {
                    value = "";
                }
                String[] previous = metadata.getValues(name);
                if (previous == null || !Arrays.asList(previous).contains(value) || allowDuplicateValues) {
                    metadata.add(targetProperty, value);
                }
            }
        } else {
            super.addMetadata(value);
        }
    }
}
