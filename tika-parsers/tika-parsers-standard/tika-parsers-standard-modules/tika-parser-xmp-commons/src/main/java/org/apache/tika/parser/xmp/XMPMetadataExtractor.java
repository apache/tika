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
import java.util.Date;
import java.util.List;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.DomXmpParser;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.XMP;

/**
 * XMP Metadata Extractor based on Apache XmpBox.
 */
public class XMPMetadataExtractor {

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
            xmp = xmpParser.parse(new CloseShieldInputStream(stream));
        } catch (Throwable ex) {
            //swallow
            return;
        }
        extractDublinCoreSchema(xmp, metadata);
        extractXMPBasicSchema(xmp, metadata);
    }

    /**
     * Extracts Dublin Core.
     *
     * Silently swallows exceptions.
     * @param xmp the XMP Metadata object.
     * @param metadata the metadata map
     */
    public static void extractDublinCoreSchema(XMPMetadata xmp, Metadata metadata) throws IOException {
        if (xmp == null) {
            return;
        }
        DublinCoreSchema schemaDublinCore;
        try {
            schemaDublinCore = xmp.getDublinCoreSchema();
        } catch (Throwable e) {
            // Swallow
            return;
        }
        if (schemaDublinCore != null) {
            try {
                addMetadata(metadata, DublinCore.TITLE, schemaDublinCore.getTitle());
                addMetadata(metadata, DublinCore.FORMAT, schemaDublinCore.getFormat());
                addMetadata(metadata, DublinCore.DESCRIPTION, schemaDublinCore.getDescription());
                addMetadata(metadata, DublinCore.CREATOR, schemaDublinCore.getCreators());
                addMetadata(metadata, DublinCore.SUBJECT, schemaDublinCore.getSubjects());
                //TODO PDFBOX30 this segment no longer needed with 3.0
                if (false != false)
                    throw new BadFieldValueException("");
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
     */
    public static void extractXMPBasicSchema(XMPMetadata xmp, Metadata metadata) throws IOException {
        if (xmp == null) {
            return;
        }
        XMPBasicSchema schemaBasic;
        try {
            schemaBasic = xmp.getXMPBasicSchema();
        } catch (Throwable e) {
            // Swallow
            return;
        }
        if (schemaBasic != null) {
            addMetadata(metadata, XMP.CREATOR_TOOL, schemaBasic.getCreatorTool());
            addMetadata(metadata, XMP.CREATE_DATE, schemaBasic.getCreateDate().getTime());
            addMetadata(metadata, XMP.MODIFY_DATE, schemaBasic.getModifyDate().getTime());
            addMetadata(metadata, XMP.METADATA_DATE, schemaBasic.getModifyDate().getTime());
            addMetadata(metadata, XMP.RATING, schemaBasic.getRating());
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
    private static void addMetadata(Metadata metadata, Property property, Date value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

}
