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
package org.apache.tika.mime;

// Junit imports
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

/**
 * 
 * Test Suite for the {@link MimeTypes} repository.
 * 
 */
public class TestMimeTypes extends TestCase {

    private MimeTypes repo;

    private static URL u;

    static {
        try {
            u = new URL("http://mydomain.com/x.pdf?x=y");
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }

    private static final File f = new File("/a/b/c/x.pdf");

    public TestMimeTypes() {
        try {
            repo = TikaConfig.getDefaultConfig().getMimeRepository();
        } catch (Exception e) {
            fail(e.getMessage());
        }

    }

    public void testCaseSensitivity() {
        MimeType type = repo.getMimeType("test.PDF");
        assertNotNull(type);
        assertEquals(repo.getMimeType("test.pdf"), type);
        assertEquals(repo.getMimeType("test.PdF"), type);
        assertEquals(repo.getMimeType("test.pdF"), type);
    }

    public void testLoadMimeTypes() throws MimeTypeException {
        assertNotNull(repo.forName("application/octet-stream"));
        assertNotNull(repo.forName("text/x-tex"));
    }

    /**
     * Tests MIME type determination based solely on the URL's extension.
     */
    public void testGuessMimeTypes() throws Exception {
        assertTypeByName("application/pdf", "x.pdf");
        assertEquals("application/pdf", repo.getMimeType(u).getName());
        assertEquals("application/pdf", repo.getMimeType(f).getName());
        assertTypeByName("text/plain", "x.txt");
        assertTypeByName("text/html", "x.htm");
        assertTypeByName("text/html", "x.html");
        assertTypeByName("application/xhtml+xml", "x.xhtml");
        assertTypeByName("application/xml", "x.xml");
        assertTypeByName("application/zip", "x.zip");
        assertTypeByName("application/vnd.oasis.opendocument.text", "x.odt");
        assertTypeByName("application/octet-stream", "x.unknown");

        // Test for the MS Office media types and file extensions listed in
        // http://blogs.msdn.com/vsofficedeveloper/pages/Office-2007-Open-XML-MIME-Types.aspx
        assertTypeByName("application/msword", "x.doc");
        assertTypeByName("application/msword", "x.dot");
        assertTypeByName("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x.docx");
        assertTypeByName("application/vnd.openxmlformats-officedocument.wordprocessingml.template", "x.dotx");
        assertTypeByName("application/vnd.ms-word.document.macroenabled.12", "x.docm");
        assertTypeByName("application/vnd.ms-word.template.macroenabled.12", "x.dotm");
        assertTypeByName("application/vnd.ms-excel", "x.xls");
        assertTypeByName("application/vnd.ms-excel", "x.xlt");
        assertTypeByName("application/vnd.ms-excel", "x.xla");
        assertTypeByName("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x.xlsx");
        assertTypeByName("application/vnd.openxmlformats-officedocument.spreadsheetml.template", "x.xltx");
        assertTypeByName("application/vnd.ms-excel.sheet.macroenabled.12", "x.xlsm");
        assertTypeByName("application/vnd.ms-excel.template.macroenabled.12", "x.xltm");
        assertTypeByName("application/vnd.ms-excel.addin.macroenabled.12", "x.xlam");
        assertTypeByName("application/vnd.ms-excel.sheet.binary.macroenabled.12", "x.xlsb");
        assertTypeByName("application/vnd.ms-powerpoint", "x.ppt");
        assertTypeByName("application/vnd.ms-powerpoint", "x.pot");
        assertTypeByName("application/vnd.ms-powerpoint", "x.pps");
        assertTypeByName("application/vnd.ms-powerpoint", "x.ppa");
        assertTypeByName("application/vnd.openxmlformats-officedocument.presentationml.presentation", "x.pptx");
        assertTypeByName("application/vnd.openxmlformats-officedocument.presentationml.template", "x.potx");
        assertTypeByName("application/vnd.openxmlformats-officedocument.presentationml.slideshow", "x.ppsx");
        assertTypeByName("application/vnd.ms-powerpoint.addin.macroenabled.12", "x.ppam");
        assertTypeByName("application/vnd.ms-powerpoint.presentation.macroenabled.12", "x.pptm");
        assertTypeByName("application/vnd.ms-powerpoint.template.macroenabled.12", "x.potm");
        assertTypeByName("application/vnd.ms-powerpoint.slideshow.macroenabled.12", "x.ppsm");

        assertTypeByName("application/x-staroffice-template", "x.vor");
        assertTypeByData("application/x-tika-msoffice", "testVORCalcTemplate.vor");
        assertTypeByData("application/x-tika-msoffice", "testVORDrawTemplate.vor");
        assertTypeByData("application/x-tika-msoffice", "testVORImpressTemplate.vor");
        assertTypeByData("application/x-tika-msoffice", "testVORWriterTemplate.vor");
        assertTypeByNameAndData("application/x-staroffice-template", "testVORCalcTemplate.vor");
        assertTypeByNameAndData("application/x-staroffice-template", "testVORDrawTemplate.vor");
        assertTypeByNameAndData("application/x-staroffice-template", "testVORImpressTemplate.vor");
        assertTypeByNameAndData("application/x-staroffice-template", "testVORWriterTemplate.vor");
    }

    /**
     * Note - detecting container formats by mime magic is very very
     *  iffy, as we can't be sure where things will end up.
     * People really ought to use the container aware detection...
     */
    public void testOoxmlDetection() throws Exception {
        // These two do luckily have [Content_Types].xml near the start,
        //  so our mime magic will spot them
        assertTypeByData("application/x-tika-ooxml", "testEXCEL.xlsx");
        assertTypeByData("application/x-tika-ooxml", "testPPT.pptx");
        
        // This one quite legitimately doesn't have its [Content_Types].xml
        //  file as one of the first couple of entries
        // As such, our mime magic can't figure it out...
        assertTypeByData("application/zip", "testWORD.docx");
        
        assertTypeByNameAndData("application/vnd.ms-excel.sheet.binary.macroenabled.12","testEXCEL.xlsb");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.presentation.macroenabled.12", "testPPT.pptm");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.template.macroenabled.12", "testPPT.potm");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.slideshow.macroenabled.12", "testPPT.ppsm");
    }

