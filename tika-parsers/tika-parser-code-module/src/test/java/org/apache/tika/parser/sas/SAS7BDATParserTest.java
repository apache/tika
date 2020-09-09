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

import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.MachineMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

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
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = SAS7BDATParserTest.class.getResourceAsStream(
                "/test-documents/test-columnar.sas7bdat")) {            
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/x-sas-data", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("TESTING", metadata.get(TikaCoreProperties.TITLE));

        assertEquals("2018-05-18T11:38:30Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2018-05-18T11:38:30Z", metadata.get(TikaCoreProperties.MODIFIED));
        
        assertEquals("1", metadata.get(PagedText.N_PAGES));
        assertEquals("8", metadata.get(Database.COLUMN_COUNT));
        assertEquals("11", metadata.get(Database.ROW_COUNT));
        assertEquals("windows-1252", metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("X64_7PRO", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("9.0401M5", metadata.get(OfficeOpenXMLExtended.APP_VERSION));
        assertEquals("32", metadata.get(MachineMetadata.ARCHITECTURE_BITS));
        assertEquals("Little", metadata.get(MachineMetadata.ENDIAN));
        assertEquals(Arrays.asList("Record Number","Square of the Record Number",
                                   "Description of the Row","Percent Done",
                                   "Percent Increment","date","datetime","time"),
                     Arrays.asList(metadata.getValues(Database.COLUMN_NAME)));
        
        String content = handler.toString();
        assertContains("TESTING", content);
        assertContains("0\t0\tThis", content);
        assertContains("2\t4\tThis", content);
        assertContains("4\t16\tThis", content);
        assertContains("\t01-01-1960\t", content);
        assertContains("\t01Jan1960:00:00", content);
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
    
    @Test
    public void testHTML2() throws Exception {
        XMLResult result = getXML("test-columnar.sas7bdat");
        String xml = result.xml;

        // Check the title came through
        assertContains("<h1>TESTING</h1>", xml);
        // Check the headings
        assertContains("<th title=\"recnum\">Record Number</th>", xml);
        assertContains("<th title=\"square\">Square of the Record Number</th>", xml);
        assertContains("<th title=\"date\">date</th>", xml);
        // Check formatting of dates
        assertContains("<td>01-01-1960</td>", xml);
        assertContains("<td>01Jan1960:00:00:10.00</td>", xml);
    }
}
