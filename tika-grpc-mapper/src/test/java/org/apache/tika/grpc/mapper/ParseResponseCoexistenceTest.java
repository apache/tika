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

import org.apache.tika.grpc.v1.DocumentFormatCategory;
import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPRights;

/**
 * Demonstrates that the typed format submessages coexist now that {@code document_metadata} is a set
 * of independent {@code optional} fields rather than a {@code oneof}. Two cases:
 * <ul>
 *   <li><b>Within one response</b> a format submessage coexists with the Creative Commons overlay —
 *       the exact pair the former {@code oneof} forced to be a special case.</li>
 *   <li><b>Across the embedded tree</b> a parent and its embedded child carry distinct typed formats
 *       at the correct altitude, matching Tika's "a PDF carrying EXIF on embedded images". The child's
 *       image metadata lives on its own {@code EmbeddedDocument.parsed_content}, not on the parent.</li>
 * </ul>
 */
class ParseResponseCoexistenceTest {

    @Test
    void formatSubmessageCoexistsWithCreativeCommonsOverlay() {
        Metadata pdf = new Metadata();
        pdf.set(Metadata.CONTENT_TYPE, "application/pdf");
        pdf.set(XMPRights.MARKED, "True");
        pdf.set(XMPRights.OWNER, "Example Author");

        ParseResponse response = ParseResponseMapper.map(
                pdf, List.of(pdf), "body", "pdf-cc", "OK", 1L);

        // Both peers are populated on the same response — impossible under the former oneof.
        assertTrue(response.hasPdf(), "PDF format submessage should be populated");
        assertTrue(response.hasCreativeCommons(), "Creative Commons overlay should coexist with the format");
        assertEquals(DocumentFormatCategory.DOCUMENT_FORMAT_CATEGORY_PDF, response.getPrimaryFormat());
    }

    @Test
    void parentAndEmbeddedChildCarryDistinctTypedFormats() {
        Metadata pdf = new Metadata();
        pdf.set(Metadata.CONTENT_TYPE, "application/pdf");

        Metadata embeddedImage = new Metadata();
        embeddedImage.set(Metadata.CONTENT_TYPE, "image/jpeg");
        embeddedImage.set("tiff:ImageWidth", "800");

        // allMetadata: index 0 is the primary; index 1+ are embedded documents.
        ParseResponse response = ParseResponseMapper.map(
                pdf, List.of(pdf, embeddedImage), "body", "pdf-with-image", "OK", 1L);

        // Parent is typed PDF; the image metadata does NOT leak onto it.
        assertTrue(response.hasPdf());
        assertEquals(DocumentFormatCategory.DOCUMENT_FORMAT_CATEGORY_PDF, response.getPrimaryFormat());
        assertFalse(response.hasImage(), "embedded image metadata belongs to the child, not the parent");

        // The embedded child is independently typed IMAGE at its own altitude.
        assertEquals(1, response.getEmbeddedDocsCount());
        ParseResponse child = response.getEmbeddedDocs(0).getParsedContent();
        assertTrue(child.hasImage(), "embedded JPEG should be typed as image");
        assertEquals(DocumentFormatCategory.DOCUMENT_FORMAT_CATEGORY_IMAGE, child.getPrimaryFormat());
    }
}
