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

import java.io.InputStream;
import java.util.Locale;

import junit.framework.TestCase;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class ExcelParserTest extends TestCase {
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
            assertTrue(content.contains("Sample Excel Worksheet"));
            assertTrue(content.contains("Numbers and their Squares"));
            assertTrue(content.contains("\t\tNumber\tSquare"));
            assertTrue(content.contains("9"));
            assertFalse(content.contains("9.0"));
            assertTrue(content.contains("196"));
            assertFalse(content.contains("196.0"));
        } finally {
            input.close();
        }
    }

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
            assertTrue(content.contains("1,599.99"));
            assertTrue(content.contains("-1,599.99"));

            // Currency $#,##0.00;[Red]($#,##0.00)
            assertTrue(content.contains("$1,599.99"));
            assertTrue(content.contains("($1,599.99)"));

            // Scientific 0.00E+00
            // poi <=3.8beta1 returns 1.98E08, newer versions return 1.98+E08
            assertTrue(content.contains("1.98E08") || content.contains("1.98E+08"));
            assertTrue(content.contains("-1.98E08") || content.contains("-1.98E+08"));

            // Percentage.
            assertTrue(content.contains("2.50%"));
            // Excel rounds up to 3%, but that requires Java 1.6 or later
            if(System.getProperty("java.version").startsWith("1.5")) {
                assertTrue(content.contains("2%"));
            } else {
                assertTrue(content.contains("3%"));
            }

            // Time Format: h:mm
            assertTrue(content.contains("6:15"));
            assertTrue(content.contains("18:15"));

            // Date Format: d-mmm-yy
            assertTrue(content.contains("17-May-07"));

            // Date Format: m/d/yy
            assertTrue(content.contains("10/3/09"));
            
            // Date/Time Format: m/d/yy h:mm
            assertTrue(content.contains("1/19/08 4:35"));

            
            // Below assertions represent outstanding formatting issues to be addressed
            // they are included to allow the issues to be progressed with the Apache POI
            // team - See TIKA-103.

            /*************************************************************************
            // Custom Number (0 "dollars and" .00 "cents")
            assertTrue(content.contains("19 dollars and .99 cents"));

            // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
            assertTrue(content.contains("At 4:20 AM on Thursday May 17, 2007"));

            // Fraction (2.5): # ?/?  (TODO Coming in POI 3.8 beta 6)
            assertTrue(content.contains("2 1 / 2"));
            **************************************************************************/

        } finally {
            input.close();
        }
    }

    /**
     * TIKA-214 - Ensure we extract labels etc from Charts
     */
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
            assertTrue(content.contains("charttabyodawg"));
            assertTrue(content.contains("WhamPuff"));
            
            // The second sheet has a bar chart and some text
            assertTrue(content.contains("Sheet1"));
            assertTrue(content.contains("Test Excel Spreasheet"));
            assertTrue(content.contains("foo"));
            assertTrue(content.contains("bar"));
            assertTrue(content.contains("fizzlepuff"));
            assertTrue(content.contains("whyaxis"));
            assertTrue(content.contains("eksaxis"));
            
            // The third sheet has some text
            assertTrue(content.contains("Sheet2"));
            assertTrue(content.contains("dingdong"));
        } finally {
            input.close();
        }
    }

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
            assertTrue(content.contains("Number Formats"));
        } finally {
            input.close();
        }
    }
    
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
            assertTrue(content.contains("Microsoft Works"));
        } finally {
            input.close();
        }
    }

    /**
     * We don't currently support the .xlsb file format 
     *  (an OOXML container with binary blobs), but we 
     *  shouldn't break on these files either (TIKA-826)  
     */
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
     * We don't currently support the old Excel 95 .xls file format, 
     *  but we shouldn't break on these files either (TIKA-976)  
     */
    public void testExcel95() throws Exception {
       Detector detector = new DefaultDetector();
       AutoDetectParser parser = new AutoDetectParser();
       
       InputStream input = ExcelParserTest.class.getResourceAsStream(
             "/test-documents/testEXCEL_95.xls");
       Metadata m = new Metadata();
       m.add(Metadata.RESOURCE_NAME_KEY, "excel_95.xls");
       
       // Should be detected correctly
       MediaType type = null;
       try {
          type = detector.detect(input, m);
          assertEquals("application/vnd.ms-excel", type.toString());
       } finally {
          input.close();
       }
       
       // OfficeParser will claim to handle it
       assertEquals(true, (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));
       
       // OOXMLParser won't handle it
       assertEquals(false, (new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));
       
       // AutoDetectParser doesn't break on it
       input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_95.xls");

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
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
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
