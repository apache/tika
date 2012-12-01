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

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class PowerPointParserTest extends TikaTest {

    public void testPowerPointParser() throws Exception {
        InputStream input = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT.ppt");
        try {
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
            assertTrue(content.contains("Sample Powerpoint Slide"));
            assertTrue(content.contains("Powerpoint X for Mac"));
        } finally {
            input.close();
        }
    }

    public void testVarious() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_various.ppt");
        try {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        //content = content.replaceAll("\\s+"," ");
        assertContains("Footnote appears here", content);
        assertContains("This is a footnote.", content);
        assertContains("This is the header text.", content);
        assertContains("This is the footer text.", content);
        assertContains("Here is a text box", content);
        assertContains("Bold", content);
        assertContains("italic", content);
        assertContains("underline", content);
        assertContains("superscript", content);
        assertContains("subscript", content);
        assertContains("Here is a citation:", content);
        assertContains("Figure 1 This is a caption for Figure 1", content);
        assertContains("(Kramer)", content);
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3", content.replaceAll("\\s+"," "));
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2", content.replaceAll("\\s+"," "));
        assertContains("This is a hyperlink", content);
        assertContains("Here is a list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(TikaCoreProperties.KEYWORDS));

        assertContains("Subject is here", content);
        assertEquals("Subject is here",
                     metadata.get(OfficeOpenXMLCore.SUBJECT));
        // TODO: Remove subject in Tika 2.0
        assertEquals("Subject is here",
                     metadata.get(Metadata.SUBJECT));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", content);

        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    public void testMasterFooter() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterFooter.ppt");
        try {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Master footer is here", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));
    }

    // TODO: once we fix TIKA-712, re-enable this
    public void testMasterText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText.ppt");
        try {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));
    }

    // TODO: once we fix TIKA-712, re-enable this
    public void testMasterText2() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = PowerPointParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText2.ppt");
        try {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);

        // Make sure boilerplate text didn't come through:
        assertEquals(-1, content.indexOf("Click to edit Master"));
    }

    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    public void testCustomProperties() throws Exception {
       InputStream input = PowerPointParserTest.class.getResourceAsStream(
             "/test-documents/testPPT_custom_props.ppt");
       Metadata metadata = new Metadata();
       
       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OfficeParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }
       
       assertEquals("application/vnd.ms-powerpoint", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("JOUVIN ETIENNE",       metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("EJ04325S",             metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("EJ04325S",             metadata.get(Metadata.LAST_AUTHOR));
       assertEquals("2011-08-22T13:32:58Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2011-08-22T13:32:58Z", metadata.get(Metadata.DATE));
       assertEquals("2011-08-22T13:30:53Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2011-08-22T13:30:53Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("1",                    metadata.get(Office.SLIDE_COUNT));
       assertEquals("3",                    metadata.get(Office.WORD_COUNT));
       assertEquals("Test extraction properties pptx", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("true",                 metadata.get("custom:myCustomBoolean"));
       assertEquals("3",                    metadata.get("custom:myCustomNumber"));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
       assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    // TIKA-1025
    public void testEmbeddedPlacedholder() throws Exception {
       XMLResult result = getXML("testPPT_embedded2.ppt");
       assertContains("<div class=\"embedded\" id=\"1\"/>", result.xml);
       assertContains("<div class=\"embedded\" id=\"14\"/>", result.xml);
    }
}