    /**
     * Note - detecting container formats by mime magic is very very
     *  iffy, as we can't be sure where things will end up.
     * People really ought to use the container aware detection...
     */
    public void testIWorkDetection() throws Exception {
        // By name is easy
       assertTypeByName("application/vnd.apple.keynote", "testKeynote.key");
       assertTypeByName("application/vnd.apple.numbers", "testNumbers.numbers");
       assertTypeByName("application/vnd.apple.pages", "testPages.pages");
       
       // We can't do it by data, as we'd need to unpack
       //  the zip file to check the XML 
       assertTypeByData("application/zip", "testKeynote.key");
       
       assertTypeByNameAndData("application/vnd.apple.keynote", "testKeynote.key");
       assertTypeByNameAndData("application/vnd.apple.numbers", "testNumbers.numbers");
       assertTypeByNameAndData("application/vnd.apple.pages", "testPages.pages");
    }

    public void testJpegDetection() throws Exception {
        assertType("image/jpeg", "testJPEG.jpg");
        assertTypeByData("image/jpeg", "testJPEG.jpg");
        assertTypeByName("image/jpeg", "x.jpg");
        assertTypeByName("image/jpeg", "x.JPG");
        assertTypeByName("image/jpeg", "x.jpeg");
        assertTypeByName("image/jpeg", "x.JPEG");
        assertTypeByName("image/jpeg", "x.jpe");
        assertTypeByName("image/jpeg", "x.jif");
        assertTypeByName("image/jpeg", "x.jfif");
        assertTypeByName("image/jpeg", "x.jfi");
    }

    public void testTiffDetection() throws Exception {
        assertType("image/tiff", "testTIFF.tif");
        assertTypeByData("image/tiff", "testTIFF.tif");
        assertTypeByName("image/tiff", "x.tiff");
        assertTypeByName("image/tiff", "x.tif");
        assertTypeByName("image/tiff", "x.TIF");
    }

    public void testGifDetection() throws Exception {
        assertType("image/gif", "testGIF.gif");
        assertTypeByData("image/gif", "testGIF.gif");
        assertTypeByName("image/gif", "x.gif");
        assertTypeByName("image/gif", "x.GIF");
    }

    public void testPngDetection() throws Exception {
        assertType("image/png", "testPNG.png");
        assertTypeByData("image/png", "testPNG.png");
        assertTypeByName("image/png", "x.png");
        assertTypeByName("image/png", "x.PNG");
    }

    public void testBmpDetection() throws Exception {
        assertType("image/x-ms-bmp", "testBMP.bmp");
        assertTypeByData("image/x-ms-bmp", "testBMP.bmp");
        assertTypeByName("image/x-ms-bmp", "x.bmp");
        assertTypeByName("image/x-ms-bmp", "x.BMP");
        assertTypeByName("image/x-ms-bmp", "x.dib");
        assertTypeByName("image/x-ms-bmp", "x.DIB");
        //false positive check -- contains part of BMP signature
        assertType("text/plain", "testBMPfp.txt");
    }

