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

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class PowerPointParserTest extends TikaTest {

    @Test
    public void testPowerPointParser() throws Exception {
        try (InputStream input = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT.ppt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/vnd.ms-powerpoint",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Powerpoint Slide", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            String content = handler.toString();
            assertContains("Sample Powerpoint Slide", content);
            assertContains("Powerpoint X for Mac", content);
        }
    }

    @Test
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testPPT_various.ppt", metadata).xml;
        assertContains("<p>Footnote appears here", xml);
        assertContains("<p>[1] This is a footnote.", xml);
        assertContains("<p>This is the header text.</p>", xml);
        assertContains("<p>This is the footer text.</p>", xml);
        assertContains("<p>Here is a text box</p>", xml);
        assertContains("<p>Bold ", xml);
        assertContains("italic underline superscript subscript", xml);
        assertContains("underline", xml);
        assertContains("superscript", xml);
        assertContains("subscript", xml);
        assertContains("<p>Here is a citation:", xml);
        assertContains("Figure 1 This is a caption for Figure 1", xml);
        assertContains("(Kramer)", xml);
        assertContains("<table><tr>\t<td>Row 1 Col 1</td>", xml);
        assertContains("<td>Row 2 Col 2</td>\t<td>Row 2 Col 3</td></tr>", xml);
        assertContains("<p>Row 1 column 1</p>", xml);
        assertContains("<p>Row 2 column 2</p>", xml);
        assertContains("<p><a href=\"http://tika.apache.org/\">This is a hyperlink</a>", xml);
        assertContains("<p>Here is a list:", xml);
        for(int row=1;row<=3;row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("<li>Bullet " + row, xml);
        }
        assertContains("Here is a numbered list:", xml);
        for(int row=1;row<=3;row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("<li>Number bullet " + row, xml);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, xml);
            }
        }
        assertContains("Keyword1 Keyword2", xml);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(TikaCoreProperties.KEYWORDS));

        assertContains("Subject is here", xml);
        assertEquals("Subject is here",
                     metadata.get(OfficeOpenXMLCore.SUBJECT));
        // TODO: Remove subject in Tika 2.0
        assertEquals("Subject is here",
                     metadata.get(Metadata.SUBJECT));

        assertContains("Suddenly some Japanese text:", xml);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", xml);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                xml);

        assertContains("And then some Gothic text:", xml);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A",
                xml);
    }

    @Test
    public void testMasterFooter() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterFooter.ppt")) {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
        assertContains("Master footer is here", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));

        //TIKA-1171
        assertEquals(-1, content.indexOf("*"));
    }

    /**
     * TIKA-712 Master Slide Text from PPT and PPTX files
     *  should be extracted too
     */
    @Test
    public void testMasterText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText.ppt")) {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));

        //TIKA-1171
        assertEquals(-1, content.indexOf("*"));
    }

    @Test
    public void testMasterText2() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText2.ppt")) {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));
        //TIKA-1171
        assertEquals(-1, content.indexOf("*"));
    }

    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    @Test
    public void testCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (InputStream input = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_custom_props.ppt")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);
        }

        assertEquals("application/vnd.ms-powerpoint", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("JOUVIN ETIENNE", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("EJ04325S", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("EJ04325S", metadata.get(Metadata.LAST_AUTHOR));
        assertEquals("2011-08-22T13:32:58Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("2011-08-22T13:32:58Z", metadata.get(Metadata.DATE));
        assertEquals("2011-08-22T13:30:53Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-08-22T13:30:53Z", metadata.get(Metadata.CREATION_DATE));
        assertEquals("1", metadata.get(Office.SLIDE_COUNT));
        assertEquals("3", metadata.get(Office.WORD_COUNT));
        assertEquals("Test extraction properties pptx", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    // TIKA-1025
    @Test
    public void testEmbeddedPlacedholder() throws Exception {
        XMLResult result = getXML("testPPT_embedded2.ppt");
        assertContains("<div class=\"embedded\" id=\"1\" />", result.xml);
        assertContains("<div class=\"embedded\" id=\"14\" />", result.xml);
    }

    // TIKA-817
    @Test
    public void testAutoDatePPT() throws Exception {
        //decision was made in POI-52367 not to generate
        //autodate automatically.  For pptx, where value is stored,
        //value is extracted.  For ppt, however, no date is extracted.
        XMLResult result = getXML("testPPT_autodate.ppt");
        assertContains(
                "<div class=\"slide-content\"><p>Now</p>",
                result.xml);
    }

    @Test
    public void testCommentAuthorship() throws Exception {
        XMLResult r = getXML("testPPT_comment.ppt");
        assertContains("<p class=\"slide-comment\"><b>Allison, Timothy B. (ATB)", r.xml);
    }

    @Test
    public void testEmbeddedPDF() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.ppt");
        assertContains("Apache Tika project", metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("3.pdf", metadataList.get(1).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("Hello World", metadataList.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("4.pdf", metadataList.get(2).get(Metadata.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMacros() throws  Exception {
        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);


        List<Metadata> metadataList = getRecursiveMetadata("testPPT_macros.ppt", parseContext);
        assertContainsAtLeast(minExpected, metadataList);
    }


    @Test
    public void testSkippingBadCompressedObj() throws Exception {
        //test file is from govdocs1: 258642.ppt
        //TIKA-2130
        XMLResult r = getXML("testPPT_skipBadCompressedObject.ppt");
        assertContains("NASA Human", r.xml);
        assertEquals(2,
                r.metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM).length);
        assertContains("incorrect data check",
                r.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));

        List<Metadata> metadataList = getRecursiveMetadata("testPPT_skipBadCompressedObject.ppt");
        assertEquals(2,
                metadataList.get(0).getValues(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM).length);
        assertContains("incorrect data check",
                metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    @Test(expected = EncryptedDocumentException.class)
    public void testEncrypted() throws Exception {
        getXML("testPPT_protected_passtika.ppt");
    }
}
