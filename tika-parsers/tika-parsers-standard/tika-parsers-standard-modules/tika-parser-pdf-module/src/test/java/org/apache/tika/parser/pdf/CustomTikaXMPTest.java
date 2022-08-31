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

import java.io.IOException;
import java.io.InputStream;

import org.apache.jempbox.xmp.XMPMetadata;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

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
        assertEquals("PDF/X-1:2001", metadata.get(PDF.PDFX_CONFORMANCE));
    }

    @Test
    public void testPDFUA() throws Exception {
        Metadata metadata = extract("testPDFUA.xmp");
        assertEquals(1, metadata.getInt(PDF.PDFUAID_PART));
    }

    @Test
    public void testPDFVT() throws Exception {
        Metadata metadata = extract("testPDFVT.xmp");
        assertEquals("PDF/VT-1", metadata.get(PDF.PDFVT_VERSION));
        assertEquals("2018-08-06T12:53:12Z", metadata.getDate(PDF.PDFVT_MODIFIED).toInstant().toString());
    }

    private Metadata extract(String xmpFileName) throws IOException, TikaException, SAXException {
        try (InputStream is = getResourceAsStream("/test-documents/xmp/" + xmpFileName)) {
            Document doc = XMLReaderUtils.buildDOM(is);
            XMPMetadata xmp = new XMPMetadata(doc);
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            PDMetadataExtractor.extract(xmp, metadata, context);
            return metadata;
        }
    }
}
