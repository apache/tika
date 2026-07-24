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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

class DocumentTransformersTest {

    /**
     * With no format-specific transformer registered, the generic fallback must map the
     * Dublin Core fields to their typed home and everything else must land in the tagged
     * tail exactly once.
     */
    @Test
    void genericFallbackMapsDublinCoreAndTailIsLossless() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(TikaCoreProperties.TITLE, "A Generic Document");
        metadata.add(TikaCoreProperties.CREATOR, "Jane Doe");
        metadata.set("custom:key", "custom value");

        Document.Builder builder = Document.newBuilder();
        DocumentTransformers.defaults().transform(metadata, builder, new HashSet<>());

        assertEquals("A Generic Document", builder.getMetadata().getTitle());
        assertTrue(builder.getMetadata().getAuthorsList().contains("Jane Doe"));

        // typed keys must not duplicate into the tail; unmapped keys must survive there
        assertEquals(0, builder.getExtraList().stream()
                .filter(f -> f.getKey().equals(TikaCoreProperties.TITLE.getName()))
                .count());
        assertEquals(1, builder.getExtraList().stream()
                .filter(f -> f.getKey().equals("custom:key"))
                .count());
    }

    /**
     * The stage-2 extension mechanism: registering a format transformer suppresses the
     * generic fallback for matching documents, while the tagged tail still carries
     * whatever the format transformer chose not to type. No proto change involved --
     * this is exactly how per-format support is meant to arrive.
     */
    @Test
    void registeredFormatTransformerReplacesFallbackAndKeepsTailLossless() {
        DocumentTransformer testFormat = new DocumentTransformer() {
            @Override
            public boolean appliesTo(Metadata tika) {
                return "application/x-test".equals(tika.get(Metadata.CONTENT_TYPE));
            }

            @Override
            public void transform(Metadata tika, Document.Builder document,
                                  java.util.Set<String> consumed) {
                TransformSupport.setString(tika, TikaCoreProperties.DESCRIPTION,
                        document.getMetadataBuilder()::setDescription, consumed);
            }
        };
        DocumentTransformers transformers =
                new DocumentTransformers(java.util.List.of(testFormat));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/x-test");
        metadata.set(TikaCoreProperties.DESCRIPTION, "typed by the format transformer");
        metadata.set(TikaCoreProperties.TITLE, "left for the tail");

        Document.Builder builder = Document.newBuilder();
        transformers.transform(metadata, builder, new HashSet<>());

        assertEquals("typed by the format transformer", builder.getMetadata().getDescription());
        // the fallback did not run, so TITLE has no typed home -- but it is not lost
        assertEquals("", builder.getMetadata().getTitle());
        assertEquals(1, builder.getExtraList().stream()
                .filter(f -> f.getKey().equals(TikaCoreProperties.TITLE.getName()))
                .count());

        // a non-matching document still gets the generic fallback
        Metadata other = new Metadata();
        other.set(Metadata.CONTENT_TYPE, "text/plain");
        other.set(TikaCoreProperties.TITLE, "mapped by the fallback");
        Document.Builder otherBuilder = Document.newBuilder();
        transformers.transform(other, otherBuilder, new HashSet<>());
        assertEquals("mapped by the fallback", otherBuilder.getMetadata().getTitle());
    }

    /**
     * Keys the caller already consumed (the envelope fields mapped by DocumentBuilder)
     * must be honored by the shared consumed-set contract and stay out of the tail.
     */
    @Test
    void preConsumedKeysStayOutOfTheTail() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set("already:mapped", "by the caller");

        Set<String> consumed = new HashSet<>();
        consumed.add("already:mapped");

        Document.Builder builder = Document.newBuilder();
        DocumentTransformers.defaults().transform(metadata, builder, consumed);

        assertTrue(builder.getExtraList().stream()
                .noneMatch(f -> f.getKey().equals("already:mapped")));
    }
}
