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
package org.apache.tika.grpc.mapper.builders;

import java.util.HashSet;
import java.util.Set;

import com.google.protobuf.Struct;

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.DatabaseMetadata;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds strongly-typed DatabaseMetadata from Tika Metadata.
 *
 * Maps commonly available database fields (table/column names, counts) and preserves all
 * remaining keys in additional_metadata for fidelity.
 */
public final class DatabaseMetadataBuilder {

    private DatabaseMetadataBuilder() { }

    /**
     * Builds a {@link DatabaseMetadata} message from the given Tika metadata.
     * <p>
     * Maps core document properties, database table/column names and row/column
     * counts, and content/resource hints, preserving any remaining keys in
     * {@code additional_metadata} for fidelity.
     *
     * @param md the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link DatabaseMetadata} message
     */
    public static DatabaseMetadata build(Metadata md, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        DatabaseMetadata.Builder builder = DatabaseMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        // Core document fields
        mapCore(md, builder, mapped);

        // Database table/column lists and counts
        mapDbCore(md, builder, mapped);

        // Content/resource hints
        MetadataUtils.mapStringField(md, "Content-Type", builder::setContentType, mapped);
        MetadataUtils.mapStringField(md, "Content-Length", builder::setContentLength, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, builder::setResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ORIGINAL_RESOURCE_NAME, builder::setOriginalResourceName, mapped);

        // Additional metadata
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        builder.setAdditionalMetadata(additional);

        // Base fields
        BaseFields baseFields = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        builder.setBaseFields(baseFields);

        return builder.build();
    }

    private static void mapCore(Metadata md, DatabaseMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, TikaCoreProperties.TITLE, b::setTitle, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR, b::setCreator, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DESCRIPTION, b::setDescription, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SUBJECT, b::setSubject, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.LANGUAGE, b::setLanguage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.FORMAT, b::setFormat, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.IDENTIFIER, b::setIdentifier, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, b::setComments, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.CREATED, b::setCreated, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setModified, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR_TOOL, b::setCreatorTool, mapped);
    }

    private static void mapDbCore(Metadata md, DatabaseMetadata.Builder b, Set<String> mapped) {
        try {
            // Table names and column names (repeated)
            MetadataUtils.mapRepeatedStringField(md, Database.TABLE_NAME, b::addAllTableNames, mapped);
            MetadataUtils.mapRepeatedStringField(md, Database.COLUMN_NAME, b::addAllColumnNames, mapped);

            // Total counts
            MetadataUtils.mapIntField(md, Database.ROW_COUNT, b::setTotalRowCount, mapped);
            MetadataUtils.mapIntField(md, Database.COLUMN_COUNT, b::setTotalColumnCount, mapped);
        } catch (Throwable ignored) {
            // If Tika's Database class isn't present for some formats, skip quietly
        }
    }
}
