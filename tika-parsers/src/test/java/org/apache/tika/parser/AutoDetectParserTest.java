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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class AutoDetectParserTest extends TestCase {
    private TikaConfig tika = TikaConfig.getDefaultConfig();

    // Easy to read constants for the MIME types:
    private static final String RAW        = "application/octet-stream";
    private static final String EXCEL      = "application/vnd.ms-excel";
    private static final String HTML       = "text/html; charset=ISO-8859-1";
    private static final String PDF        = "application/pdf";
    private static final String POWERPOINT = "application/vnd.ms-powerpoint";
    private static final String KEYNOTE    = "application/vnd.apple.keynote";
    private static final String PAGES      = "application/vnd.apple.pages";
    private static final String NUMBERS    = "application/vnd.apple.numbers";
    private static final String RTF        = "application/rtf";
    private static final String PLAINTEXT  = "text/plain; charset=ISO-8859-1";
    private static final String UTF8TEXT   = "text/plain; charset=UTF-8";
    private static final String WORD       = "application/msword";
    private static final String XML        = "application/xml";
    private static final String RSS        = "application/rss+xml";
    private static final String BMP        = "image/x-ms-bmp";
    private static final String GIF        = "image/gif";
    private static final String JPEG       = "image/jpeg";
    private static final String PNG        = "image/png";
    private static final String OGG_VORBIS = "audio/vorbis";
    private static final String OGG_FLAC   = "audio/x-flac";
    private static final String FLAC_NATIVE= "audio/x-flac";
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
            new AutoDetectParser(tika).parse(input, handler, metadata);

            assertEquals("Bad content type: " + tp,
                    tp.realType, metadata.get(Metadata.CONTENT_TYPE));

            if (tp.expectedContentFragment != null) {
               assertTrue("Expected content not found: " + tp,
                       handler.toString().contains(tp.expectedContentFragment));
            }
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
        assertAutoDetect(resource, null, type, null,          content);
        assertAutoDetect(resource, null, type, wrongMimeType, content);

        final String badResource = "a.xyz";
        assertAutoDetect(resource, badResource, type, type,          content);
        assertAutoDetect(resource, badResource, type, null,          content);
        assertAutoDetect(resource, badResource, type, wrongMimeType, content);
    }

    public void testKeynote() throws Exception {
        assertAutoDetect("testKeynote.key", KEYNOTE, "A sample presentation");
    }

    public void testPages() throws Exception {
        assertAutoDetect("testPages.pages", PAGES, "Sample pages document");
    }

    public void testNumbers() throws Exception {
        assertAutoDetect("testNumbers.numbers", NUMBERS, "Checking Account: 300545668");
    }

    public void testEpub() throws Exception {
        assertAutoDetect(
                "testEPUB.epub", "application/epub+zip",
                "The previous headings were subchapters");
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

    public void testRdfXml() throws Exception {
        assertAutoDetect("testRDF.rdf", "application/rdf+xml", "");
    }

    public void testRTF() throws Exception {
        assertAutoDetect("testRTF.rtf", RTF, "indexation Word");
    }

    public void testText() throws Exception {
        assertAutoDetect("testTXT.txt", PLAINTEXT, "indexation de Txt");
    }
    
    public void testTextNonASCIIUTF8() throws Exception {
        assertAutoDetect("testTXTNonASCIIUTF8.txt", UTF8TEXT, "The quick brown fox jumps over the lazy dog");
    }

    public void testWord() throws Exception {
        assertAutoDetect("testWORD.doc", WORD, "Sample Word Document");
    }

    public void testXML() throws Exception {
        assertAutoDetect("testXML.xml", XML, "Lius");
    }

    public void testRss() throws Exception {
        assertAutoDetect("/test-documents/rsstest.rss", "feed", RSS, "application/rss+xml", "Sample RSS File for Junit test");
    }
    
    public void testImages() throws Exception {
       assertAutoDetect("testBMP.bmp", BMP, null);
       assertAutoDetect("testGIF.gif", GIF, null);
       assertAutoDetect("testJPEG.jpg", JPEG, null);
       assertAutoDetect("testPNG.png", PNG, null);
   }

    /**
     * Make sure that zip bomb attacks are prevented.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-216">TIKA-216</a>
     */
    public void testZipBombPrevention() throws Exception {
        InputStream tgz = AutoDetectParserTest.class.getResourceAsStream(
                "/test-documents/TIKA-216.tgz");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler(-1);
            new AutoDetectParser(tika).parse(tgz, handler, metadata);
            fail("Zip bomb was not detected");
        } catch (TikaException e) {
            // expected
        } finally {
            tgz.close();
        }
    
    }

    /**
     * Test to ensure that the Vorbis and FLAC parsers have been correctly
     *  included, and are available
     */
    @SuppressWarnings("deprecation")
    public void testVorbisFlac() throws Exception {
       // The three test files should all have similar test data
       String[] testFiles = new String[] {
             "testVORBIS.ogg", "testFLAC.oga", "testFLAC.flac"
       };
       String[] mimetypes = new String[] {
             OGG_VORBIS, OGG_FLAC, FLAC_NATIVE
       };
       
       // Check we found the parser
       CompositeParser parser = (CompositeParser)tika.getParser();
       for (String type : mimetypes) {
          MediaType mt = MediaType.parse(type);
          assertNotNull("Parser not found for " + type, parser.getParsers().get(mt) );
       }
       
       // Have each file parsed, and check
       for (int i=0; i<testFiles.length; i++) {
          String file = testFiles[i];
          InputStream input = AutoDetectParserTest.class.getResourceAsStream(
                "/test-documents/"+file);

          if (input == null) {
             fail("Could not find test file " + file);
          }
          
          try {
             Metadata metadata = new Metadata();
             ContentHandler handler = new BodyContentHandler();
             new AutoDetectParser(tika).parse(input, handler, metadata);

             assertEquals("Incorrect content type for " + file,
                   mimetypes[i], metadata.get(Metadata.CONTENT_TYPE));

             // Check some of the common metadata
             assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));
             assertEquals("Test Title", metadata.get(Metadata.TITLE));
//             assertEquals("Test Artist", metadata.get(TikaCoreProperties.AUTHOR));
//             assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
             
             // Check some of the XMPDM metadata
             assertEquals("Test Album", metadata.get(XMPDM.ALBUM));
             assertEquals("Test Artist", metadata.get(XMPDM.ARTIST));
             assertEquals("Stereo", metadata.get(XMPDM.AUDIO_CHANNEL_TYPE));
             assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
             
             // Check some of the text
             String content = handler.toString();
             assertTrue(content.contains("Test Title"));
             assertTrue(content.contains("Test Artist"));
          } finally {
             input.close();
          }
       }
    }
    
    /**
     * Test case for TIKA-514. Provide constructor for AutoDetectParser that has explicit
     * list of supported parsers.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-514">TIKA-514</a>
     */
    public void testSpecificParserList() throws Exception {
        AutoDetectParser parser = new AutoDetectParser(new MyDetector(), new MyParser());
        
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Metadata metadata = new Metadata();
        parser.parse(is, new BodyContentHandler(), metadata, new ParseContext());
        
        assertEquals("value", metadata.get("MyParser"));
    }

    private static final MediaType MY_MEDIA_TYPE = new MediaType("application", "x-myparser");
    
    /**
     * A test detector which always returns the type supported
     *  by the test parser
     */
    @SuppressWarnings("serial")
    private static class MyDetector implements Detector {
        public MediaType detect(InputStream input, Metadata metadata) throws IOException {
            return MY_MEDIA_TYPE;
        }
    }
    
    @SuppressWarnings("serial")
    private static class MyParser extends AbstractParser {
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            Set<MediaType> supportedTypes = new HashSet<MediaType>();
            supportedTypes.add(MY_MEDIA_TYPE);
            return supportedTypes;
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
            metadata.add("MyParser", "value");
        }

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
         * <pre>
         * Test parameters:
         *   resourceRealName        = /test-documents/testEXCEL.xls
         *   resourceStatedName      = null
         *   realType                = application/vnd.ms-excel
         *   statedType              = null
         *   expectedContentFragment = Sample Excel Worksheet
         * </pre>
         */
        public String toString() {
            return "Test parameters:\n"
                + "  resourceRealName        = " + resourceRealName + "\n"
                + "  resourceStatedName      = " + resourceStatedName + "\n"
                + "  realType                = " + realType + "\n"
                + "  statedType              = " + statedType + "\n"
                + "  expectedContentFragment = " + expectedContentFragment + "\n";
        }
    }
}
