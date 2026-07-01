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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Verifies {@link HtmlDocumentTransformer} against a real HTML fixture: typed common
 * fields land on {@link org.apache.tika.grpc.v1.DocumentMetadata}, and the many
 * HTML-specific properties (html:meta:*, Open Graph, Twitter Card, ...) land in the
 * tagged tail.
 */
class HtmlDocumentTransformerTest extends TikaTest {

    @Test
    void transformsRealHtmlFixture() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testHTML_metadata.html")) {
            AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(-1), metadata, new ParseContext());
        }

        HtmlDocumentTransformer transformer = new HtmlDocumentTransformer();
        assertTrue(transformer.appliesTo(metadata));

        Document.Builder builder = Document.newBuilder();
        java.util.Set<String> consumed = new java.util.HashSet<>();
        transformer.transform(metadata, builder, consumed);
        MetadataTagger.appendTail(metadata, consumed, builder);

        assertFalse(builder.getMetadata().getTitle().isBlank());
        assertTrue(builder.getExtraCount() > 0);
    }
}
