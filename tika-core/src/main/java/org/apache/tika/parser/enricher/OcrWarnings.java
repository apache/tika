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
package org.apache.tika.parser.enricher;

import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Writes {@link TikaCoreProperties#OCR_WARNINGS}: each code once per document, and for an
 * inline image also on the parent document, whose content receives the image's text.
 *
 * @since Apache Tika 4.2
 */
public final class OcrWarnings {

    private OcrWarnings() {
    }

    /** Adds the code to the metadata unless it is already there. */
    public static void add(Metadata metadata, String code) {
        for (String existing : metadata.getValues(TikaCoreProperties.OCR_WARNINGS)) {
            if (existing.equals(code)) {
                return;
            }
        }
        metadata.add(TikaCoreProperties.OCR_WARNINGS, code);
    }

    /** Adds the code to the metadata and, for an inline image, to its parent's. */
    public static void add(ParseContext context, Metadata metadata, String code) {
        add(metadata, code);
        addToInlineParent(context, metadata, code);
    }

    /** The text of an inline image lands in the parent's content, so the doubt goes with it. */
    public static void addToInlineParent(ParseContext context, Metadata metadata, String code) {
        ParentMetadata parent = context.get(ParentMetadata.class);
        if (parent == null || parent.getMetadata() == null) {
            return;
        }
        String type = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        if (!TikaCoreProperties.EmbeddedResourceType.INLINE.name().equals(type)) {
            return;
        }
        add(parent.getMetadata(), code);
    }
}