    public void testPnmDetection() throws Exception {
        assertType("image/x-portable-bitmap", "testPBM.pbm");
        assertType("image/x-portable-graymap", "testPGM.pgm");
        assertType("image/x-portable-pixmap", "testPPM.ppm");
        assertTypeByData("image/x-portable-bitmap", "testPBM.pbm");
        assertTypeByData("image/x-portable-graymap", "testPGM.pgm");
        assertTypeByData("image/x-portable-pixmap", "testPPM.ppm");
        assertTypeByName("image/x-portable-anymap", "x.pnm");
        assertTypeByName("image/x-portable-anymap", "x.PNM");
        assertTypeByName("image/x-portable-bitmap", "x.pbm");
        assertTypeByName("image/x-portable-bitmap", "x.PBM");
        assertTypeByName("image/x-portable-graymap", "x.pgm");
        assertTypeByName("image/x-portable-graymap", "x.PGM");
        assertTypeByName("image/x-portable-pixmap", "x.ppm");
        assertTypeByName("image/x-portable-pixmap", "x.PPM");
    }

    public void testCgmDetection() throws Exception {
        // TODO: Need a test image file
        assertTypeByName("image/cgm", "x.cgm");
        assertTypeByName("image/cgm", "x.CGM");
    }

    public void testRdfXmlDetection() throws Exception {
        assertTypeByName("application/rdf+xml", "x.rdf");
        assertTypeByName("application/rdf+xml", "x.owl");
    }

    public void testSvgDetection() throws Exception {
        assertType("image/svg+xml", "testSVG.svg");
        assertTypeByData("image/svg+xml", "testSVG.svg");
        assertTypeByName("image/svg+xml", "x.svg");
        assertTypeByName("image/svg+xml", "x.SVG");

        // Should *.svgz be svg or gzip
        assertType("application/x-gzip", "testSVG.svgz");
        assertTypeByData("application/x-gzip", "testSVG.svgz");
        assertTypeByName("image/svg+xml", "x.svgz");
        assertTypeByName("image/svg+xml", "x.SVGZ");
    }

    public void testPdfDetection() throws Exception {
        assertType("application/pdf", "testPDF.pdf");
        assertTypeByData("application/pdf", "testPDF.pdf");
        assertTypeByName("application/pdf", "x.pdf");
        assertTypeByName("application/pdf", "x.PDF");
    }

    public void testSwfDetection() throws Exception {
        // TODO: Need a test flash file
        assertTypeByName("application/x-shockwave-flash", "x.swf");
        assertTypeByName("application/x-shockwave-flash", "x.SWF");
    }

    public void testDwgDetection() throws Exception {
        assertTypeByName("image/vnd.dwg", "x.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2004.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2007.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2010.dwg");
    }

    public void testWmfDetection() throws Exception {
        // TODO: Need a test wmf file
        assertTypeByName("application/x-msmetafile", "x.wmf");
        assertTypeByName("application/x-msmetafile", "x.WMF");
        // TODO: Need a test emf file
        assertTypeByName("application/x-msmetafile", "x.emf");
        assertTypeByName("application/x-msmetafile", "x.EMF");
        // TODO: Need a test wmz file
        assertTypeByName("application/x-ms-wmz", "x.wmz");
        assertTypeByName("application/x-ms-wmz", "x.WMZ");
        // TODO: Need a test emf file
        assertTypeByName("application/x-gzip", "x.emz");
        assertTypeByName("application/x-gzip", "x.EMZ");
    }

    public void testPsDetection() throws Exception {
        // TODO: Need a test postscript file
        assertTypeByName("application/postscript", "x.ps");
        assertTypeByName("application/postscript", "x.PS");
        assertTypeByName("application/postscript", "x.eps");
        assertTypeByName("application/postscript", "x.epsf");
        assertTypeByName("application/postscript", "x.epsi");
    }

    /**
     * @since TIKA-194
     */
    public void testJavaRegex() throws Exception{
        MimeType testType = new MimeType(MediaType.parse("foo/bar"));
        this.repo.add(testType);
        assertNotNull(repo.forName("foo/bar"));
        String pattern = "rtg_sst_grb_0\\.5\\.\\d{8}";
        this.repo.addPattern(testType, pattern, true);
        String testFileName = "rtg_sst_grb_0.5.12345678";
        assertNotNull(this.repo.getMimeType(testFileName));
        assertEquals(this.repo.getMimeType(testFileName).getName(), "foo/bar");

        MimeType testType2 = new MimeType(MediaType.parse("foo/bar2"));
        this.repo.add(testType2);
        assertNotNull(repo.forName("foo/bar2"));
        this.repo.addPattern(testType2, pattern, false);
        assertNotNull(this.repo.getMimeType(testFileName));
        assertNotSame("foo/bar2", this.repo.getMimeType(testFileName).getName());
    }
    
