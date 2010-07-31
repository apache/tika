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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
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
            assertEquals("Simple Excel document", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
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
            assertTrue(content.contains("1.98E08"));
            assertTrue(content.contains("-1.98E08"));

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

            // Below assertions represent outstanding formatting issues to be addressed
            // they are included to allow the issues to be progressed with the Apache POI
            // team - See TIKA-103.

            /*************************************************************************
            // Date Format: m/d/yy
            assertTrue(content.contains("03/10/2009"));

            // Date/Time Format
            assertTrue(content.contains("19/01/2008 04:35"));

            // Custom Number (0 "dollars and" .00 "cents")
            assertTrue(content.contains("19 dollars and .99 cents"));

            // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
            assertTrue(content.contains("At 4:20 AM on Thursday May 17, 2007"));

            // Fraction (2.5): # ?/?
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
}
