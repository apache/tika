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
import org.apache.tika.grpc.v1.CreativeCommonsMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPRights;

/**
 * Builds CreativeCommonsMetadata from Tika Metadata. Overlay metadata across types.
 */
public final class CreativeCommonsMetadataBuilder {

    private CreativeCommonsMetadataBuilder() { }

    /**
     * Builds a {@link CreativeCommonsMetadata} message from the given Tika metadata.
     * <p>
     * Maps the XMPRights fields (certificate, marked flag, owners, usage terms and
     * web statement) into typed fields, and leaves any other {@code cc:*} or
     * {@code license*} fields in {@code additional_rights_metadata}.
     *
     * @param metadata the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link CreativeCommonsMetadata} message
     */
    public static CreativeCommonsMetadata build(Metadata metadata, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        CreativeCommonsMetadata.Builder builder = CreativeCommonsMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        // XMPRights
        MetadataUtils.mapStringField(metadata, XMPRights.CERTIFICATE, builder::setRightsCertificate, mapped);
        MetadataUtils.mapBooleanField(metadata, XMPRights.MARKED, builder::setRightsMarked, mapped);
        MetadataUtils.mapRepeatedStringField(metadata, XMPRights.OWNER, builder::addAllRightsOwners, mapped);
        MetadataUtils.mapStringField(metadata, XMPRights.USAGE_TERMS, builder::setUsageTerms, mapped);
        MetadataUtils.mapStringField(metadata, XMPRights.WEB_STATEMENT, builder::setWebStatement, mapped);

        // Additional (any cc:* or license* fields) left in additional_metadata
        Struct additional = MetadataUtils.buildAdditionalMetadata(metadata, mapped);
        builder.setAdditionalRightsMetadata(additional);

        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, metadata);
        builder.setBaseFields(base);

        return builder.build();
    }
}
