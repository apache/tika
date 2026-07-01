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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.FormatCategory;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * End-to-end coverage of {@link DocumentBuilder}: the content tree (parsed from the
 * pipes-default markdown rendering), typed/tagged metadata (via {@link
 * org.apache.tika.grpc.mapper.transform.DocumentTransformers}), format-category routing,
 * status, and embedded-document recursion.
 */
class DocumentBuilderTest extends ParseFixtureSupport {

    @Test
    void buildsContentTreeAndMetadataFromRealPdf() throws Exception {
        Document document = map(parseBody("testPDF.pdf"), "doc-1", 42L);

        assertTrue(document.getMarkdown().contains("Tika"));
        assertFalse(document.getBlocksList().isEmpty());
        assertEquals(FormatCategory.FORMAT_CATEGORY_PDF, document.getFormatCategory());
        assertEquals("application/pdf", document.getContentType());
        assertEquals("Apache Tika - Apache Tika", document.getMetadata().getTitle());
        assertTrue(document.getExtraCount() > 0);

        assertEquals(ParseStatus.Status.SUCCESS, document.getStatus().getStatus());
        assertEquals(42L, document.getStatus().getParseTimeMs());
    }

    @Test
    void recursesIntoEmbeddedDocuments() {
        Metadata primary = new Metadata();
        primary.set(Metadata.CONTENT_TYPE, "application/pdf");
        primary.set(TikaCoreProperties.TITLE, "Container");

        Metadata embedded = new Metadata();
        embedded.set(Metadata.CONTENT_TYPE, "image/jpeg");
        embedded.set(TikaCoreProperties.RESOURCE_NAME_KEY, "photo.jpg");
        embedded.set(TikaCoreProperties.TIKA_CONTENT, "an embedded photo");

        Document document = DocumentBuilder.build(
                primary, List.of(primary, embedded), "# Container\n", "doc-1", "PARSE_SUCCESS", 5L);

        assertEquals(1, document.getEmbeddedCount());
        Document child = document.getEmbedded(0);
        assertEquals("image/jpeg", child.getContentType());
        assertEquals(FormatCategory.FORMAT_CATEGORY_IMAGE, child.getFormatCategory());
        assertEquals("photo.jpg", child.getOrigin().getFilename());
        assertEquals("an embedded photo", child.getMarkdown());
    }

    @Test
    void reportsFailureWhenNoMetadata() {
        Document document = DocumentBuilder.build(null, null, null, "doc-1", "PARSE_EXCEPTION", 1L);

        assertEquals(ParseStatus.Status.FAILED, document.getStatus().getStatus());
        assertFalse(document.getStatus().getErrorsList().isEmpty());
    }

    /**
     * {@code pipesStatus} is populated from {@code PipesResult.RESULT_STATUS.name()} (see
     * org.apache.tika.pipes.api.PipesResult), never from a plain "OK" -- there is no such
     * enum constant. A real successful parse reports as e.g. "PARSE_SUCCESS" or
     * "EMIT_SUCCESS", and a real timeout reports as "TIMEOUT" (categorized as a process
     * crash, not a partial success). The status mapping must recognize the real names.
     */
    @Test
    void mapsRealPipesResultStatusNames() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, null, null, "d", "PARSE_SUCCESS", 1L).getStatus().getStatus(),
                "PARSE_SUCCESS is a real, clean success status");
        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, null, null, "d", "EMIT_SUCCESS", 1L).getStatus().getStatus());
        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, null, null, "d", "EMPTY_OUTPUT", 1L).getStatus().getStatus());

        assertEquals(ParseStatus.Status.PARTIAL,
                DocumentBuilder.build(metadata, null, null, "d", "PARSE_SUCCESS_WITH_EXCEPTION", 1L)
                        .getStatus().getStatus(),
                "succeeded, but with a caveat along the way, is a partial success not a clean one");

        assertEquals(ParseStatus.Status.FAILED,
                DocumentBuilder.build(metadata, null, null, "d", "TIMEOUT", 1L).getStatus().getStatus(),
                "TIMEOUT is categorized as a process crash, not a partial success");
        assertEquals(ParseStatus.Status.FAILED,
                DocumentBuilder.build(metadata, null, null, "d", "OOM", 1L).getStatus().getStatus());
    }
}
