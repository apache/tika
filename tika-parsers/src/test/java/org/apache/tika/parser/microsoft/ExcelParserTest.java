/**
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
package org.apache.tika.parser.microsoft;

import static org.apache.tika.TikaTest.assertContains;
import static org.apache.tika.TikaTest.assertNotContained;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Locale;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class ExcelParserTest {
    @Test
    @SuppressWarnings("deprecation") // Checks legacy Tika-1.0 style metadata keys
    public void testExcelParser() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL.xls");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            
            // Mon Oct 01 17:13:56 BST 2007
            assertEquals("2007-10-01T16:13:56Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("2007-10-01T16:13:56Z", metadata.get(Metadata.CREATION_DATE));
            
            // Mon Oct 01 17:31:43 BST 2007
            assertEquals("2007-10-01T16:31:43Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2007-10-01T16:31:43Z", metadata.get(Metadata.DATE));
            
            String content = handler.toString();
            assertContains("Sample Excel Worksheet", content);
            assertContains("Numbers and their Squares", content);
            assertContains("\t\tNumber\tSquare", content);
            assertContains("9", content);
            assertNotContained("9.0", content);
            assertContains("196", content);
            assertNotContained("196.0", content);
        } finally {
            input.close();
        }
    }

    @Test
    public void testExcelParserFormatting() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL-formats.xls");
        try {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            ContentHandler handler = new BodyContentHandler();
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();

            // Number #,##0.00
            assertContains("1,599.99", content);
            assertContains("-1,599.99", content);

            // Currency $#,##0.00;[Red]($#,##0.00)
            assertContains("$1,599.99", content);
            assertContains("($1,599.99)", content);

            // Scientific 0.00E+00
            // poi <=3.8beta1 returns 1.98E08, newer versions return 1.98+E08
            assertTrue(content.contains("1.98E08") || content.contains("1.98E+08"));
            assertTrue(content.contains("-1.98E08") || content.contains("-1.98E+08"));

            // Percentage.
            assertContains("2.50%", content);
            // Excel rounds up to 3%, but that requires Java 1.6 or later
            if(System.getProperty("java.version").startsWith("1.5")) {
                assertContains("2%", content);
            } else {
                assertContains("3%", content);
            }

            // Time Format: h:mm
            assertContains("6:15", content);
            assertContains("18:15", content);

            // Date Format: d-mmm-yy
            assertContains("17-May-07", content);

            // Date Format: m/d/yy
            assertContains("10/3/09", content);
            
            // Date/Time Format: m/d/yy h:mm
            assertContains("1/19/08 4:35", content);

            // Fraction (2.5): # ?/?
            assertContains("2 1/2", content);

            
            // Below assertions represent outstanding formatting issues to be addressed
            // they are included to allow the issues to be progressed with the Apache POI
            // team - See TIKA-103.

            /*************************************************************************
            // Custom Number (0 "dollars and" .00 "cents")
            assertContains("19 dollars and .99 cents", content);

            // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
            assertContains("At 4:20 AM on Thursday May 17, 2007", content);
            **************************************************************************/

        } finally {
            input.close();
        }
    }
    
    @Test
    public void testExcelParserPassword() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_protected_passtika.xls");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);
            fail("Document is encrypted, shouldn't parse");
        } catch (EncryptedDocumentException e) {
            // Good
        } finally {
            input.close();
        }

        // Try again, this time with the password
        input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_protected_passtika.xls");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            context.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return "tika";
                }
            });
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));
            
            assertEquals(null, metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Antoni", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("2011-11-25T09:52:48Z", metadata.get(TikaCoreProperties.CREATED));
            
            String content = handler.toString();
            assertContains("This is an Encrypted Excel spreadsheet", content);
            assertNotContained("9.0", content);
        } finally {
            input.close();
        }
    }

    /**
     * TIKA-214 - Ensure we extract labels etc from Charts
     */
    @Test
    public void testExcelParserCharts() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                  "/test-documents/testEXCEL-charts.xls");
        try {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            ContentHandler handler = new BodyContentHandler();
            new OfficeParser().parse(input, handler, metadata, context);
        
            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));
        
            String content = handler.toString();
            
            // The first sheet has a pie chart
            assertContains("charttabyodawg", content);
            assertContains("WhamPuff", content);
            
            // The second sheet has a bar chart and some text
            assertContains("Sheet1", content);
            assertContains("Test Excel Spreasheet", content);
            assertContains("foo", content);
            assertContains("bar", content);
            assertContains("fizzlepuff", content);
            assertContains("whyaxis", content);
            assertContains("eksaxis", content);
            
            // The third sheet has some text
            assertContains("Sheet2", content);
            assertContains("dingdong", content);
        } finally {
            input.close();
        }
    }

    @Test
    public void testJXL() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/jxl.xls");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));
            String content = handler.toString();
            assertContains("Number Formats", content);
        } finally {
            input.close();
        }
    }
    
    @Test
    public void testWorksSpreadsheet70() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testWORKSSpreadsheet7.0.xlr");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);

            String content = handler.toString();
            assertContains("Microsoft Works", content);
        } finally {
            input.close();
        }
    }

    /**
     * We don't currently support the .xlsb file format 
     *  (an OOXML container with binary blobs), but we 
     *  shouldn't break on these files either (TIKA-826)  
     */
    @Test
    public void testExcelXLSB() throws Exception {
       Detector detector = new DefaultDetector();
       AutoDetectParser parser = new AutoDetectParser();
       
       InputStream input = ExcelParserTest.class.getResourceAsStream(
             "/test-documents/testEXCEL.xlsb");
       Metadata m = new Metadata();
       m.add(Metadata.RESOURCE_NAME_KEY, "excel.xlsb");
       
       // Should be detected correctly
       MediaType type = null;
       try {
          type = detector.detect(input, m);
          assertEquals("application/vnd.ms-excel.sheet.binary.macroenabled.12", type.toString());
       } finally {
          input.close();
       }
       
       // OfficeParser won't handle it
       assertEquals(false, (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));
       
       // OOXMLParser won't handle it
       assertEquals(false, (new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));
       
       // AutoDetectParser doesn't break on it
       input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL.xlsb");

       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          parser.parse(input, handler, m, context);

          String content = handler.toString();
          assertEquals("", content);
       } finally {
          input.close();
       }
    }

    /**
     * Excel 5 and 95 are older formats, and only get basic support
     */
    @Test
    public void testExcel95() throws Exception {
       Detector detector = new DefaultDetector();
       AutoDetectParser parser = new AutoDetectParser();
       InputStream input;
       MediaType type;
       Metadata m;
       
       // First try detection of Excel 5
       m = new Metadata();
       m.add(Metadata.RESOURCE_NAME_KEY, "excel_5.xls");
       input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_5.xls");
       try {
           type = detector.detect(input, m);
           assertEquals("application/vnd.ms-excel", type.toString());
        } finally {
           input.close();
        }
       
       // Now Excel 95
       m = new Metadata();
       m.add(Metadata.RESOURCE_NAME_KEY, "excel_95.xls");
       input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_95.xls");
       try {
           type = detector.detect(input, m);
           assertEquals("application/vnd.ms-excel", type.toString());
        } finally {
           input.close();
        }

        // OfficeParser can handle it
        assertEquals(true, (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));

        // OOXMLParser won't handle it
        assertEquals(false, (new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));
       
        
        // Parse the Excel 5 file
        m = new Metadata();
        input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_5.xls");
        try {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            parser.parse(input, handler, m, context);

            String content = handler.toString();
            
            // Sheet names
            assertContains("Feuil1", content);
            assertContains("Feuil3", content);
            
            // Text
            assertContains("Sample Excel", content);
            assertContains("Number", content);
            
            // Numbers
            assertContains("15", content);
            assertContains("225", content);
            
            // Metadata was also fetched
            assertEquals("Simple Excel document", m.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", m.get(TikaCoreProperties.CREATOR));
        } finally {
            input.close();
        }
        
        // Parse the Excel 95 file
        m = new Metadata();
        input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_95.xls");
        try {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            parser.parse(input, handler, m, context);

            String content = handler.toString();
            
            // Sheet name
            assertContains("Foglio1", content);
            
            // Very boring file, no actual text or numbers!
            
            // Metadata was also fetched
            assertEquals(null, m.get(TikaCoreProperties.TITLE));
            assertEquals("Marco Quaranta", m.get(Office.LAST_AUTHOR));
        } finally {
            input.close();
        }
    }
    
    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    @Test
    public void testCustomProperties() throws Exception {
       InputStream input = ExcelParserTest.class.getResourceAsStream(
             "/test-documents/testEXCEL_custom_props.xls");
       Metadata metadata = new Metadata();
       
       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OfficeParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }
       
       assertEquals("application/vnd.ms-excel", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("",                     metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("",                     metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("2011-08-22T13:45:54Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2006-09-12T15:06:44Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("Microsoft Excel",      metadata.get(OfficeOpenXMLExtended.APPLICATION));
       assertEquals("true",                 metadata.get("custom:myCustomBoolean"));
       assertEquals("3",                    metadata.get("custom:myCustomNumber"));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
       assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }
}
