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
package org.apache.tika.parser.xmp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.schema.XMPMediaManagementSchema;
import org.apache.xmpbox.type.AbstractField;
import org.apache.xmpbox.type.ArrayProperty;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.type.ResourceEventType;
import org.apache.xmpbox.type.ResourceRefType;
import org.apache.xmpbox.xml.DomXmpParser;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.utils.DateUtils;

/**
 * XMP Metadata Extractor based on Apache XmpBox.
 */
public class XMPMetadataExtractor {

    private static volatile int MAX_EVENT_HISTORY_IN_XMPMM = 1024;

    /**
     * Parse the XMP Packets.
     *
     * @param stream the stream to parser.
     * @param metadata the metadata collection to update
     * @throws IOException on any IO error.
     * @throws TikaException on any Tika error.
     */
    public static void parse(InputStream stream, Metadata metadata) throws IOException, TikaException {
        XMPMetadata xmp;
        try {
            DomXmpParser xmpParser = new DomXmpParser();
            xmpParser.setStrictParsing(false);
            xmp = xmpParser.parse(CloseShieldInputStream.wrap(stream));
        } catch (Throwable ex) {
            //swallow
            return;
        }
        extractDublinCoreSchema(xmp, metadata);
        extractXMPBasicSchema(xmp, metadata);
        extractXMPMM(xmp, metadata);
    }

    /**
     * Extracts Dublin Core.
     *
     * Silently swallows exceptions.
     * @param xmp the XMP Metadata object.
     * @param metadata the metadata map
     * @throws IOException
     */
    public static void extractDublinCoreSchema(XMPMetadata xmp, Metadata metadata) throws IOException {
        if (xmp == null) {
            return;
        }
        DublinCoreSchema schemaDublinCore = xmp.getDublinCoreSchema();
        if (schemaDublinCore != null) {
            try {
                addMetadata(metadata, DublinCore.TITLE, schemaDublinCore.getTitle());
                addMetadata(metadata, DublinCore.FORMAT, schemaDublinCore.getFormat());
                addMetadata(metadata, DublinCore.DESCRIPTION, schemaDublinCore.getDescription());
                addMetadata(metadata, DublinCore.CREATOR, schemaDublinCore.getCreators());
                addMetadata(metadata, DublinCore.SUBJECT, schemaDublinCore.getSubjects());
            }
            catch (BadFieldValueException ex) {
                throw new IOException(ex);
            }
        }
    }

    /**
     * Extracts basic schema metadata from XMP.
     *
     * Silently swallows exceptions.
     * @param xmp the XMP Metadata object.
     * @param metadata the metadata map
     * @throws IOException
     */
    public static void extractXMPBasicSchema(XMPMetadata xmp, Metadata metadata) throws IOException {
        if (xmp == null) {
            return;
        }
        XMPBasicSchema schemaBasic = xmp.getXMPBasicSchema();
        if (schemaBasic != null) {
            addMetadata(metadata, XMP.CREATOR_TOOL, schemaBasic.getCreatorTool());
            addMetadata(metadata, XMP.CREATE_DATE, schemaBasic.getCreateDate());
            addMetadata(metadata, XMP.MODIFY_DATE, schemaBasic.getModifyDate());
            addMetadata(metadata, XMP.METADATA_DATE, schemaBasic.getModifyDate());
            addMetadata(metadata, XMP.RATING, schemaBasic.getRating());
        }
    }

    /**
     * @return maximum number of events to extract from the XMPMM history.
     */
    public static int getMaxXMPMMHistory() {
        return MAX_EVENT_HISTORY_IN_XMPMM;
    }

    /**
     * Maximum number of events to extract from the
     * event history in the XMP Media Management (XMPMM) section.
     * The extractor will silently stop adding events after it
     * has reached this threshold.
     * <p>
     * The default is 1024.
     * @param maxEvents
     */
    public static void setMaxXMPMMHistory(int maxEvents) {
        MAX_EVENT_HISTORY_IN_XMPMM = maxEvents;
    }

