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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.util.LocaleUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.OfficeParserTest;

public class OOXMLParserTest extends MultiThreadedTikaTest {

    private static Locale USER_LOCALE = null;

    @BeforeAll
    public static void setUp() {
        USER_LOCALE = LocaleUtil.getUserLocale();
    }

    @AfterAll
    public static void tearDown() {
        LocaleUtil.setUserLocale(USER_LOCALE);
        Locale.setDefault(USER_LOCALE);
    }

    @BeforeEach
    public void beforeEach() {
        LocaleUtil.setUserLocale(Locale.US);
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testExcel() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL.xlsx", metadata, context);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));

        assertContains("Sample Excel Worksheet", content);
        assertContains("Numbers and their Squares", content);
        assertContains("9", content);
        assertNotContained("9.0", content);
        assertContains("196", content);
        assertNotContained("196.0", content);
        assertEquals("false", metadata.get(Office.PROTECTED_WORKSHEET));

    }

    @Test
    public void testExcelFormats() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL-formats.xlsx", metadata, context);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));

        // Number #,##0.00
        assertContains("1,599.99", content);
        assertContains("-1,599.99", content);

        // Currency $#,##0.00;[Red]($#,##0.00)
        assertContains("$1,599.99", content);
        assertContains("$1,599.99)", content);

        // Scientific 0.00E+00
        // poi <=3.8beta1 returns 1.98E08, newer versions return 1.98+E08
        assertTrue(content.contains("1.98E08") || content.contains("1.98E+08"));
        assertTrue(content.contains("-1.98E08") || content.contains("-1.98E+08"));

        // Percentage
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

        // Currency $#,##0.00;[Red]($#,##0.00)
        assertContains("$1,599.99", content);
        assertContains("($1,599.99)", content);

        // Fraction (2.5): # ?/?
        assertContains("2 1/2", content);

        // Below assertions represent outstanding formatting issues to be addressed
        // they are included to allow the issues to be progressed with the Apache POI
        // team - See TIKA-103.

