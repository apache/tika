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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

class RtfDocumentTransformerTest extends TikaTest {

    @Test
    void mapsCommonFieldsAndTagsTheRest() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testRTF.rtf")) {
            AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(-1), metadata, new ParseContext());
        }

        RtfDocumentTransformer transformer = new RtfDocumentTransformer();
        assertTrue(transformer.appliesTo(metadata));

        Document.Builder builder = Document.newBuilder();
        java.util.Set<String> consumed = new java.util.HashSet<>();
        transformer.transform(metadata, builder, consumed);
        MetadataTagger.appendTail(metadata, consumed, builder);

        // testRTF.rtf carries a title, an author, a creation date and word/page/character
        // counts. Assert on what this real fixture actually has, not on every field the
        // transformer is capable of mapping.
        assertEquals("Test d’indexation Word", builder.getMetadata().getTitle());
        assertEquals(1, builder.getMetadata().getAuthorsCount());
        assertEquals("Bibliotheque", builder.getMetadata().getAuthors(0));
        assertTrue(builder.getMetadata().hasCreated());
        assertEquals(1, builder.getMetadata().getPageCount());
        assertEquals(3, builder.getMetadata().getWordCount());
        assertEquals(21, builder.getMetadata().getCharacterCount());

        // RTF/OOXML-specific properties (e.g. extended-properties:Company) are not typed
        // fields; they must still show up in the tagged tail.
        assertTrue(builder.getExtraCount() > 0);
    }
}