    /**
     * Extracts Media Management metadata from XMP.
     * <p>
     * Silently swallows exceptions.
     *
     * @param xmp
     * @param metadata
     */
    public static void extractXMPMM(XMPMetadata xmp, Metadata metadata) {
        if (xmp == null) {
            return;
        }
        XMPMediaManagementSchema mmSchema = xmp.getXMPMediaManagementSchema();
        if (mmSchema != null) {
            addMetadata(metadata, XMPMM.DOCUMENTID, mmSchema.getDocumentID());
            metadata.set(XMPMM.INSTANCEID, mmSchema.getInstanceID());
            metadata.set(XMPMM.ORIGINAL_DOCUMENTID, mmSchema.getOriginalDocumentID());

            //ResourceRefType derivedFrom = mmSchema.getDerivedFromProperty(); //TODO after XMPBox 3.0.7
            ResourceRefType derivedFrom = mmSchema.getResourceRefProperty();
            
            if (derivedFrom != null) {
                addMetadata(metadata, XMPMM.DERIVED_FROM_DOCUMENTID, derivedFrom.getDocumentID());
                addMetadata(metadata, XMPMM.DERIVED_FROM_INSTANCEID, derivedFrom.getInstanceID());
            }
            ArrayProperty historyProperty = mmSchema.getHistoryProperty();
            if (historyProperty != null) {
                int eventsAdded = 0;
                for (AbstractField af : historyProperty.getAllProperties()) {
                    if (eventsAdded >= MAX_EVENT_HISTORY_IN_XMPMM) {
                        break;
                    }
                    if (!(af instanceof ResourceEventType))
                    {
                        continue;
                    }
                    ResourceEventType stevt = (ResourceEventType) af;
                    String instanceId = stevt.getInstanceID();
                    String action = stevt.getAction();
                    Calendar when = stevt.getWhen();
                    String softwareAgent = stevt.getSoftwareAgent();
                    if (instanceId != null && !instanceId.isBlank())
                    {
                        // for absent data elements, pass in empty strings so
                        // that parallel arrays will have matching offsets for absent data
                        action = action == null ? "" : action;
                        String dateString = when == null ? "" : DateUtils.formatDate(when);
                        softwareAgent = softwareAgent == null ? "" : softwareAgent;

                        metadata.add(XMPMM.HISTORY_EVENT_INSTANCEID, instanceId);
                        metadata.add(XMPMM.HISTORY_ACTION, action);
                        metadata.add(XMPMM.HISTORY_WHEN, dateString);
                        metadata.add(XMPMM.HISTORY_SOFTWARE_AGENT, softwareAgent);
                        eventsAdded++;
                    }
                }
            }
        }
    }

    /**
     * Add list to the metadata map.
     *
     * @param metadata the metadata map to update.
     * @param property the property to add.
     * @param values the values to add.
     */
    private static void addMetadata(Metadata metadata, Property property, List<String> values) {
        if (values != null) {
            for (String value : values) {
                addMetadata(metadata, property, value);
            }
        }
    }

    /**
     * Add value to the metadata map.
     *
     * @param metadata the metadata map to update.
     * @param property the property to add.
     * @param value the value to add.
     */
    private static void addMetadata(Metadata metadata, Property property, String value) {
        if (value != null) {
            if (property.isMultiValuePermitted()) {
                metadata.add(property, value);
            } else {
                metadata.set(property, value);
            }
        }
    }

    /**
     * Add value to the metadata map.
     *
     * @param metadata the metadata map to update.
     * @param property the property to add.
     * @param value the value to add.
     */
    private static void addMetadata(Metadata metadata, Property property, Integer value) {
        if (value != null) {
            if (property.isMultiValuePermitted()) {
                metadata.add(property, value);
            } else {
                metadata.set(property, value);
            }
        }
    }

    /**
     * Add value to the metadata map.
     *
     * @param metadata the metadata map to update.
     * @param property the property to add.
     * @param value the value to add.
     */
    private static void addMetadata(Metadata metadata, Property property, Calendar value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

}
