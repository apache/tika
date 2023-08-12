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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.io.RandomAccessRead;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.updates.StartXRefOffset;
import org.apache.tika.parser.pdf.updates.StartXRefScanner;

public class PDFIncrementalUpdatesTest extends TikaTest {
    /*
    Test files with incremental updates?
    testPDF_Version.4.x.pdf 1
    testPDFTwoTextBoxes.pdf 1
    testPDF_incrementalUpdates.pdf 2
    testOptionalHyphen.pdf 1
    testPageNumber.pdf 1
    testPDF_twoAuthors.pdf 1
    testPDF_XFA_govdocs1_258578.pdf 1
    testJournalParser.pdf 1
    testPDF_bookmarks.pdf 1
    testPDFVarious.pdf 1
     */

    /*
        Many thanks to Tyler Thorsted for sharing "testPDF_incrementalUpdates.pdf"
     */

    @Test
    public void testIncrementalUpdateInfoExtracted() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setExtractIncrementalUpdateInfo(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata(
                "testPDF_incrementalUpdates.pdf",
                parseContext);
        assertEquals(2, metadataList.get(0).getInt(TikaCoreProperties.VERSION_COUNT));
        assertEquals(2, metadataList.get(0).getInt(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
        long[] expected = new long[]{16242, 41226, 64872};
        long[] eofs = metadataList.get(0).getLongValues(PDF.EOF_OFFSETS);
        assertEquals(3, eofs.length);
        assertArrayEquals(expected, eofs);
    }

    @Test
    public void testTooLongLong() throws Exception {
        String s = "blah blah startxref 01234567890123456789\n%%EOF blah";
        assertEquals(0, getOffsets(s).size());
    }

    @Test
    public void testMissingEOF() throws Exception {
        String s = "blah blah startxref 123456\nblah";
        assertEquals(1, getOffsets(s).size());
    }
    @Test
    public void testBrokenEOF() throws Exception {
        String s = "blah blah startxref 123456\n%%EO\nstartxref 234567\n%%EOF\nblah";
        assertEquals(2, getOffsets(s).size());
    }

    @Test
    public void testNoSpace1() throws Exception {
        String s = "blah blah startxref123456\n%%EOF\nblah";
        assertEquals(1, getOffsets(s).size());
    }

    @Test
    public void testNoSpace2() throws Exception {
        String s = "blah blah startxref 123456%%EOF\nblah";
        assertEquals(1, getOffsets(s).size());
    }

    @Test
    public void testNoStartXref() throws Exception {
        String s = "blah blah startxref not a startxre";
        assertEquals(0, getOffsets(s).size());
    }

    @Test
    public void testLongAtEOF() throws Exception {
        //we should not count longs at EOF because
        //they might be truncated?
        String s = "blah blah startxref 100";
        assertEquals(0, getOffsets(s).size());
    }
    @Test
    public void testCommentInsteadOfEOF() throws Exception {
        String s = "blah blah startxref 123456\n%%regular comment\n%%EOF";
        assertEquals(1, getOffsets(s).size());
    }

    @Test
    public void testStartxStartXref() throws Exception {
        //make sure that we are rewinding last character
        String s = "blah blah startxstartxref 123456\n%%EOFblah";
        assertEquals(1, getOffsets(s).size());
    }


    private List<StartXRefOffset> getOffsets(String s) throws IOException {
        //TODO PDFBOX30 replace RandomAccessBuffer with RandomAccessReadBuffer
        try (RandomAccessRead randomAccessRead =
                new RandomAccessBuffer(s.getBytes(StandardCharsets.US_ASCII))) {
            StartXRefScanner scanner = new StartXRefScanner(randomAccessRead);
            return scanner.scan();
        }
    }

    @Test
    public void testIncrementalUpdateParsing() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setParseIncrementalUpdates(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata(
                "testPDF_incrementalUpdates.pdf",
                parseContext);
        assertEquals(3, metadataList.size());
        assertEquals(2, metadataList.get(0).getInt(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
        assertEquals(2, metadataList.get(0).getInt(TikaCoreProperties.VERSION_COUNT));
        long[] expected = new long[]{16242, 41226, 64872};
        long[] eofs = metadataList.get(0).getLongValues(PDF.EOF_OFFSETS);
        assertEquals(3, eofs.length);
        assertArrayEquals(expected, eofs);

        assertContains("Testing Incremental", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("Testing Incremental",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("Testing Incremental", metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));

        assertNull(metadataList.get(0).get(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertNull(metadataList.get(0).get(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertEquals(0, metadataList.get(1).getInt(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertEquals(1, metadataList.get(2).getInt(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertEquals("/version-number-0",
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/version-number-1",
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));

        assertNull(metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertNull(metadataList.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));

        assertEquals(TikaCoreProperties.EmbeddedResourceType.VERSION.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.VERSION.toString(),
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    //TODO: embed the incremental updates PDF inside another doc and confirm it works

}
