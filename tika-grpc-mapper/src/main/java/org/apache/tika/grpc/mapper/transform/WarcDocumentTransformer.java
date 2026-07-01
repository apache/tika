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
 * WARC (Web ARChive) containers. Unlike most formats, WARC's useful facts - WARC-Date,
 * WARC-Target-URI, WARC-Record-ID, HTTP status, HTTP response headers, ... - are not
 * normalized by {@code org.apache.tika.parser.warc.WARCParser} onto declared
 * {@link org.apache.tika.metadata.Property} constants. The parser adds them under literal
 * string keys instead ({@code warc:WARC-Date}, {@code warc:WARC-Target-URI},
 * {@code warc:http:status}, {@code warc:http:*}, ...). {@code org.apache.tika.metadata.WARC}
 * only declares a handful of properties (WARC_RECORD_CONTENT_TYPE, WARC_PAYLOAD_CONTENT_TYPE,
 * WARC_RECORD_ID, WARC_WARNING), and none of the common, cross-format facts that this
 * transformer could map (title, created, ...) are actually populated by the parser for a
 * WARC record.
 *
 * There is therefore nothing here that fairly maps onto the small set of typed
 * DocumentMetadata fields - forcing one would be dishonest, not useful. That is fine: the
 * tagged tail already handles arbitrary string keys, typed by Tika's declared Property type
 * where one exists and as plain strings otherwise, so none of the WARC/HTTP richness is
 * lost - it just does not need its own proto fields.
 */
public final class WarcDocumentTransformer implements DocumentTransformer {

    @Override
    public boolean appliesTo(Metadata tika) {
        String contentType = tika.get(Metadata.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("warc");
    }

    @Override
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        // No common field fairly maps to the typed DocumentMetadata for a WARC record (see
        // class javadoc). Everything (warc:WARC-Date, warc:WARC-Target-URI,
        // warc:WARC-Record-ID, warc:http:status, warc:http:*, ...) lands in the tagged tail,
        // appended once by DocumentTransformers.
    }
}
