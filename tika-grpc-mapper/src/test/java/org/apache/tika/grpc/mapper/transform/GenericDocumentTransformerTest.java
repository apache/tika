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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * The generic fallback must map the full Dublin Core descriptive core -- every typed
 * field in stage-1 {@code DocumentMetadata} -- and mark each mapped key consumed.
 */
class GenericDocumentTransformerTest {

    @Test
    void mapsTheFullDublinCoreDescriptiveCore() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TITLE, "The Title");
        metadata.set(TikaCoreProperties.DESCRIPTION, "A description.");
        metadata.add(TikaCoreProperties.CREATOR, "Author One");
        metadata.add(TikaCoreProperties.CREATOR, "Author Two");
        metadata.add(TikaCoreProperties.SUBJECT, "keyword-a");
        metadata.add(TikaCoreProperties.SUBJECT, "keyword-b");
        metadata.add(TikaCoreProperties.LANGUAGE, "en");
        metadata.add(TikaCoreProperties.PUBLISHER, "ASF");
        metadata.add(TikaCoreProperties.IDENTIFIER, "urn:isbn:9780000000000");
        metadata.set(TikaCoreProperties.CREATED, "2023-01-02T03:04:05Z");
        metadata.set(TikaCoreProperties.MODIFIED, "2024-06-07T08:09:10Z");
        metadata.set(TikaCoreProperties.RIGHTS, "Apache License 2.0");

        Document.Builder document = Document.newBuilder();
        Set<String> consumed = new HashSet<>();
        new GenericDocumentTransformer().transform(metadata, document, consumed);

        var meta = document.getMetadata();
        assertEquals("The Title", meta.getTitle());
        assertEquals("A description.", meta.getDescription());
        assertEquals(List.of("Author One", "Author Two"), meta.getAuthorsList());
        assertEquals(List.of("keyword-a", "keyword-b"), meta.getKeywordsList());
        assertEquals(List.of("en"), meta.getLanguagesList());
        assertEquals(List.of("ASF"), meta.getPublishersList());
        assertEquals(List.of("urn:isbn:9780000000000"), meta.getIdentifiersList());
        assertEquals("Apache License 2.0", meta.getRights());
        assertEquals(java.time.Instant.parse("2023-01-02T03:04:05Z").getEpochSecond(),
                meta.getCreated().getSeconds());
        assertEquals(java.time.Instant.parse("2024-06-07T08:09:10Z").getEpochSecond(),
                meta.getModified().getSeconds());

        // every mapped key must be consumed so the tail cannot double-ship it
        for (String key : new String[]{
                TikaCoreProperties.TITLE.getName(),
                TikaCoreProperties.DESCRIPTION.getName(),
                TikaCoreProperties.CREATOR.getName(),
                TikaCoreProperties.SUBJECT.getName(),
                TikaCoreProperties.LANGUAGE.getName(),
                TikaCoreProperties.PUBLISHER.getName(),
                TikaCoreProperties.IDENTIFIER.getName(),
                TikaCoreProperties.CREATED.getName(),
                TikaCoreProperties.MODIFIED.getName(),
                TikaCoreProperties.RIGHTS.getName()}) {
            assertTrue(consumed.contains(key), "expected consumed to contain " + key);
        }
    }

    @Test
    void absentFieldsStayAbsentAndUnconsumed() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TITLE, "Only a title");

        Document.Builder document = Document.newBuilder();
        Set<String> consumed = new HashSet<>();
        new GenericDocumentTransformer().transform(metadata, document, consumed);

        assertEquals("Only a title", document.getMetadata().getTitle());
        assertEquals(0, document.getMetadata().getAuthorsCount());
        assertEquals("", document.getMetadata().getRights());
        assertEquals(Set.of(TikaCoreProperties.TITLE.getName()), consumed);
    }

    /**
     * Whitespace-only values are noise, not data: they must neither populate a typed
     * field nor be marked consumed (so a later transformer could still map the key).
     */
    @Test
    void blankValuesAreIgnored() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TITLE, "   ");

        Document.Builder document = Document.newBuilder();
        Set<String> consumed = new HashSet<>();
        new GenericDocumentTransformer().transform(metadata, document, consumed);

        assertEquals("", document.getMetadata().getTitle());
        assertTrue(consumed.isEmpty());
    }
}
