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
import org.apache.tika.metadata.Metadata;

/**
 * HTML / XHTML. Maps the common, cross-format facts into typed DocumentMetadata.
 *
 * HTML-specific properties (html:meta:*, Open Graph og:*, Twitter Card twitter:*,
 * html:link:*, ICBM geo, encoding-detector fields, ...) are NOT given their own proto
 * fields. They flow into the tagged tail, typed where Tika declares the type and string
 * otherwise. That is the whole point: format richness lives in code plus the tail, not
 * in the wire contract - so the schema stays small and stable and clients never rebuild
 * when we add or change an HTML property.
 */
public final class HtmlDocumentTransformer implements DocumentTransformer {

    @Override
    public boolean appliesTo(Metadata tika) {
        String contentType = tika.get(Metadata.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") || lower.contains("application/xhtml");
    }

    @Override
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        // Common, cross-format fields -> typed (a date is a Timestamp, a count is an int).
        TransformSupport.mapCommonFields(tika, document.getMetadataBuilder(), consumed);

        // Everything HTML-specific (html:meta:*, og:*, twitter:*, html:link:*, ICBM, ...)
        // lands in the tagged tail, appended once by DocumentTransformers.
    }
}
