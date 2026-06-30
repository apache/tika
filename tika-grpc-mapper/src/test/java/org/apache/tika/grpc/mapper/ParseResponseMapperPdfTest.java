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

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.grpc.v1.PdfMetadata;

/**
 * Golden-field regression for {@code testPDF.pdf}, aligned with upstream {@code PDFParserTest}.
 */
class ParseResponseMapperPdfTest extends ParseFixtureSupport {

    @Test
    void mapsKnownPdfFieldsFromTestPdfFixture() throws Exception {
        ParseResponse response = map(parseBody("testPDF.pdf"), "testPDF.pdf");

        assertTrue(response.hasPdf(), "PDF documents should populate pdf oneof");
        assertFalse(response.hasGeneric());

        PdfMetadata pdf = response.getPdf();
        assertEquals("Apache Tika - Apache Tika", pdf.getDocInfoTitle());
        assertEquals("Bertrand Delacr\u00e9taz", pdf.getDocInfoCreator());
        assertTrue(pdf.hasDocInfoCreatorTool());
        assertEquals("Firefox", pdf.getDocInfoCreatorTool());
        assertTrue(pdf.hasPdfVersion());
        assertEquals("1.3", pdf.getPdfVersion());
        assertTrue(pdf.hasIsEncrypted());
        assertFalse(pdf.getIsEncrypted());
        assertTrue(pdf.getNPages() > 0);

        assertTrue(response.hasDublinCore());
        assertEquals("Apache Tika - Apache Tika", response.getDublinCore().getTitle());
        assertTrue(response.getDublinCore().getCreatorsList().contains("Bertrand Delacr\u00e9taz"));
    }

}
