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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

/**
 * 
 * Test Suite for the {@link MimeTypes} repository.
 * 
 */
public class TestMimeTypes extends TestCase {

    private Tika tika;

    private MimeTypes repo;

    private URL u;

    private static final File f = new File("/a/b/c/x.pdf");

    protected void setUp() throws Exception{
        TikaConfig config = TikaConfig.getDefaultConfig();
        repo = config.getMimeRepository();
        tika = new Tika(config);
        u = new URL("http://mydomain.com/x.pdf?x=y");
    }

    public void testCaseSensitivity() {
        String type = tika.detect("test.PDF");
        assertNotNull(type);
        assertEquals(type, tika.detect("test.pdf"));
        assertEquals(type, tika.detect("test.PdF"));
        assertEquals(type, tika.detect("test.pdF"));
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
        assertEquals("application/pdf", tika.detect(u.toExternalForm()));
        assertEquals("application/pdf", tika.detect(f.getPath()));
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
    }

    /**
     * Note - detecting container formats by mime magic is very very
     *  iffy, as we can't be sure where things will end up.
     * People really ought to use the container aware detection...
     */
    public void testOLE2Detection() throws Exception {
        // These have the properties block near the start, so our mime
        //  magic will spot them
        assertTypeByData("application/vnd.ms-excel", "testEXCEL.xls");
        
        // This one quite legitimately doesn't have its properties block
        //  as one of the first couple of entries
        // As such, our mime magic can't figure it out...
        assertTypeByData("application/x-tika-msoffice", "testWORD.doc");
        assertTypeByData("application/x-tika-msoffice", "testPPT.ppt");
        
        
        // By name + data:
        
        // Those we got right to start with are fine
        assertTypeByNameAndData("application/vnd.ms-excel","testEXCEL.xls");
        
        // And the name lets us specialise the generic OOXML
        //  ones to their actual type
        assertTypeByNameAndData("application/vnd.ms-powerpoint", "testPPT.ppt");
        assertTypeByNameAndData("application/msword", "testWORD.doc");
    }
    
    /**
     * Files generated by Works 7.0 Spreadsheet application use the OLE2
     * structure and resemble Excel files (they contain a "Workbook"). They are
     * not Excel though. They are distinguished from Excel files with an
     * additional top-level entry in below the root of the POI filesystem.
     * 
     * @throws Exception
     */
    public void testWorksSpreadsheetDetection() throws Exception {
        assertTypeDetection("testWORKSSpreadsheet7.0.xlr",
                // with name-only, everything should be all right 
                "application/x-tika-msworks-spreadsheet",
                // this is possible due to MimeTypes guessing the type
                // based on the WksSSWorkBook near the beginning of the
                // file
                "application/x-tika-msworks-spreadsheet",
                // this is right, the magic-based detection works, there is
                // no need for the name-based detection to refine it
                "application/x-tika-msworks-spreadsheet");
    }
    
    public void testStarOfficeDetection() throws Exception {
        assertTypeDetection("testVORCalcTemplate.vor",
                "application/x-staroffice-template",
                "application/vnd.stardivision.calc",
                "application/vnd.stardivision.calc");
        assertTypeDetection("testVORDrawTemplate.vor",
                "application/x-staroffice-template",
                "application/vnd.stardivision.draw",
                "application/vnd.stardivision.draw");
        assertTypeDetection("testVORImpressTemplate.vor",
                "application/x-staroffice-template",
                "application/vnd.stardivision.impress",
                "application/vnd.stardivision.impress");
        assertTypeDetection("testVORWriterTemplate.vor",
                "application/x-staroffice-template",
                "application/vnd.stardivision.writer",
                "application/vnd.stardivision.writer");
        
        assertTypeDetection("testStarOffice-5.2-calc.sdc",
                "application/vnd.stardivision.calc",
                "application/vnd.stardivision.calc",
                "application/vnd.stardivision.calc");
        assertTypeDetection("testStarOffice-5.2-draw.sda",
                "application/vnd.stardivision.draw",
                "application/vnd.stardivision.draw",
                "application/vnd.stardivision.draw");
        assertTypeDetection("testStarOffice-5.2-impress.sdd",
                "application/vnd.stardivision.impress",
                "application/vnd.stardivision.impress",
                "application/vnd.stardivision.impress");
        assertTypeDetection("testStarOffice-5.2-writer.sdw",
                "application/vnd.stardivision.writer",
                "application/vnd.stardivision.writer",
                "application/vnd.stardivision.writer");
    }
    
