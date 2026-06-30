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
import org.apache.tika.grpc.v1.FontMetadata;
import org.apache.tika.metadata.Font;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds strongly-typed {@link FontMetadata} from Tika metadata, mapping the font
 * name, original filename and MIME type, with fallbacks that derive the filename
 * and font name from the resource name when full Tika parsing is bypassed, and any
 * remaining keys preserved in additional metadata.
 */
public class FontMetadataBuilder {

    /**
     * Utility class; not meant to be instantiated.
     */
    private FontMetadataBuilder() { }

    /**
     * Builds a {@link FontMetadata} message from the given Tika metadata.
     * <p>
     * Maps the font name, original filename and MIME type. When those are absent it
     * falls back to the {@code resourceName} for the filename and derives a font name
     * from the filename (stripping its extension). Any remaining keys are preserved
     * in {@code additional_metadata}.
     *
     * @param md the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link FontMetadata} message
     */
    public static FontMetadata build(Metadata md, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        FontMetadata.Builder b = FontMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        // Basic font name(s)
        MetadataUtils.mapRepeatedStringField(md, Font.FONT_NAME, names -> {
            if (names.iterator().hasNext()) {
                String first = names.iterator().next();
                b.setFontName(first);
            }
        }, mapped);

        // Known fields Tika often sets for fonts (best effort via raw keys)
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, b::setOriginalFilename, mapped);

        // Fallbacks for common cases when we bypass full Tika parsing
        if (!b.hasOriginalFilename()) {
            String rn = md.get("resourceName");
            if (rn != null && !rn.isEmpty()) {
                b.setOriginalFilename(rn);
            }
        }
        if (!b.hasFontName()) {
            String base = null;
            if (b.hasOriginalFilename()) {
                base = b.getOriginalFilename();
            } else {
                String rn = md.get("resourceName");
                if (rn != null && !rn.isEmpty()) base = rn;
            }
            if (base != null && !base.isEmpty()) {
                int dot = base.lastIndexOf('.');
                if (dot > 0) {
                    base = base.substring(0, dot);
                }
                if (!base.isEmpty()) {
                    b.setFontName(base);
                }
            }
        }

        // Base fields
        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }
}
