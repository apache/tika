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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.parser.ParseContext;

/**
 * This tests our custom schemas and PDF/A
 */
public class CustomTikaXMPTest extends TikaTest {

    @Test
    public void testPDFA() throws Exception {
        Metadata metadata = extract("testPDFA.xmp");
        assertEquals("A-1b", metadata.get(PDF.PDFA_VERSION));
        assertEquals(1, metadata.getInt(PDF.PDFAID_PART));
        assertEquals("B", metadata.get(PDF.PDFAID_CONFORMANCE));
    }

    @Test
    public void testPDFX() throws Exception {
        Metadata metadata = extract("testPDFX.xmp");
        assertEquals("PDF/X-1:2001", metadata.get(PDF.PDFXID_VERSION));
        assertEquals("PDF/X-1:2001", metadata.get(PDF.PDFX_VERSION));
        assertEquals("PDF/X-1a:2001", metadata.get(PDF.PDFX_CONFORMANCE));
    }

    @Test
    public void testPDFUA() throws Exception {
        Metadata metadata = extract("testPDFUA.xmp");
        assertEquals(1, metadata.getInt(PDF.PDFUAID_PART));
        // keywords (pdf:Keywords via the Office.KEYWORDS composite) and subject (dc:subject) both
        // land in SUBJECT; presence is the contract, order is not.
        List<String> subjects = Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT));
        assertEquals(2, subjects.size());
        assertTrue(subjects.contains("keywords"), "keywords in SUBJECT");
        assertTrue(subjects.contains("subject"), "subject in SUBJECT");
        assertEquals("1234567890", metadata.get(XMP.IDENTIFIER));
        assertEquals("Advisory", metadata.get(XMP.ADVISORY));
    }

    @Test
    public void testPDFVT() throws Exception {
        Metadata metadata = extract("testPDFVT.xmp");
        assertEquals("PDF/VT-1", metadata.get(PDF.PDFVT_VERSION));
        assertEquals("2018-08-06T11:53:12Z",
                metadata.getDate(PDF.PDFVT_MODIFIED).toInstant().toString());
    }

    /**
     * Test dublin core properties.
     * 
     * @throws Exception 
     */
    @Test
    public void testDublinCore() throws Exception {
        Metadata metadata = extract("TIKA-4442.xmp"); // test file based on file 188032
        assertEquals("research papers", metadata.get(TikaCoreProperties.TYPE));
        assertEquals("doi:1234/S56789", metadata.get(TikaCoreProperties.IDENTIFIER));
        assertEquals("en", metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("International Union of Thinkology", metadata.get(TikaCoreProperties.PUBLISHER));
        assertEquals("Relation", metadata.get(TikaCoreProperties.RELATION));
        assertEquals("Journal of Thinkology", metadata.get(TikaCoreProperties.SOURCE));
        assertEquals("Copyright (c) 1939 International Union of Thinkology", metadata.get(TikaCoreProperties.RIGHTS));
        assertEquals("Thinking: is it needed?", metadata.get(TikaCoreProperties.DESCRIPTION));
        String[] subjects = metadata.getValues(TikaCoreProperties.SUBJECT);
        assertEquals(5, subjects.length);
        assertEquals("THOUGHTS", subjects[0]);
        assertEquals("HAPPINESS", subjects[1]);
        assertEquals("FEAR", subjects[2]);
        assertEquals("ANGER", subjects[3]);
        assertEquals("DESPAIR", subjects[4]);
        String[] creators = metadata.getValues(TikaCoreProperties.CREATOR);
        assertEquals(5, creators.length);
        assertEquals("Dorothy", creators[0]);
        assertEquals("Toto", creators[1]);
        assertEquals("Scarecrow", creators[2]);
        assertEquals("Tin Man", creators[3]);
        assertEquals("Cowardly Lion", creators[4]);
    }

    private Metadata extract(String xmpFileName) throws IOException, TikaException, SAXException {
        try (TikaInputStream tis = getResourceAsStream("/test-documents/xmp/" + xmpFileName)) {
            Metadata metadata = new Metadata();
            PDMetadataExtractor.extract(tis, metadata, new ParseContext());
            return metadata;
        }
    }
}
