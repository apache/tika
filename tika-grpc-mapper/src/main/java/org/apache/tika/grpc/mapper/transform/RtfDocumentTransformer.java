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
package org.apache.tika.grpc.mapper.transform;

import java.util.Locale;
import java.util.Set;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.DocumentMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;

/**
 * RTF. Maps the common, cross-format facts into typed DocumentMetadata.
 *
 * RTF-specific properties (encapsulated HTML flags, embedded-object thumbnails and
 * application/class/topic/item strings, OOXML core/extended fields such as category,
 * manager, company, template, ...) are NOT given their own proto fields. They flow into
 * the tagged tail, typed where Tika declares the type and string otherwise. That is the
 * whole point: format richness lives in code plus the tail, not in the wire contract - so
 * the schema stays small and stable and clients never rebuild when we add or change an
 * RTF property.
 */
public final class RtfDocumentTransformer implements DocumentTransformer {

    @Override
    public boolean appliesTo(Metadata tika) {
        String contentType = tika.get(Metadata.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("rtf");
    }

    @Override
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        DocumentMetadata.Builder meta = document.getMetadataBuilder();

        // Common, cross-format fields -> typed (a date is a Timestamp, a count is an int).
        // RTF keywords ride on the Office property set (meta:keyword), not dc:subject.
        TransformSupport.mapCommonFields(tika, meta, consumed, Office.KEYWORDS);
        TransformSupport.setInt(tika, Office.PAGE_COUNT, meta::setPageCount, consumed);
        TransformSupport.setLong(tika, Office.WORD_COUNT, meta::setWordCount, consumed);
        TransformSupport.setLong(tika, Office.CHARACTER_COUNT, meta::setCharacterCount, consumed);

        // Everything RTF-specific (rtf_meta:*, extended-properties:*, ...) lands in the
        // tagged tail, appended once by DocumentTransformers.
    }
}
