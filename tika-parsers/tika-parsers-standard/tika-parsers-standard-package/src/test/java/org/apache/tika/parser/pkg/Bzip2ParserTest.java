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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing bzip2 files.
 */
public class Bzip2ParserTest extends AbstractPkgTest {

    @Test
    public void testBzip2Parsing() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/test-documents.tbz2")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-bzip2", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }

}
