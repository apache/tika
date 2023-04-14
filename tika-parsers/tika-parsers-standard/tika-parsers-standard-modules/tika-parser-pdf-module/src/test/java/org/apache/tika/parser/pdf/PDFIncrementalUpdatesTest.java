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
package org.apache.tika.parser.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;

public class PDFIncrementalUpdatesTest extends TikaTest {
    /*
    Test files with incremental updates?
    testPDF_Version.4.x.pdf 1
    testPDFTwoTextBoxes.pdf 1
    testPDF_IncrementalUpdates.pdf 2
    testOptionalHyphen.pdf 1
    testPageNumber.pdf 1
    testPDF_twoAuthors.pdf 1
    testPDF_XFA_govdocs1_258578.pdf 1
    testJournalParser.pdf 1
    testPDF_bookmarks.pdf 1
    testPDFVarious.pdf 1
     */

    @Test
    public void testIncrementalUpdateInfoExtracted() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setExtractIncrementalUpdateInfo(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata(
                "testPDF_IncrementalUpdates.pdf",
                parseContext);
        assertEquals(2, metadataList.get(0).getInt(PDF.PDF_INCREMENTAL_UPDATES));
    }

    //TODO: add parsing and tests
    //TODO: embed the incremental updates PDF inside another doc and confirm it works


    //TODO -- add tests for the scanner with failed EOF, etc.
}
