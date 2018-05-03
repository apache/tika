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
package org.apache.tika.parser.sas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.executable.MachineMetadata;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class SAS7BDATParserTest extends TikaTest {
    private Parser parser = new SAS7BDATParser();
    
    @Test
    public void testSimpleFile() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = SAS7BDATParserTest.class.getResourceAsStream(
                "/test-documents/testSAS.sas7bdat")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/x-sas-data", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("TESTING", metadata.get(TikaCoreProperties.TITLE));

        // Mon Jan 30 07:31:47 GMT 2017
        assertEquals("2017-01-30T07:31:47Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2017-01-30T07:31:47Z", metadata.get(TikaCoreProperties.MODIFIED));
        
        assertEquals("1", metadata.get(PagedText.N_PAGES));
        assertEquals("2", metadata.get(Database.COLUMN_COUNT));
        assertEquals("11", metadata.get(Database.ROW_COUNT));
        assertEquals("windows-1252", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("W32_7PRO", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("9.0301M2", metadata.get(OfficeOpenXMLExtended.APP_VERSION));
        assertEquals("32", metadata.get(MachineMetadata.ARCHITECTURE_BITS));
        assertEquals("Little", metadata.get(MachineMetadata.ENDIAN));
        assertEquals(Arrays.asList("recnum","label"),
                     Arrays.asList(metadata.getValues(Database.COLUMN_NAME)));
        
        String content = handler.toString();
        assertContains("TESTING", content);
        assertContains("\t3\t", content);
        assertContains("\t10\t", content);
        assertContains("\tThis is row", content);
        assertContains(" of ", content);
    }
    
    @Test
    public void testMultiColumns() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = SAS7BDATParserTest.class.getResourceAsStream(
                "/test-documents/test-columnar.sas7bdat")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/x-sas-data", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("SHEET1", metadata.get(TikaCoreProperties.TITLE));

        // Fri Mar 06 19:10:19 GMT 2015
        assertEquals("2015-03-06T19:10:19Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2015-03-06T19:10:19Z", metadata.get(TikaCoreProperties.MODIFIED));
        
        assertEquals("1", metadata.get(PagedText.N_PAGES));
        assertEquals("5", metadata.get(Database.COLUMN_COUNT));
        assertEquals("31", metadata.get(Database.ROW_COUNT));
        assertEquals("windows-1252", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("XP_PRO", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("9.0101M3", metadata.get(OfficeOpenXMLExtended.APP_VERSION));
        assertEquals("32", metadata.get(MachineMetadata.ARCHITECTURE_BITS));
        assertEquals("Little", metadata.get(MachineMetadata.ENDIAN));
        assertEquals(Arrays.asList("A","B","C","D","E"),
                     Arrays.asList(metadata.getValues(Database.COLUMN_NAME)));
        
        String content = handler.toString();
        assertContains("SHEET1", content);
        assertContains("A\tB\tC", content);
        assertContains("Num=0\t", content);
        assertContains("Num=404242\t", content);
        assertContains("\t0\t", content);
        assertContains("\t404242\t", content);
        assertContains("\t08Feb1904\t", content);
    }

    @Test
    public void testHTML() throws Exception {
        XMLResult result = getXML("testSAS.sas7bdat");
        String xml = result.xml;

        // Check the title came through
        assertContains("<h1>TESTING</h1>", xml);
        // Check the headings
        assertContains("<th title=\"recnum\">recnum</th>", xml);
        assertContains("<th title=\"label\">label</th>", xml);
        // Check some rows
        assertContains("<td>3</td>", xml);
        assertContains("<td>This is row", xml);
        assertContains("10</td>", xml);
    }

    // TODO Column names vs labels, with a different test file
    // TODO Columnar consistency test
}
