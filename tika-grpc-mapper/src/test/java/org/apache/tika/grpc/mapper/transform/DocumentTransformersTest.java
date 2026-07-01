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

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPRights;

class DocumentTransformersTest {

    /**
     * A plain-text document (no format transformer applies) that also happens to carry
     * Creative Commons rights metadata must still get the universal Dublin-Core-ish fields
     * (title, authors, ...) mapped by the generic fallback. Before the fix, the
     * cross-cutting {@link CreativeCommonsDocumentTransformer} matching on its own set
     * {@code matched = true} and suppressed the fallback entirely, silently dropping title/
     * author/description/keywords/languages/dates for any generic document that happens to
     * carry rights metadata.
     */
    @Test
    void crossCuttingTransformerDoesNotSuppressGenericFallback() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(TikaCoreProperties.TITLE, "A Generic Document");
        metadata.add(TikaCoreProperties.CREATOR, "Jane Doe");
        metadata.set(XMPRights.MARKED, "True");

        Document.Builder builder = Document.newBuilder();
        DocumentTransformers.defaults().transform(metadata, builder);

        assertEquals("A Generic Document", builder.getMetadata().getTitle(),
                "generic fallback must still run alongside a matching cross-cutting transformer");
        assertTrue(builder.getMetadata().getAuthorsList().contains("Jane Doe"));
        // the cross-cutting transformer's own mapping must also still apply
        assertTrue(builder.getExtraList().stream().anyMatch(f -> f.getKey().equals(XMPRights.MARKED.getName())));
    }

    /**
     * When two transformers both apply to the same document (e.g. a PDF that also carries
     * Creative Commons rights metadata -- an explicitly supported, documented scenario), each
     * transformer must not re-tag a key the other one already mapped to a typed field. Every
     * key should appear in the tagged tail at most once.
     */
    @Test
    void doesNotDuplicateTaggedTailWhenMultipleTransformersApply() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set(TikaCoreProperties.TITLE, "Report");
        metadata.set(XMPRights.MARKED, "True");

        Document.Builder builder = Document.newBuilder();
        DocumentTransformers.defaults().transform(metadata, builder);

        long titleTagCount = builder.getExtraList().stream()
                .filter(f -> f.getKey().equals(TikaCoreProperties.TITLE.getName()))
                .count();
        assertEquals(0, titleTagCount,
                "title is a typed field (already on document.metadata.title); it must not also "
                        + "be duplicated into the tagged tail by a second transformer");

        long markedTagCount = builder.getExtraList().stream()
                .filter(f -> f.getKey().equals(XMPRights.MARKED.getName()))
                .count();
        assertEquals(1, markedTagCount, "every tail key should appear at most once");
    }
}
