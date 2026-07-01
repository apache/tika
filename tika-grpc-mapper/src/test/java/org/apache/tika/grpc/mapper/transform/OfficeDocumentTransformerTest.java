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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.DocumentMetadata;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

class OfficeDocumentTransformerTest extends TikaTest {

    @Test
    void mapsTypedFieldsAndTagsTheRest() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testWORD.doc")) {
            AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(-1), metadata, new ParseContext());
        }

        OfficeDocumentTransformer transformer = new OfficeDocumentTransformer();
        assertTrue(transformer.appliesTo(metadata), "should apply to application/msword");

        Document.Builder builder = Document.newBuilder();
        java.util.Set<String> consumed = new java.util.HashSet<>();
        transformer.transform(metadata, builder, consumed);
        MetadataTagger.appendTail(metadata, consumed, builder);

        DocumentMetadata meta = builder.getMetadata();
        assertFalse(meta.getTitle().isBlank(), "title should be populated");
        assertEquals("Sample Word Document", meta.getTitle());
        assertEquals(1, meta.getAuthorsCount());
        assertEquals("Keith Bennett", meta.getAuthors(0));
        assertTrue(meta.hasCreated(), "created should be populated");
        assertTrue(meta.hasModified(), "modified should be populated");
        assertEquals(2, meta.getPageCount());
        assertEquals(122L, meta.getWordCount());
        assertEquals(699L, meta.getCharacterCount());

        // Everything not mapped to a typed field (revision, extended properties,
        // last-author, comment info, hidden-text flag, etc.) lands in the tagged tail.
        assertTrue(builder.getExtraCount() > 0, "tagged tail should be populated");
    }
}