    public void testRawDetection() throws Exception {
        assertTypeByName("image/x-raw-adobe", "x.dng");
        assertTypeByName("image/x-raw-adobe", "x.DNG");
        assertTypeByName("image/x-raw-hasselblad", "x.3fr");
        assertTypeByName("image/x-raw-fuji", "x.raf");
        assertTypeByName("image/x-raw-canon", "x.crw");
        assertTypeByName("image/x-raw-canon", "x.cr2");
        assertTypeByName("image/x-raw-kodak", "x.k25");
        assertTypeByName("image/x-raw-kodak", "x.kdc");
        assertTypeByName("image/x-raw-kodak", "x.dcs");
        assertTypeByName("image/x-raw-kodak", "x.drf");
        assertTypeByName("image/x-raw-minolta", "x.mrw");
        assertTypeByName("image/x-raw-nikon", "x.nef");
        assertTypeByName("image/x-raw-nikon", "x.nrw");
        assertTypeByName("image/x-raw-olympus", "x.orf");
        assertTypeByName("image/x-raw-pentax", "x.ptx");
        assertTypeByName("image/x-raw-pentax", "x.pef");
        assertTypeByName("image/x-raw-sony", "x.arw");
        assertTypeByName("image/x-raw-sony", "x.srf");
        assertTypeByName("image/x-raw-sony", "x.sr2");
        assertTypeByName("image/x-raw-sigma", "x.x3f");
        assertTypeByName("image/x-raw-epson", "x.erf");
        assertTypeByName("image/x-raw-mamiya", "x.mef");
        assertTypeByName("image/x-raw-leaf", "x.mos");
        assertTypeByName("image/x-raw-panasonic", "x.raw");
        assertTypeByName("image/x-raw-panasonic", "x.rw2");
        assertTypeByName("image/x-raw-phaseone", "x.cap");
        assertTypeByName("image/x-raw-phaseone", "x.iiq");
        assertTypeByName("image/x-raw-phaseone", "x.cap");
        assertTypeByName("image/x-raw-red", "x.r3d");
        assertTypeByName("image/x-raw-imacon", "x.fff");
        assertTypeByName("image/x-raw-logitech", "x.pxn");
        assertTypeByName("image/x-raw-casio", "x.bay");
        assertTypeByName("image/x-raw-rawzor", "x.rwz");
    }

    /**
     * Tests MimeTypes.getMimeType(URL), which examines both the byte header
     * and, if necessary, the URL's extension.
     */
    public void testMimeDeterminationForTestDocuments() throws Exception {
        assertType("text/html", "testHTML.html");
        assertType("application/zip", "test-documents.zip");
        // TODO: Currently returns generic MS Office type based on
        // the magic header. The getMimeType method should understand
        // MS Office types better.
        // assertEquals("application/vnd.ms-excel",
        // getMimeType("testEXCEL.xls"));
        // assertEquals("application/vnd.ms-powerpoint",
        // getMimeType("testPPT.ppt"));
        // assertEquals("application/msword", getMimeType("testWORD.doc"));
        assertType("text/html", "testHTML_utf8.html");
        assertType(
                "application/vnd.oasis.opendocument.text",
                "testOpenOffice2.odt");
        assertType("application/pdf", "testPDF.pdf");
        assertType("application/rtf", "testRTF.rtf");
        assertType("text/plain", "testTXT.txt");
        assertType("application/xml", "testXML.xml");
        assertType("audio/basic", "testAU.au");
        assertType("audio/x-aiff", "testAIFF.aif");
        assertType("audio/x-wav", "testWAV.wav");
        assertType("audio/midi", "testMID.mid");
        assertType("application/x-msaccess", "testACCESS.mdb");
        assertType("application/x-font-ttf", "testTrueType.ttf");
    }

    private void assertType(String expected, String filename) throws Exception {
        InputStream stream = TestMimeTypes.class.getResourceAsStream(
                "/test-documents/" + filename);
        try {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            assertEquals(expected, repo.detect(stream, metadata).toString());
        } finally {
            stream.close();
        }
    }

    private void assertTypeByName(String expected, String filename)
            throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
        assertEquals(expected, repo.detect(null, metadata).toString());
    }

    private void assertTypeByData(String expected, String filename)
            throws IOException {
        InputStream stream = TestMimeTypes.class.getResourceAsStream(
                "/test-documents/" + filename);
        try {
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
        } finally {
            stream.close();
        }
    }
    
    private void assertTypeByNameAndData(String expected, String filename)
	    throws IOException {
	InputStream stream = TestMimeTypes.class.getResourceAsStream(
	        "/test-documents/" + filename);
	try {
	    Metadata metadata = new Metadata();
	    metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
	    assertEquals(expected, repo.detect(stream, metadata).toString());
	} finally {
	    stream.close();
	}
}

}
