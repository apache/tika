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

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.mapper.builders.DocumentTypeDetector;
import org.apache.tika.grpc.mapper.builders.DocumentTypeDetector.DocumentType;
import org.apache.tika.grpc.v1.DocumentFormatCategory;
import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.metadata.Metadata;

class DocumentFormatCategoryTest {

    @Test
    void detectorMapsEveryDocumentTypeToProtoEnum() {
        for (DocumentType documentType : DocumentType.values()) {
            DocumentFormatCategory category = DocumentTypeDetector.toFormatCategory(documentType);
            assertEquals(
                    "DOCUMENT_FORMAT_CATEGORY_" + documentType.name(),
                    category.name(),
                    () -> "Mismatch for " + documentType);
        }
    }

    @Test
    void parseResponseSetsPrimaryFormatForPdf() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.pdf.PDFParser", "body", "doc");

        assertEquals(DocumentFormatCategory.DOCUMENT_FORMAT_CATEGORY_PDF, response.getPrimaryFormat());
        assertEquals("application/pdf", response.getContentType());
    }

    @Test
    void parseResponseSetsPrimaryFormatForGenericText() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        ParseResponse response = ParseResponseMapper.extractComprehensiveMetadata(
                metadata, "org.apache.tika.parser.DefaultParser", "body", "doc");

        assertEquals(DocumentFormatCategory.DOCUMENT_FORMAT_CATEGORY_GENERIC, response.getPrimaryFormat());
    }

}
