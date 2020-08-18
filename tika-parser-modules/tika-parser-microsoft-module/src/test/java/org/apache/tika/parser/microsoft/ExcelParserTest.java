/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.apache.poi.util.LocaleUtil;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
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
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class ExcelParserTest extends TikaTest {
    @Test
    @SuppressWarnings("deprecation") // Checks legacy Tika-1.0 style metadata keys
    public void testExcelParser() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL.xls")) {
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

            // Mon Oct 01 17:13:56 BST 2007
            assertEquals("2007-10-01T16:13:56Z", metadata.get(TikaCoreProperties.CREATED));

            // Mon Oct 01 17:31:43 BST 2007
            assertEquals("2007-10-01T16:31:43Z", metadata.get(TikaCoreProperties.MODIFIED));

            String content = handler.toString();
            assertContains("Sample Excel Worksheet", content);
            assertContains("Numbers and their Squares", content);
            assertContains("\t\tNumber\tSquare", content);
            assertContains("9", content);
            assertNotContained("9.0", content);
            assertContains("196", content);
            assertNotContained("196.0", content);


            // Won't include missing rows by default
            assertContains("Numbers and their Squares\n\t\tNumber", content);
            assertContains("\tSquare\n\t\t1", content);
        }

        // Request with missing rows
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL.xls")) {
            OfficeParserConfig config = new OfficeParserConfig();
            config.setIncludeMissingRows(true);

            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            context.set(OfficeParserConfig.class, config);
            new OfficeParser().parse(input, handler, metadata, context);

            // Will now have the missing rows, each with a single empty cell
            String content = handler.toString();
            assertContains("Numbers and their Squares\n\t\n\t\n\t\tNumber", content);
            assertContains("\tSquare\n\t\n\t\t1", content);
        }
    }

    @Test
    public void testExcelParserFormatting() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL-formats.xls")) {
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
            if (System.getProperty("java.version").startsWith("1.5")) {
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

        }
    }

    @Test
    public void testExcelParserPassword() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_protected_passtika.xls")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);
            fail("Document is encrypted, shouldn't parse");
        } catch (EncryptedDocumentException e) {
            // Good
        }

        // Try again, this time with the password
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_protected_passtika.xls")) {
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
        }
    }

    /**
     * TIKA-214 - Ensure we extract labels etc from Charts
     */
    @Test
    public void testExcelParserCharts() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL-charts.xls")) {
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
        }
    }

    @Test
    public void testJXL() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/jxl.xls")) {
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
        }
    }

    @Test
    public void testWorksSpreadsheet70() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testWORKSSpreadsheet7.0.xlr")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);

            String content = handler.toString();
            assertContains("Microsoft Works", content);
        }
    }


    /**
     * Excel 5 and 95 are older formats, and only get basic support
     */
    @Test
    public void testExcel95() throws Exception {
        Detector detector = new DefaultDetector();
        MediaType type;
        Metadata m;

        // First try detection of Excel 5
        m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "excel_5.xls");
        try (InputStream input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_5.xls")) {
            type = detector.detect(input, m);
            assertEquals("application/vnd.ms-excel", type.toString());
        }

        // Now Excel 95
        m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "excel_95.xls");
        try (InputStream input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_95.xls")) {
            type = detector.detect(input, m);
            assertEquals("application/vnd.ms-excel", type.toString());
        }

        // OfficeParser can handle it
        assertEquals(true, (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));

        // OOXMLParser won't handle it
        assertEquals(false, (new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));


        // Parse the Excel 5 file
        m = new Metadata();
        try (InputStream input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_5.xls")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            AUTO_DETECT_PARSER.parse(input, handler, m, context);

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
        }

        // Parse the Excel 95 file
        m = new Metadata();
        try (InputStream input = ExcelParserTest.class.getResourceAsStream("/test-documents/testEXCEL_95.xls")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            AUTO_DETECT_PARSER.parse(input, handler, m, context);

            String content = handler.toString();

            // Sheet name
            assertContains("Foglio1", content);

            // Very boring file, no actual text or numbers!

            // Metadata was also fetched
            assertEquals(null, m.get(TikaCoreProperties.TITLE));
            assertEquals("Marco Quaranta", m.get(Office.LAST_AUTHOR));
        }
    }

    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    @Test
    public void testCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_custom_props.xls")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);
        }

        assertEquals("application/vnd.ms-excel", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2011-08-22T13:45:54Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("2006-09-12T15:06:44Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("Microsoft Excel", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

	@Test
    public void testHeaderAndFooterExtraction() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_headers_footers.xls")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.UK);
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Internal spreadsheet", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Aeham Abushwashi", metadata.get(TikaCoreProperties.CREATOR));

            String content = handler.toString();
            assertContains("John Smith1", content);
            assertContains("John Smith50", content);
            assertContains("1 Corporate HQ", content);
            assertContains("Header - Corporate Spreadsheet", content);
            assertContains("Header - For Internal Use Only", content);
            assertContains("Header - Author: John Smith", content);
            assertContains("Footer - Corporate Spreadsheet", content);
            assertContains("Footer - For Internal Use Only", content);
            assertContains("Footer - Author: John Smith", content);
        }
    }

    @Test
    public void testHeaderAndFooterNotExtraction() throws Exception {
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL_headers_footers.xls")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.UK);

            OfficeParserConfig officeParserConfig = new OfficeParserConfig();
            officeParserConfig.setIncludeHeadersAndFooters(false);
            context.set(OfficeParserConfig.class, officeParserConfig);
            new OfficeParser().parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.ms-excel",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();
            assertContains("John Smith1", content);
            assertContains("John Smith50", content);
            assertContains("1 Corporate HQ", content);
            assertNotContained("Header - Corporate Spreadsheet", content);
            assertNotContained("Header - For Internal Use Only", content);
            assertNotContained("Header - Author: John Smith", content);
            assertNotContained("Footer - Corporate Spreadsheet", content);
            assertNotContained("Footer - For Internal Use Only", content);
            assertNotContained("Footer - Author: John Smith", content);
        }
    }


    @Test
    public void testHyperlinksInXLS() throws Exception {
        String xml = getXML("testEXCEL_hyperlinks.xls").xml;
        //external url
        assertContains("<a href=\"http://tika.apache.org/\">", xml);
        //mail url
        assertContains("<a href=\"mailto:user@tika.apache.org?subject=help\">", xml);
        //external linked file
        assertContains("<a href=\"linked_file.txt.htm\">", xml);

        //TODO: not extracting these yet
        //link on textbox
//        assertContains("<a href=\"http://tika.apache.org/1.12/gettingstarted.html\">", xml);
    }



    @Test
    public void testBigIntegersWGeneralFormat() throws Exception {
        //TIKA-2025
        String xml = getXML("testEXCEL_big_numbers.xls").xml;
        assertContains("123456789012345", xml);//15 digit number
        assertContains("123456789012346", xml);//15 digit formula
        Locale locale = LocaleUtil.getUserLocale();
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        //16 digit number is treated as scientific notation as is the 16 digit formula
        assertContains("1"+symbols.getDecimalSeparator()+"23456789012345E+15</td>\t"+
                "<td>1"+symbols.getDecimalSeparator()+"23456789012345E+15", xml);
    }

    @Test
    public void testMacros() throws  Exception {
        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testEXCEL_macro.xls")) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Dirty()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "dirty dirt dirt");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, getRecursiveMetadata("testEXCEL_macro.xls", context));

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertContainsAtLeast(minExpected, getRecursiveMetadata("testEXCEL_macro.xls", parser));

    }

    @Test
    public void testTextBox() throws Exception {
        String xml = getXML("testEXCEL_textbox.xls").xml;
        assertContains("autoshape", xml);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtractionExcel() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testEXCEL_textbox.xls", pc).xml;
        assertNotContained("autoshape", xml);
    }

    @Test
    public void testPhoneticStrings() throws Exception {
        //This unit test and test file come from Apache POI 51519.xlsx

        //test default concatenates = true
        assertContains("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xls").xml);

        //test turning it off
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setConcatenatePhoneticRuns(false);
        ParseContext pc = new ParseContext();
        pc.set(OfficeParserConfig.class, officeParserConfig);
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xls", pc).xml);

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(OfficeParser.class.getResourceAsStream("tika-config-exclude-phonetic.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xls", parser).xml);

    }

    @Test
    public void testLabelsAreExtracted() throws Exception {
        String xml = getXML("testEXCEL_labels-govdocs-515858.xls").xml;
        assertContains("Morocco", xml);
    }

    @Test
    public void testWorkBookInCapitals() throws Exception {
        String xml = getXML("testEXCEL_WORKBOOK_in_capitals.xls").xml;
        assertContains("Inventarliste", xml);
    }

    @Test
    public void testDateFormat() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                this.getClass().getResourceAsStream("tika-config-custom-date-override.xml"));
        Parser p = new AutoDetectParser(tikaConfig);
        String xml = getXML("testEXCEL_dateFormats.xls", p).xml;
        assertContains("2018-09-20", xml);
        assertContains("1996-08-10", xml);
    }
}
