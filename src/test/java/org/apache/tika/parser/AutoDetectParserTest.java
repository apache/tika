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
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class AutoDetectParserTest extends TestCase {

    // Easy to read constants for the MIME types:
    private static final String RAW        = "application/octet-stream";
    private static final String EXCEL      = "application/vnd.ms-excel";
    private static final String HTML       = "text/html";
    private static final String PDF        = "application/pdf";
    private static final String POWERPOINT = "application/vnd.ms-powerpoint";
    private static final String RTF        = "application/rtf";
    private static final String PLAINTEXT  = "text/plain";
    private static final String WORD       = "application/msword";
    private static final String XML        = "application/xml";
    private static final String OPENOFFICE
            = "application/vnd.oasis.opendocument.text";


    /**
     * This is where a single test is done.
     * @param tp the parameters encapsulated in a TestParams instance
     * @throws IOException
     */
    private void assertAutoDetect(TestParams tp) throws Exception {

        InputStream input =
            AutoDetectParserTest.class.getResourceAsStream(tp.resourceRealName);

        if (input == null) {
            fail("Could not open stream from specified resource: "
                    + tp.resourceRealName);
        }

        try {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, tp.resourceStatedName);
            metadata.set(Metadata.CONTENT_TYPE, tp.statedType);
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);

            assertEquals("Bad content type: " + tp,
                    tp.realType, metadata.get(Metadata.CONTENT_TYPE));

            assertTrue("Expected content not found: " + tp,
                    handler.toString().contains(tp.expectedContentFragment));
        } finally {
            input.close();
        }
    }

    /**
     * Convenience method -- its sole purpose of existence is to make the
     * call to it more readable than it would be if a TestParams instance
     * would need to be instantiated there.
     *
     * @param resourceRealName real name of resource
     * @param resourceStatedName stated name -- will a bad name fool us?
     * @param realType - the real MIME type
     * @param statedType - stated MIME type - will a wrong one fool us?
     * @param expectedContentFragment - something expected in the text
     * @throws Exception
     */
    private void assertAutoDetect(String resourceRealName,
                                  String resourceStatedName,
                                  String realType,
                                  String statedType,
                                  String expectedContentFragment)
            throws Exception {

        assertAutoDetect(new TestParams(resourceRealName, resourceStatedName,
                realType, statedType, expectedContentFragment));
    }

    private void assertAutoDetect(
            String resource, String type, String content) throws Exception {

        resource = "/test-documents/" + resource;

        // TODO !!!!  The disabled tests below should work!
        // The correct MIME type should be determined regardless of the
        // stated type (ContentType hint) and the stated URL name.


        // Try different combinations of correct and incorrect arguments:
        final String wrongMimeType = RAW;
        assertAutoDetect(resource, resource, type, type,          content);
        assertAutoDetect(resource, resource, type, null,          content);
        assertAutoDetect(resource, resource, type, wrongMimeType, content);

        assertAutoDetect(resource, null, type, type,          content);
//        assertAutoDetect(resource, null, type, null,          content);
//        assertAutoDetect(resource, null, type, wrongMimeType, content);

        final String badResource = "a.xyz";
//        assertAutoDetect(resource, badResource, type, type,          content);
//        assertAutoDetect(resource, badResource, type, null,          content);
//        assertAutoDetect(resource, badResource, type, wrongMimeType, content);
    }


    public void testExcel() throws Exception {
        assertAutoDetect("testEXCEL.xls", EXCEL, "Sample Excel Worksheet");
    }

    public void testHTML() throws Exception {
        assertAutoDetect("testHTML.html", HTML, "Test Indexation Html");
    }

    public void testOpenOffice() throws Exception {
        assertAutoDetect("testOpenOffice2.odt", OPENOFFICE,
                "This is a sample Open Office document");
    }

    public void testPDF() throws Exception {
        assertAutoDetect("testPDF.pdf", PDF, "Content Analysis Toolkit");

    }

    public void testPowerpoint() throws Exception {
        assertAutoDetect("testPPT.ppt", POWERPOINT, "Sample Powerpoint Slide");
    }

    public void testRTF() throws Exception {
        assertAutoDetect("testRTF.rtf", RTF, "indexation Word");
    }

    public void testText() throws Exception {
        assertAutoDetect("testTXT.txt", PLAINTEXT, "indexation de Txt");
    }

    public void testWord() throws Exception {
        assertAutoDetect("testWORD.doc", WORD, "Sample Word Document");
    }

    public void testXML() throws Exception {
        assertAutoDetect("testXML.xml", XML, "Lius");
    }

    /**
     * Minimal class to encapsulate all parameters -- the main reason for
     * its existence is to aid in debugging via its toString() method.
     *
     * Getters and setters intentionally not provided.
     */
    private static class TestParams {

        public String resourceRealName;
        public String resourceStatedName;
        public String realType;
        public String statedType;
        public String expectedContentFragment;


        private TestParams(String resourceRealName,
                           String resourceStatedName,
                           String realType,
                           String statedType,
                           String expectedContentFragment) {
            this.resourceRealName = resourceRealName;
            this.resourceStatedName = resourceStatedName;
            this.realType = realType;
            this.statedType = statedType;
            this.expectedContentFragment = expectedContentFragment;
        }


        /**
         * Produces a string like the following:
         *
         * org.apache.tika.parser.AutoDetectParserTest$TestParams@8fff06[
         *   resourceRealName=/test-documents/testEXCEL.xls
         *   resourceStatedName=<null>
         *   realType=application/vnd.ms-excel
         *   statedType=<null>
         *   expectedContentFragment=Sample Excel Worksheet
         * ]
         *
         * @return
         */

        public String toString() {
            return ReflectionToStringBuilder.toString(
                    this, ToStringStyle.MULTI_LINE_STYLE);
        }
    }
}
