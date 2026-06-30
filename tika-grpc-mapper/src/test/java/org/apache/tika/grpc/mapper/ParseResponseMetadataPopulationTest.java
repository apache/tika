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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.mapper.builders.MetadataUtils;
import org.apache.tika.grpc.v1.MetadataEntry;
import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PDF;

class ParseResponseMetadataPopulationTest {

    @Test
    void metadataEntriesMirrorEveryTikaKeyWithMultivalueSupport() {
        Metadata metadata = new Metadata();
        metadata.add(DublinCore.TITLE, "Sample");
        metadata.add(DublinCore.SUBJECT, "alpha");
        metadata.add(DublinCore.SUBJECT, "beta");
        metadata.add(Metadata.CONTENT_TYPE, "text/plain");
        metadata.add("custom:Info", "value");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.DefaultParser", "body text", "doc-1");

        assertEquals(metadata.names().length, response.getMetadataCount(),
                "every Tika key should appear in ParseResponse.metadata");

        MetadataEntry subjectEntry = response.getMetadataList().stream()
                .filter(entry -> entry.getKey().equals(DublinCore.SUBJECT.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(subjectEntry.hasTextList());
        assertEquals(2, subjectEntry.getTextList().getValuesCount());
        assertEquals("alpha", subjectEntry.getTextList().getValues(0));
        assertEquals("beta", subjectEntry.getTextList().getValues(1));
    }

    @Test
    void contentKeywordsJoinMultivalueSubjects() {
        Metadata metadata = new Metadata();
        metadata.add(Office.KEYWORDS, "one");
        metadata.add(Office.KEYWORDS, "two");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.DefaultParser", "body", "doc-3");

        assertEquals("one; two", response.getContent().getKeywords());
    }

    @Test
    void metadataUtilsBuildMetadataEntriesPreservesMultivalue() {
        Metadata metadata = new Metadata();
        metadata.add("k1", "v1");
        metadata.add("k2", "a");
        metadata.add("k2", "b");

        assertEquals(2, MetadataUtils.buildMetadataEntries(metadata).size());
        MetadataEntry k2 = MetadataUtils.buildMetadataEntries(metadata).stream()
                .filter(entry -> entry.getKey().equals("k2"))
                .findFirst()
                .orElseThrow();
        assertTrue(k2.hasTextList());
        assertEquals(2, k2.getTextList().getValuesCount());
    }

    @Test
    void metadataEntriesUseRegisteredPropertyTypes() {
        Metadata metadata = new Metadata();
        metadata.set(PDF.PDFAID_PART, "2");
        metadata.set(PDF.IS_ENCRYPTED, "true");
        metadata.set(DublinCore.CREATED, "2019-01-15T10:00:00Z");
        metadata.set(Metadata.CONTENT_LENGTH, "4096");

        var entries = MetadataUtils.buildMetadataEntries(metadata);

        MetadataEntry pdfaid = findEntry(entries, PDF.PDFAID_PART.getName());
        assertTrue(pdfaid.hasInteger());
        assertEquals(2L, pdfaid.getInteger());

        MetadataEntry encrypted = findEntry(entries, PDF.IS_ENCRYPTED.getName());
        assertTrue(encrypted.hasBoolean());
        assertTrue(encrypted.getBoolean());

        MetadataEntry created = findEntry(entries, DublinCore.CREATED.getName());
        assertTrue(created.hasDate());
        assertEquals(1547546400L, created.getDate().getSeconds());

        MetadataEntry length = findEntry(entries, Metadata.CONTENT_LENGTH);
        assertTrue(length.hasInteger());
        assertEquals(4096L, length.getInteger());
    }

    @Test
    void metadataEntriesFallBackToTextWhenCoercionFails() {
        Metadata metadata = new Metadata();
        metadata.set(PDF.PDFAID_PART, "not-a-number");

        MetadataEntry entry = findEntry(MetadataUtils.buildMetadataEntries(metadata), PDF.PDFAID_PART.getName());
        assertTrue(entry.hasText());
        assertEquals("not-a-number", entry.getText());
    }

    @Test
    void envelopeContentTypeIsCanonicalAndExcludedFromFormatDuplicates() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set(PDF.PDFAID_PART, "2");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.pdf.PDFParser", "body", "pdf-doc");

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getPdf().getContentType().isEmpty());

        boolean inMetadataMirror = response.getMetadataList().stream()
                .anyMatch(entry -> entry.getKey().equals(Metadata.CONTENT_TYPE));
        assertTrue(inMetadataMirror, "Content-Type remains in metadata mirror");
    }

    @Test
    void parseResponseMetadataIncludesTypedEntries() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set(PDF.NUM_3D_ANNOTATIONS, "3");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.pdf.PDFParser", "body", "pdf-doc");

        MetadataEntry annotations = response.getMetadataList().stream()
                .filter(entry -> entry.getKey().equals(PDF.NUM_3D_ANNOTATIONS.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(annotations.hasInteger());
        assertEquals(3L, annotations.getInteger());
    }

    private static MetadataEntry findEntry(java.util.List<MetadataEntry> entries, String key) {
        return entries.stream()
                .filter(entry -> entry.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
