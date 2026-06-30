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

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.EpubMetadata;
import org.apache.tika.metadata.Epub;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds EpubMetadata from Tika Metadata.
 * Minimal mapping for now (rendition_layout, version, mimetype),
 * with room to extend to spine/manifest/toc when available.
 */
public final class EpubMetadataBuilder {

    private EpubMetadataBuilder() { }

    /**
     * Builds an {@link EpubMetadata} message from the given Tika metadata.
     * <p>
     * Maps core EPUB properties (rendition layout, version, mimetype, language and
     * identifier) and, when raw {@code .epub} bytes are available in the metadata,
     * enriches the result with OPF/NAV structure via {@link EpubStructureExtractor}.
     * Any unmapped keys are preserved in {@code additional_metadata}.
     *
     * @param metadata the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link EpubMetadata} message
     */
    public static EpubMetadata build(Metadata metadata, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        EpubMetadata.Builder builder = EpubMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        // Core EPUB properties
        MetadataUtils.mapStringField(metadata, Epub.RENDITION_LAYOUT, builder::setRenditionLayout, mapped);
        MetadataUtils.mapStringField(metadata, Epub.VERSION, builder::setVersion, mapped);

        // Technical

        // Additional helpful fields from core props
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.LANGUAGE, builder::setContentLanguage, mapped);
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.IDENTIFIER, builder::setUniqueIdentifier, mapped);

        // Attempt to enrich with OPF/NAV structure if raw bytes are available in metadata
        byte[] raw = MetadataUtils.tryGetRawBytes(metadata);
        if (raw != null && raw.length > 0) {
            EpubStructureExtractor.enrich(builder, raw);
            // Prevent huge base64 field from being embedded into additional/base fields
            try { metadata.remove("pipe:raw-bytes-b64"); } catch (Exception ignored) { }
        }

        // Base fields
        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, metadata);
        builder.setBaseFields(base);

        return builder.build();
    }
}