    /**
     * Files generated by Works Word Processor versions 3.0 and 4.0 use the
     * OLE2 structure. They don't resemble Word though.
     * 
     * @throws Exception
     */
    public void testOldWorksWordProcessorDetection() throws Exception {
        assertTypeDetection(
                "testWORKSWordProcessor3.0.wps",
                // .wps is just like any other works extension
                "application/vnd.ms-works",
                // this is due to MatOST substring
                "application/vnd.ms-works",
                // magic-based detection works, no need to refine it
                "application/vnd.ms-works");
        
        // files in version 4.0 are no different from those in version 3.0
        assertTypeDetection(
                "testWORKSWordProcessor4.0.wps",
                "application/vnd.ms-works",
                "application/vnd.ms-works",
                "application/vnd.ms-works");
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
        
        // If we give the filename as well as the data, we can
        //  specialise the ooxml generic one to the correct type
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "testEXCEL.xlsx");
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.presentationml.presentation", "testPPT.pptx");
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "testWORD.docx");
        
        // Test a few of the less usual ones
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
    
    public void testArchiveDetection() throws Exception {
       assertTypeByName("application/x-archive", "test.ar");
       assertTypeByName("application/zip",    "test.zip");
       assertTypeByName("application/x-tar",  "test.tar");
       assertTypeByName("application/x-gzip", "test.tgz"); // See GZIP, not tar contents of it
       assertTypeByName("application/x-cpio", "test.cpio");
       
       // TODO Add an example .deb and .udeb, then check these
       
       // Check the mime magic patterns for them work too
       assertTypeByData("application/x-archive", "testARofText.ar");
       assertTypeByData("application/x-archive", "testARofSND.ar"); 
       assertTypeByData("application/zip",    "test-documents.zip");
       assertTypeByData("application/x-gtar",  "test-documents.tar"); // GNU TAR
       assertTypeByData("application/x-gzip", "test-documents.tgz"); // See GZIP, not tar contents of it
       assertTypeByData("application/x-cpio", "test-documents.cpio");
    }
    
    public void testFitsDetection() throws Exception {
        // FITS image created using imagemagick convert of testJPEG.jpg
        assertType("application/fits", "testFITS.fits");
        assertTypeByData("application/fits", "testFITS.fits");
        assertTypeByName("application/fits", "testFITS.fits");
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

    public void testprtDetection() throws Exception {
       assertTypeByName("application/x-prt", "x.prt");
       assertTypeByData("application/x-prt", "testCADKEY.prt");
   }
    
    /**
     * Formats which are based on plain text
     */
    public void testTextBasedFormatsDetection() throws Exception {
       assertTypeByName("text/plain", "testTXT.txt");
       assertType(      "text/plain", "testTXT.txt");
       
       assertTypeByName("text/css", "testCSS.css");
       assertType(      "text/css", "testCSS.css");
       
       assertTypeByName("text/html", "testHTML.html");
       assertType(      "text/html", "testHTML.html");
       
       assertTypeByName("application/javascript", "testJS.js");
       assertType(      "application/javascript", "testJS.js");
    }

    public void testWmfDetection() throws Exception {
        assertTypeByName("application/x-msmetafile", "x.wmf");
        assertTypeByData("application/x-msmetafile", "testWMF.wmf");
        assertTypeByName("application/x-msmetafile", "x.WMF");

        assertTypeByName("application/x-emf", "x.emf");
        assertTypeByData("application/x-emf","testEMF.emf");
        assertTypeByName("application/x-emf", "x.EMF");
        // TODO: Need a test wmz file
        assertTypeByName("application/x-ms-wmz", "x.wmz");
        assertTypeByName("application/x-ms-wmz", "x.WMZ");
        // TODO: Need a test emz file
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
    
    public void testMicrosoftMultiMediaDetection() throws Exception {
       assertTypeByName("video/x-ms-asf", "x.asf");
       assertTypeByName("video/x-ms-wmv", "x.wmv");
       assertTypeByName("audio/x-ms-wma", "x.wma");
       
       assertTypeByData("video/x-ms-asf", "testASF.asf");
       assertTypeByData("video/x-ms-wmv", "testWMV.wmv");
       assertTypeByData("audio/x-ms-wma", "testWMA.wma");
    }
    
    /**
     * All 3 DITA types are in theory handled by the same mimetype,
     *  but we specialise them 
     */
    public void testDITADetection() throws Exception {
       assertTypeByName("application/dita+xml; format=topic", "test.dita");
       assertTypeByName("application/dita+xml; format=map", "test.ditamap");
       assertTypeByName("application/dita+xml; format=val", "test.ditaval");
       
       assertTypeByData("application/dita+xml; format=task", "testDITA.dita");
       assertTypeByData("application/dita+xml; format=concept", "testDITA2.dita");
       assertTypeByData("application/dita+xml; format=map", "testDITA.ditamap");
       
       assertTypeByNameAndData("application/dita+xml; format=task", "testDITA.dita");
       assertTypeByNameAndData("application/dita+xml; format=concept", "testDITA2.dita");
       assertTypeByNameAndData("application/dita+xml; format=map", "testDITA.ditamap");
       
       // These are all children of the official type
       assertEquals("application/dita+xml", 
             repo.getMediaTypeRegistry().getSupertype(getTypeByNameAndData("testDITA.ditamap")).toString());
       assertEquals("application/dita+xml", 
             repo.getMediaTypeRegistry().getSupertype(getTypeByNameAndData("testDITA.dita")).toString());
       assertEquals("application/dita+xml", 
             repo.getMediaTypeRegistry().getSupertype(getTypeByNameAndData("testDITA2.dita")).toString());
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
        assertEquals("foo/bar", tika.detect(testFileName));

        MimeType testType2 = new MimeType(MediaType.parse("foo/bar2"));
        this.repo.add(testType2);
        assertNotNull(repo.forName("foo/bar2"));
        this.repo.addPattern(testType2, pattern, false);
        assertNotSame("foo/bar2", tika.detect(testFileName));
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
        assertTypeByName("image/x-raw-phaseone", "x.iiq");
        assertTypeByName("image/x-raw-red", "x.r3d");
        assertTypeByName("image/x-raw-imacon", "x.fff");
        assertTypeByName("image/x-raw-logitech", "x.pxn");
        assertTypeByName("image/x-raw-casio", "x.bay");
        assertTypeByName("image/x-raw-rawzor", "x.rwz");
    }
    
    /**
     * Tests that we correctly detect the font types
     */
    public void testFontDetection() throws Exception {
       assertTypeByName("application/x-font-adobe-metric", "x.afm");
       assertTypeByData("application/x-font-adobe-metric", "testAFM.afm");
       
       assertTypeByName("application/x-font-printer-metric", "x.pfm");
       // TODO Get a sample .pfm file
       assertTypeByData(
             "application/x-font-printer-metric", 
             new byte[] {0x00, 0x01, 256-0xb1, 0x0a, 0x00, 0x00, 0x43, 0x6f,  
                         0x70, 0x79, 0x72, 0x69, 0x67, 0x68, 0x74, 0x20}
       );
       
       assertTypeByName("application/x-font-type1", "x.pfa");
       // TODO Get a sample .pfa file
       assertTypeByData(
             "application/x-font-type1", 
             new byte[] {0x25, 0x21, 0x50, 0x53, 0x2d, 0x41, 0x64, 0x6f,
                         0x62, 0x65, 0x46, 0x6f, 0x6e, 0x74, 0x2d, 0x31,
                         0x2e, 0x30, 0x20, 0x20, 0x2d, 0x2a, 0x2d, 0x20}
       );
       
       assertTypeByName("application/x-font-type1", "x.pfb");
       // TODO Get a sample .pfm file
       assertTypeByData(
             "application/x-font-type1", 
             new byte[] {-0x80, 0x01, 0x09, 0x05, 0x00, 0x00, 0x25, 0x21,
                          0x50, 0x53, 0x2d, 0x41, 0x64, 0x6f, 0x62, 0x65,
                          0x46, 0x6f, 0x6e, 0x74, 0x2d, 0x31, 0x2e, 0x30 }
       );
    }

    /**
     * Tests MimeTypes.getMimeType(URL), which examines both the byte header
     * and, if necessary, the URL's extension.
     */
    public void testMimeDeterminationForTestDocuments() throws Exception {
        assertType("text/html", "testHTML.html");
        assertType("application/zip", "test-documents.zip");

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
    
    public void test7ZipDetection() throws Exception {
       assertTypeByName("application/x-7z-compressed","test-documents.7z");
       assertTypeByData("application/x-7z-compressed","test-documents.7z");
       assertTypeByNameAndData("application/x-7z-compressed", "test-documents.7z");
   }

    public void testWebArchiveDetection() throws Exception {
        assertTypeByName("application/x-webarchive","x.webarchive");
        assertTypeByData("application/x-bplist","testWEBARCHIVE.webarchive");
        assertTypeByNameAndData("application/x-webarchive", "testWEBARCHIVE.webarchive");
    }

    /**
     * KML, and KMZ (zipped KML)
     */
    public void testKMLZDetection() throws Exception {
       assertTypeByName("application/vnd.google-earth.kml+xml","testKML.kml");
       assertTypeByData("application/vnd.google-earth.kml+xml","testKML.kml");
       assertTypeByNameAndData("application/vnd.google-earth.kml+xml", "testKML.kml");
       
       assertTypeByName("application/vnd.google-earth.kmz","testKMZ.kmz");
       assertTypeByNameAndData("application/vnd.google-earth.kmz", "testKMZ.kmz");
       
       // By data only, mimetype magic only gets us to a .zip
       // We need to use the Zip Aware detector to get the full type
       assertTypeByData("application/zip","testKMZ.kmz");
   }

    public void testEmlx() throws IOException {
        assertTypeDetection("testEMLX.emlx", "message/x-emlx");
    }

    /** Test getMimeType(byte[]) */
    public void testGetMimeType_byteArray() throws IOException {
        // Plain text detection
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
        assertText(new byte[] { 'a', 'b', 'c' });
        assertText(new byte[] { '\t', '\r', '\n', 0x0C, 0x1B });
        assertNotText(new byte[] { '\t', '\r', '\n', 0x0E, 0x1C });
    }

    private void assertText(byte[] prefix) throws IOException {
        assertMagic("text/plain", prefix);
    }

    private void assertNotText(byte[] prefix) throws IOException {
        assertMagic("application/octet-stream", prefix);
    }

    private void assertMagic(String expected, byte[] prefix) throws IOException {
        MediaType type =
                repo.detect(new ByteArrayInputStream(prefix), new Metadata());
        assertNotNull(type);
        assertEquals(expected, type.toString());
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
        assertNotNull("Test file not found: " + filename, stream);
        try {
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
        } finally {
            stream.close();
        }
    }
    
    private void assertTypeByData(String expected, byte[] data)
            throws IOException {
       InputStream stream = new ByteArrayInputStream(data);
       try {
          Metadata metadata = new Metadata();
          assertEquals(expected, repo.detect(stream, metadata).toString());
       } finally {
          stream.close();
       }
    }

    private void assertTypeDetection(String filename, String type)
            throws IOException {
        assertTypeDetection(filename, type, type, type);
    }

    private void assertTypeDetection(String filename, String byName, String byData, 
            String byNameAndData) throws IOException {
        assertTypeByName(byName, filename);
        assertTypeByData(byData, filename);
        assertTypeByNameAndData(byNameAndData, filename);
    }

    private void assertTypeByNameAndData(String expected, String filename)
        throws IOException {
       assertEquals(expected, getTypeByNameAndData(filename).toString());
    }

    private MediaType getTypeByNameAndData(String filename) throws IOException {
       InputStream stream = TestMimeTypes.class.getResourceAsStream(
             "/test-documents/" + filename);
       assertNotNull("Test document not found: " + filename, stream);
       try {
          Metadata metadata = new Metadata();
          metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
          return repo.detect(stream, metadata);
       } finally {
          stream.close();
       }
    }
}