        /*************************************************************************
         // Date Format: m/d/yy
         assertContains("03/10/2009", content);

         // Date/Time Format
         assertContains("19/01/2008 04:35", content);

         // Custom Number (0 "dollars and" .00 "cents")
         assertContains("19 dollars and .99 cents", content);

         // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
         assertContains("At 4:20 AM on Thursday May 17, 2007", content);
         **************************************************************************/

    }

    @Test
    @Disabled("OOXML-Strict not currently supported by POI, see #57699")
    public void testExcelStrict() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL.strict.xlsx", metadata, context);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Spreadsheet", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Nick Burch", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Spreadsheet for testing", metadata.get(TikaCoreProperties.DESCRIPTION));
        assertContains("Test spreadsheet", content);
        assertContains("This one is red", content);
        assertContains("cb=10", content);
        assertNotContained("10.0", content);
        assertContains("cb=sum", content);
        assertNotContained("13.0", content);
        assertEquals("false", metadata.get(Office.PROTECTED_WORKSHEET));

    }

    /**
     * Documents with some sheets are protected, but not all.
     * See TIKA-364.
     */
    @Test
    public void testProtectedExcelSheets() throws Exception {

        Metadata metadata = getXML("protectedSheets.xlsx").metadata;

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));

        assertEquals("true", metadata.get(Office.PROTECTED_WORKSHEET));

    }

    /**
     * An excel document which is password protected.
     * See TIKA-437.
     */
    @Test
    public void testProtectedExcelFile() throws Exception {
        XMLResult xmlResult = getXML("protectedFile.xlsx");

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));

        assertEquals("true", xmlResult.metadata.get(Office.PROTECTED_WORKSHEET));

        assertContains("Office", xmlResult.xml);
    }

    /**
     * Ensures that custom OOXML properties are extracted
     */
    @Test
    public void testExcelCustomProperties() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        getXML("testEXCEL_custom_props.xlsx", metadata, context);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(null, metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(null, metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2006-09-12T15:06:44Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-08-22T14:24:38Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("Microsoft Excel", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    //TIKA-1100:
    @Test
    public void testExcelTextBox() throws Exception {
        XMLResult r = getXML("testEXCEL_textbox.xlsx");
        assertContains("some autoshape", r.xml);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtractionExcel() throws Exception {

        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testEXCEL_textbox.xlsx", pc).xml;
        assertNotContained("autoshape", xml);
    }

    @Test
    public void testXLSXThumbnail() throws Exception {
        String xml = getXML("testXLSX_Thumbnail.xlsx").xml;
        int a = xml.indexOf("This file contains an embedded thumbnail by default");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.wmf\" />");

        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<>();
        //the first three contain javax.crypto.CipherInputStream
        tests.put("testWORD_protected_passtika.docx", "This is an encrypted Word 2007 File");
        tests.put("testPPT_protected_passtika.pptx", "This is an encrypted PowerPoint 2007 slide.");
        tests.put("testEXCEL_protected_passtika.xlsx", "This is an Encrypted Excel spreadsheet.");
        //TIKA-2873 this one contains a ChunkedCipherInputStream
        //that is buggy at the POI level...can unwrap TikaInputStream in OfficeParser
        //once https://bz.apache.org/bugzilla/show_bug.cgi?id=63431 is fixed.
        tests.put("testEXCEL_protected_passtika_2.xlsx",
                "This is an Encrypted Excel spreadsheet with a ChunkedCipherInputStream.");

        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        };
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(org.apache.tika.parser.PasswordProvider.class, passwordProvider);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            XMLResult xmlResult = getXML(e.getKey(), passwordContext);
            assertContains(e.getValue(), xmlResult.xml);
        }

        ParseContext context = new ParseContext();
        //now try with no password
        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try {
                getXML(e.getKey());
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }
    }

    @Test
    public void testExcelHeaderAndFooterExtraction() throws Exception {
        XMLResult xml = getXML("testEXCEL_headers_footers.xlsx");

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xml.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Internal spreadsheet", xml.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Aeham Abushwashi", xml.metadata.get(TikaCoreProperties.CREATOR));

        String content = xml.xml;
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

    @Test
    public void testExcelHeaderAndFooterNotExtraction() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        String content = getXML("testEXCEL_headers_footers.xlsx", parseContext).xml;
        assertNotContained("Header - Corporate Spreadsheet", content);
        assertNotContained("Header - For Internal Use Only", content);
        assertNotContained("Header - Author: John Smith", content);
        assertNotContained("Footer - Corporate Spreadsheet", content);
        assertNotContained("Footer - For Internal Use Only", content);
        assertNotContained("Footer - Author: John Smith", content);

        //now test configuration via tika-config
        Parser configuredParser = TikaLoader.load(
                getConfigPath(OfficeParserTest.class, "tika-config-headers-footers.json"))
                .loadAutoDetectParser();
        content = getXML("testEXCEL_headers_footers.xlsx", configuredParser).xml;
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


    @Test
    public void testHyperlinksInXLSX() throws Exception {
        String xml = getXML("testEXCEL_hyperlinks.xlsx").xml;
        //external url
        assertContains("<a href=\"http://tika.apache.org/\">", xml);
        //mail url
        assertContains("<a href=\"mailto:user@tika.apache.org?subject=help\">", xml);
        //external linked file
        assertContains("<a href=\"linked_file.txt.htm\">", xml);
        //link on textbox
        assertContains("<a href=\"http://tika.apache.org/1.12/gettingstarted.html\">", xml);
    }


    @Test
    public void testBigIntegersWGeneralFormat() throws Exception {
        //TIKA-2025
        String xml = getXML("testEXCEL_big_numbers.xlsx").xml;
        assertContains("123456789012345", xml);//15 digit number
        assertContains("123456789012346", xml);//15 digit formula
        Locale locale = LocaleUtil.getUserLocale();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        //16 digit number is treated as scientific notation as is the 16 digit formula
        assertContains("1" + symbols.getDecimalSeparator() + "23456789012345E+15</td>\t" + "<td>1" +
                symbols.getDecimalSeparator() + "23456789012345E+15", xml);
    }

    @Test
    public void testBigIntegersWGeneralFormatWLocaleIT() throws Exception {
        LocaleUtil.setUserLocale(Locale.ITALIAN);
        //TIKA-2438
        try {
            String xml = getXML("testEXCEL_big_numbers.xlsx").xml;
            assertContains("123456789012345", xml);//15 digit number
            assertContains("123456789012346", xml);//15 digit formula
            Locale locale = LocaleUtil.getUserLocale();

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
            //16 digit number is treated as scientific notation as is the 16 digit formula
            assertContains(
                    "1" + symbols.getDecimalSeparator() + "23456789012345E+15</td>\t" + "<td>1" +
                            symbols.getDecimalSeparator() + "23456789012345E+15", xml);
        } finally {
            LocaleUtil.setUserLocale(USER_LOCALE);
        }
    }


    @Test
    public void testMacroinXlsm() throws Exception {

        //test default is "don't extract macros"
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_macro.xlsm");
        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }
        assertEquals("ThisWorkbook", metadataList.get(0).get(Office.WORKBOOK_CODENAME));

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Dirty()");
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "dirty dirt dirt");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, getRecursiveMetadata("testEXCEL_macro.xlsm", context));

        //test configuring via config file
        Parser parser = TikaLoader.load(
                getConfigPath(OOXMLParserTest.class, "tika-config-dom-macros.json"))
                .loadAutoDetectParser();
        assertContainsAtLeast(minExpected,
                getRecursiveMetadata("testEXCEL_macro.xlsm", parser));
    }

    @Test
    public void testExcelXLSB() throws Exception {
        Detector detector = new DefaultDetector();

        Metadata m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "excel.xlsb");

        // Should be detected correctly
        MediaType type;
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testEXCEL.xlsb")) {
            type = detector.detect(tis, m, new ParseContext());
            assertEquals("application/vnd.ms-excel.sheet.binary.macroenabled.12", type.toString());
        }

        // OfficeParser won't handle it
        assertEquals(false,
                (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));

        // OOXMLParser will (soon) handle it
        assertTrue((new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));

        // AutoDetectParser doesn't break on it
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        String content = getText("testEXCEL.xlsb", new Metadata(), context);
        assertContains("This is an example spreadsheet", content);
    }

    @Test
    public void testXLSBVarious() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_various.xlsb", parseContext);
        assertEquals(4, metadataList.size());

        String xml = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("<td>13</td>", xml);
        assertContains("<td>13.1211231321</td>", xml);
        assertContains("<td>$   3.03</td>", xml);
        assertContains("<td>20%</td>", xml);
        assertContains("<td>13.12</td>", xml);
        assertContains("<td>123456789012345</td>", xml);
        assertContains("<td>1.23456789012345E+15</td>", xml);
        assertContains("test comment2", xml);

        assertContains("comment4 (end of row)", xml);


        assertContains("<td>1/4</td>", xml);
        assertContains("<td>3/9/17</td>", xml);
        assertContains("<td>4</td>", xml);
        assertContains("<td>2</td>", xml);

        assertContains("<td>   46/1963</td>", xml);
        assertContains("<td>  3/128</td>", xml);
        assertContains("test textbox", xml);

        assertContains("test WordArt", xml);

        assertContains("<a href=\"http://lucene.apache.org/\">http://lucene.apache.org/</a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\">http://tika.apache.org/</a>", xml);

        assertContains("OddLeftHeader OddCenterHeader OddRightHeader", xml);
        assertContains("EvenLeftHeader EvenCenterHeader EvenRightHeader", xml);

        assertContains("FirstPageLeftHeader FirstPageCenterHeader FirstPageRightHeader", xml);
        assertContains("OddLeftFooter OddCenterFooter OddRightFooter", xml);
        assertContains("EvenLeftFooter EvenCenterFooter EvenRightFooter", xml);
        assertContains("FirstPageLeftFooter FirstPageCenterFooter FirstPageRightFooter", xml);

    }

    @Test
    public void testXLSBNoHeaderFooters() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testEXCEL_various.xlsb", parseContext).xml;
        assertNotContained("OddLeftHeader OddCenterHeader OddRightHeader", xml);
        assertNotContained("EvenLeftHeader EvenCenterHeader EvenRightHeader", xml);

        assertNotContained("FirstPageLeftHeader FirstPageCenterHeader FirstPageRightHeader", xml);
        assertNotContained("OddLeftFooter OddCenterFooter OddRightFooter", xml);
        assertNotContained("EvenLeftFooter EvenCenterFooter EvenRightFooter", xml);
        assertNotContained("FirstPageLeftFooter FirstPageCenterFooter FirstPageRightFooter", xml);

    }

    @Test
    public void testPOI61034() throws Exception {
        //tests temporary work around until POI 3.17-beta1 is released
        XMLResult r = getXML("testEXCEL_poi-61034.xlsx");
        Matcher m = Pattern.compile("<h1>(Sheet\\d+)</h1>").matcher(r.xml);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String sheetName = m.group(1);
            if (seen.contains(sheetName)) {
                fail("Should only see each sheet once: " + sheetName);
            }
            seen.add(sheetName);
        }

    }

    @Test
    public void testXLSBOriginalPath() throws Exception {
        assertEquals("C:\\Users\\tallison\\Desktop\\working\\TIKA-1945\\",
                getXML("testEXCEL_diagramData.xlsb").metadata
                        .get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }

    @Test
    public void testXLSXOriginalPath() throws Exception {
        assertEquals("C:\\Users\\tallison\\Desktop\\working\\TIKA-1945\\",
                getXML("testEXCEL_diagramData.xlsx").metadata
                        .get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }

    @Test
    public void testXLSBDiagramData() throws Exception {
        assertContains("SmartArt", getXML("testEXCEL_diagramData.xlsb").xml);
    }

    @Test
    public void testXLSXDiagramData() throws Exception {
        assertContains("SmartArt", getXML("testEXCEL_diagramData.xlsx").xml);
    }

    @Test
    public void testXLSXChartData() throws Exception {
        String xml = getXML("testEXCEL_charts.xlsx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testXLSBChartData() throws Exception {
        String xml = getXML("testEXCEL_charts.xlsb").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testXLSXPhoneticStrings() throws Exception {
        //This unit test and test file come from Apache POI 51519.xlsx

        //test default concatenates = true
        assertContains("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx").xml);

        //test turning it off
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setConcatenatePhoneticRuns(false);
        ParseContext pc = new ParseContext();
        pc.set(OfficeParserConfig.class, officeParserConfig);
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx", pc).xml);


        //test configuring via config file
        Parser parser = TikaLoader.load(
                getConfigPath(OfficeParserTest.class, "tika-config-exclude-phonetic.json"))
                .loadAutoDetectParser();
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx", parser).xml);

    }

    @Test
    public void testEmbeddedXLSInOLEObject() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_oleWorkbook.pptx");
        assertEquals(4, metadataList.size());
        Metadata xlsx = metadataList.get(2);
        assertContains("<h1>Sheet1</h1>", xlsx.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("<td>1</td>", xlsx.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx.get(Metadata.CONTENT_TYPE));
    }


    @Test
    public void testSigned() throws Exception {
        Metadata m = getXML("testWORD_signed.docx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

        m = getXML("testEXCEL_signed.xlsx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

        m = getXML("testPPT_signed.pptx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

    }

    @Test
    public void testDateFormat() throws Exception {
        Parser p = TikaLoader.load(
                getConfigPath(OOXMLParserTest.class, "tika-config-custom-date-override.json"))
                .loadAutoDetectParser();
        String xml = getXML("testEXCEL_dateFormats.xlsx", p).xml;
        assertContains("2018-09-20", xml);
        assertContains("1996-08-10", xml);
    }

    @Test
    public void testDocSecurity() throws Exception {
        assertEquals(OfficeOpenXMLExtended.SECURITY_PASSWORD_PROTECTED,
                getRecursiveMetadata("protectedFile.xlsx").get(0)
                        .get(OfficeOpenXMLExtended.DOC_SECURITY_STRING));
        assertEquals(OfficeOpenXMLExtended.SECURITY_READ_ONLY_ENFORCED,
                getRecursiveMetadata("testWORD_docSecurity.docx").get(0)
                        .get(OfficeOpenXMLExtended.DOC_SECURITY_STRING));
    }

    @Test
    public void testMultiThreaded() throws Exception {
        //TIKA-3627
        int numThreads = 5;
        int numIterations = 5;
        ParseContext[] parseContexts = new ParseContext[numThreads];
        for (int i = 0; i < parseContexts.length; i++) {
            parseContexts[i] = new ParseContext();
        }
        Set<String> extensions = new HashSet<>();
        extensions.add(".pptx");
        extensions.add(".docx");
        extensions.add(".xlsx");
        extensions.add(".ppt");
        extensions.add(".doc");
        extensions.add(".xls");
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        testMultiThreaded(wrapper, parseContexts, numThreads, numIterations, path -> {
            String pathName = path.getName().toLowerCase(Locale.ENGLISH);
            if (pathName.equalsIgnoreCase("testRecordSizeExceeded.xlsx")) {
                return false;
            }
            int i = pathName.lastIndexOf(".");
            String ext = "";
            if (i > -1) {
                ext = pathName.substring(i);
            }
            return extensions.contains(ext);
        });

    }

    @Test
    public void testNoRecordSizeOverflow() throws Exception {
        //TIKA-4474 -- test: files (passed as stream) no longer have limit on record size as they are spooled
        String content = getText("testRecordSizeExceeded.xlsx");
        assertContains("Repetitive content pattern 3 for compression test row 1", content);
    }

}
