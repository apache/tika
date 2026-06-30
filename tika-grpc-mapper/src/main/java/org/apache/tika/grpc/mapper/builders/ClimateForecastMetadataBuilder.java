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
import org.apache.tika.grpc.v1.ClimateForcastMetadata;
import org.apache.tika.metadata.Metadata;

/**
 * Builds ClimateForcastMetadata from Tika Metadata as a minimal MVP: carry base fields
 * and dump all NetCDF/CF metadata to additional_scientific_metadata until we catalog keys.
 */
public final class ClimateForecastMetadataBuilder {

    private ClimateForecastMetadataBuilder() { }

    /**
     * Builds a {@link ClimateForcastMetadata} message from the given Tika metadata.
     * <p>
     * Currently a minimal MVP: it carries the standard base fields and dumps all
     * remaining NetCDF/CF metadata into {@code additional_scientific_metadata} for
     * fidelity, until well-known CF/global attributes are catalogued and mapped.
     *
     * @param md the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link ClimateForcastMetadata} message
     */
    public static ClimateForcastMetadata build(Metadata md, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        ClimateForcastMetadata.Builder builder = ClimateForcastMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        // TODO: Map well-known CF/global attributes incrementally using MetadataUtils when catalogued

        // Additional scientific metadata (preserve fidelity)
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        builder.setAdditionalScientificMetadata(additional);

        // Base fields
        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        builder.setBaseFields(base);

        return builder.build();
    }
}
