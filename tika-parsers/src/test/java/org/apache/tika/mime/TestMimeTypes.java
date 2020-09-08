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

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

/**
 * 
 * Test Suite for the {@link MimeTypes} repository.
 * 
 */
public class TestMimeTypes {

    private Tika tika;

    private MimeTypes repo;

    private URL u;

    private static final File f = new File("/a/b/c/x.pdf");

    @Before
    public void setUp() throws Exception{
        TikaConfig config = TikaConfig.getDefaultConfig();
        repo = config.getMimeRepository();
        tika = new Tika(config);
        u = new URL("http://mydomain.com/x.pdf?x=y");
    }

    @Test
    public void testCaseSensitivity() {
        String type = tika.detect("test.PDF");
        assertNotNull(type);
        assertEquals(type, tika.detect("test.pdf"));
        assertEquals(type, tika.detect("test.PdF"));
        assertEquals(type, tika.detect("test.pdF"));
    }

    @Test
    public void testNetCDF() throws Exception {
        assertTypeByData("application/x-netcdf", "sresa1b_ncar_ccsm3_0_run1_200001.nc");
    }

    @Test
    public void testLoadMimeTypes() throws MimeTypeException {
        assertNotNull(repo.forName("application/octet-stream"));
        assertNotNull(repo.forName("text/x-tex"));
    }

    /**
     * Tests MIME type determination based solely on the URL's extension.
     */
    @Test
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
    @Test
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
    @Test
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

    @Test
    public void testDigiliteFDF() throws Exception {
        //not to be confused with Adobe's .fdf!
        assertTypeByData("application/vnd.digilite.prolights",
                "testDigilite.fdf");
        assertTypeByNameAndData("application/vnd.digilite.prolights",
                "testDigilite.fdf");
    }

    @Test
    public void testXDP() throws Exception {
        assertTypeDetection("testXDP.xdp",
                "application/vnd.adobe.xdp+xml");
    }

    @Test
    public void testXFDF() throws Exception {
        //for an example: https://docs.appligent.com/fdfmerge/fdfmerge-form-data-format/
        assertTypeDetection("testXFDF.xfdf",
                "application/vnd.adobe.xfdf");
    }

    @Test
    public void testHFATypes() throws Exception {
        assertTypeByData("application/x-erdas-hfa","testHFA.hfa");
    }

    @Test
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
    @Test
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
     * Files from Excel 2 through 4 are based on the BIFF record
     *  structure, but without a wrapping OLE2 structure.
     * Excel 5 and Excel 95+ work on OLE2
     */
    @Test
    public void testOldExcel() throws Exception {
        // With just a name, we'll think everything's a new Excel file
        assertTypeByName("application/vnd.ms-excel","testEXCEL_4.xls");
        assertTypeByName("application/vnd.ms-excel","testEXCEL_5.xls");
        assertTypeByName("application/vnd.ms-excel","testEXCEL_95.xls");
        
        // With data, we can work out if it's old or new style
        assertTypeByData("application/vnd.ms-excel.sheet.4","testEXCEL_4.xls");
        assertTypeByData("application/x-tika-msoffice","testEXCEL_5.xls");
        assertTypeByData("application/x-tika-msoffice","testEXCEL_95.xls");
        
        assertTypeByNameAndData("application/vnd.ms-excel.sheet.4","testEXCEL_4.xls");
        assertTypeByNameAndData("application/vnd.ms-excel","testEXCEL_5.xls");
        assertTypeByNameAndData("application/vnd.ms-excel","testEXCEL_95.xls");
    }
    
    /**
     * Note - detecting container formats by mime magic is very very
     *  iffy, as we can't be sure where things will end up.
     * People really ought to use the container aware detection...
     */
    @Test
    public void testOoxmlDetection() throws Exception {
        // These two do luckily have [Content_Types].xml near the start,
        //  so our mime magic will spot them
        assertTypeByData("application/x-tika-ooxml", "testEXCEL.xlsx");
        assertTypeByData("application/x-tika-ooxml", "testPPT.pptx");
        
        // This one quite legitimately doesn't have its [Content_Types].xml
        //  file as one of the first couple of entries
        // As such, our mime magic can't figure it out...
        assertTypeByData("application/zip", "testWORD.docx");
        
        // POI-generated files have the rels first not Content Types
        assertTypeByData("application/x-tika-ooxml", "testEXCEL_poi.xlsx");
        
        // If we give the filename as well as the data, we can
        //  specialise the ooxml generic one to the correct type
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "testEXCEL.xlsx");
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.presentationml.presentation", "testPPT.pptx");
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "testWORD.docx");
        
        assertTypeByNameAndData("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "testEXCEL_poi.xlsx");
        
        // Test a few of the less usual ones
        assertTypeByNameAndData("application/vnd.ms-excel.sheet.binary.macroenabled.12","testEXCEL.xlsb");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.presentation.macroenabled.12", "testPPT.pptm");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.template.macroenabled.12", "testPPT.potm");
        assertTypeByNameAndData("application/vnd.ms-powerpoint.slideshow.macroenabled.12", "testPPT.ppsm");
    }
    
    /**
     * Note - container based formats, needs container detection
     *  to be properly correct
     */
    @Test
    public void testVisioDetection() throws Exception {
        // By Name, should get it right
        assertTypeByName("application/vnd.visio", "testVISIO.vsd");
        assertTypeByName("application/vnd.ms-visio.drawing.macroenabled.12", "testVISIO.vsdm");
        assertTypeByName("application/vnd.ms-visio.drawing", "testVISIO.vsdx");
        assertTypeByName("application/vnd.ms-visio.stencil.macroenabled.12", "testVISIO.vssm");
        assertTypeByName("application/vnd.ms-visio.stencil", "testVISIO.vssx");
        assertTypeByName("application/vnd.ms-visio.template.macroenabled.12", "testVISIO.vstm");
        assertTypeByName("application/vnd.ms-visio.template", "testVISIO.vstx");
        
        // By Name and Data, should get it right
        assertTypeByNameAndData("application/vnd.visio", "testVISIO.vsd");
        assertTypeByNameAndData("application/vnd.ms-visio.drawing.macroenabled.12", "testVISIO.vsdm");
        assertTypeByNameAndData("application/vnd.ms-visio.drawing", "testVISIO.vsdx");
        assertTypeByNameAndData("application/vnd.ms-visio.stencil.macroenabled.12", "testVISIO.vssm");
        assertTypeByNameAndData("application/vnd.ms-visio.stencil", "testVISIO.vssx");
        assertTypeByNameAndData("application/vnd.ms-visio.template.macroenabled.12", "testVISIO.vstm");
        assertTypeByNameAndData("application/vnd.ms-visio.template", "testVISIO.vstx");
        
        // By Data only, will get the container parent
        assertTypeByData("application/x-tika-msoffice", "testVISIO.vsd");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vsdm");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vsdx");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vssm");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vssx");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vstm");
        assertTypeByData("application/x-tika-ooxml", "testVISIO.vstx");
    }

    /**
     * Note - detecting container formats by mime magic is very very
     *  iffy, as we can't be sure where things will end up.
     * People really ought to use the container aware detection...
     */
    @Test
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
    
    @Test
    public void testArchiveDetection() throws Exception {
       assertTypeByName("application/x-archive", "test.ar");
       assertTypeByName("application/zip",    "test.zip");
       assertTypeByName("application/x-tar",  "test.tar");
       assertTypeByName("application/gzip", "test.tgz"); // See GZIP, not tar contents of it
       assertTypeByName("application/x-cpio", "test.cpio");
       assertTypeByName("application/vnd.ms-cab-compressed", "test.cab");
       
       // TODO Add an example .deb and .udeb, then check these
       
       // Check the mime magic patterns for them work too
       assertTypeByData("application/x-archive", "testARofText.ar");
       assertTypeByData("application/x-archive", "testARofSND.ar"); 
       assertTypeByData("application/zip",    "test-documents.zip");
       assertTypeByData("application/x-gtar",  "test-documents.tar"); // GNU TAR
       assertTypeByData("application/gzip", "test-documents.tgz"); // See GZIP, not tar contents of it
       assertTypeByData("application/x-cpio", "test-documents.cpio");
       assertTypeByData("application/vnd.ms-cab-compressed", "test-documents.cab");
       
       // For spanned zip files, the .zip file doesn't have the header, it's the other parts
       assertTypeByData("application/octet-stream", "test-documents-spanned.zip");
       assertTypeByData("application/zip",          "test-documents-spanned.z01");

       assertTypeDetection("testZSTD.zstd", "application/zstd");
    }
    
    @Test
    public void testFeedsDetection() throws Exception {
        assertType("application/rss+xml",  "rsstest_091.rss");
        assertType("application/rss+xml",  "rsstest_20.rss");
        assertType("application/atom+xml", "testATOM.atom");
        assertTypeByData("application/rss+xml",  "rsstest_091.rss");
        assertTypeByName("application/rss+xml",  "rsstest_091.rss");
        assertTypeByData("application/rss+xml",  "rsstest_20.rss");
        assertTypeByName("application/rss+xml",  "rsstest_20.rss");
        assertTypeByData("application/atom+xml", "testATOM.atom");
        assertTypeByName("application/atom+xml", "testATOM.atom");
    }
    
    @Test
    public void testFitsDetection() throws Exception {
        // FITS image created using imagemagick convert of testJPEG.jpg
        assertType("application/fits", "testFITS.fits");
        assertTypeByData("application/fits", "testFITS.fits");
        assertTypeByName("application/fits", "testFITS.fits");
        // Shorter Header pattern (16 rather than 20 spaces)
        assertTypeByData("application/fits", "testFITS_ShorterHeader.fits");
    }

    @Test
    public void testAVIFDetection() throws Exception {
        // The test file is an avif header fragment only, not a complete image.
        assertType("image/avif", "testAVIF.avif");
        assertTypeByData("image/avif", "testAVIF.avif");
        assertTypeByName("image/avif", "testAVIF.avif");
    }

    @Test
    public void testHeifDetection() throws Exception {
        // HEIF image using the HEVC Codec == HEIC
        //  created using https://compare.rokka.io/_compare on testJPEG_GEO.jpg
        assertType("image/heic", "testHEIF.heic");
        assertTypeByData("image/heic", "testHEIF.heic");
        assertTypeByName("image/heic", "testHEIF.heic");

        // TODO Create a HEIF using another codec, to test .heif data
        assertTypeByName("image/heif", "testHEIF.heif");
    }

    @Test
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
        
        assertType("image/jp2", "testJPEG.jp2");
        assertTypeByData("image/jp2", "testJPEG.jp2");
        assertTypeByName("image/jp2", "x.jp2");
    }

    @Test
    public void testBpgDetection() throws Exception {
        assertType("image/x-bpg", "testBPG.bpg");
        assertTypeByData("image/x-bpg", "testBPG.bpg");
        assertTypeByData("image/x-bpg", "testBPG_commented.bpg");
        assertTypeByName("image/x-bpg", "x.bpg");
    }
    
    @Test
    public void testIcnsDetection() throws Exception {
        assertType("image/icns", "testICNS.icns");
        assertTypeByData("image/icns", "testICNS_basic.icns");
        assertTypeByData("image/icns", "testICNS.icns");
        assertTypeByName("image/icns", "testICNS.icns");
    }
	
    @Test
    public void testTiffDetection() throws Exception {
        assertType("image/tiff", "testTIFF.tif");
        assertTypeByData("image/tiff", "testTIFF.tif");
        assertTypeByName("image/tiff", "x.tiff");
        assertTypeByName("image/tiff", "x.tif");
        assertTypeByName("image/tiff", "x.TIF");
    }

    @Test
    public void testGifDetection() throws Exception {
        assertType("image/gif", "testGIF.gif");
        assertTypeByData("image/gif", "testGIF.gif");
        assertTypeByName("image/gif", "x.gif");
        assertTypeByName("image/gif", "x.GIF");
    }

    @Test
    public void testPngDetection() throws Exception {
        assertType("image/png", "testPNG.png");
        assertTypeByData("image/png", "testPNG.png");
        assertTypeByName("image/png", "x.png");
        assertTypeByName("image/png", "x.PNG");
    }

    @Test
    public void testWEBPDetection() throws Exception {
        assertType("image/webp", "testWEBP.webp");
        assertTypeByData("image/webp", "testWEBP.webp");
        assertTypeByName("image/webp", "x.webp");
        assertTypeByName("image/webp", "x.WEBP");
    }

    @Test
    public void testBmpDetection() throws Exception {
        assertType("image/bmp", "testBMP.bmp");
        assertTypeByData("image/bmp", "testBMP.bmp");
        assertTypeByName("image/bmp", "x.bmp");
        assertTypeByName("image/bmp", "x.BMP");
        assertTypeByName("image/bmp", "x.dib");
        assertTypeByName("image/bmp", "x.DIB");
        //false positive check -- contains part of BMP signature
        assertType("text/plain", "testBMPfp.txt");
    }

    @Test
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

    @Test
    public void testPictDetection() throws Exception {
        assertType("image/x-pict", "testPICT.pct");
        assertTypeByData("image/x-pict", "testPICT.pct");
        assertTypeByName("image/x-pict", "x.pic");
        assertTypeByName("image/x-pict", "x.PCT");
    }

    @Test
    public void testPcxDetection() throws Exception {
        // Single PCX file
        assertType("image/vnd.zbrush.pcx", "testPCX.pcx");
        assertTypeByData("image/vnd.zbrush.pcx", "testPCX.pcx");
        assertTypeByName("image/vnd.zbrush.pcx", "x.pcx");

        // Multi-Page PCX = DCX
        assertType("image/vnd.zbrush.dcx", "testDCX.dcx");
        assertTypeByData("image/vnd.zbrush.dcx", "testDCX.dcx");
        assertTypeByName("image/vnd.zbrush.dcx", "x.dcx");
    }

    @Test
    public void testCgmDetection() throws Exception {
        // TODO: Need a test image file
        assertTypeByName("image/cgm", "x.cgm");
        assertTypeByName("image/cgm", "x.CGM");
    }

    @Test
    public void testRdfXmlDetection() throws Exception {
        assertTypeByName("application/rdf+xml", "x.rdf");
        assertTypeByName("application/rdf+xml", "x.owl");
    }

    @Test
    public void testSvgDetection() throws Exception {
        assertType("image/svg+xml", "testSVG.svg");
        assertTypeByData("image/svg+xml", "testSVG.svg");
        assertTypeByName("image/svg+xml", "x.svg");
        assertTypeByName("image/svg+xml", "x.SVG");

        // Should *.svgz be svg or gzip
        assertType("application/gzip", "testSVG.svgz");
        assertTypeByData("application/gzip", "testSVG.svgz");
        assertTypeByName("image/svg+xml", "x.svgz");
        assertTypeByName("image/svg+xml", "x.SVGZ");
    }

    @Test
    public void testPdfDetection() throws Exception {
        // PDF extension by name is enough
        assertTypeByName("application/pdf", "x.pdf");
        assertTypeByName("application/pdf", "x.PDF");

        // For normal PDFs, can get by name or data or both
        assertType("application/pdf", "testPDF.pdf");
        assertTypeByData("application/pdf", "testPDF.pdf");

        // PDF with a BoM works both ways too
        assertType("application/pdf", "testPDF_bom.pdf");
        assertTypeByData("application/pdf", "testPDF_bom.pdf");
    }

    @Test
    public void testSwfDetection() throws Exception {
        assertTypeByName("application/x-shockwave-flash", "x.swf");
        assertTypeByName("application/x-shockwave-flash", "x.SWF");
        assertTypeByName("application/x-shockwave-flash", "test1.swf");
        assertTypeByName("application/x-shockwave-flash", "test2.swf");
        assertTypeByName("application/x-shockwave-flash", "test3.swf");
    }

    @Test
    public void testAutoCADDetection() throws Exception {
        assertTypeByName("image/vnd.dwg", "x.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2004.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2007.dwg");
        assertTypeByData("image/vnd.dwg", "testDWG2010.dwg");
        
        // From name, gets the common parent type
        assertTypeByName("model/vnd.dwf", "x.dwf");
        // With the data, can work out it's the v6 zip-based flavour
        assertTypeByData("model/vnd.dwf; version=6", "testDWF2010.dwf");
        
        // From name, gets the common parent type
        assertTypeByName("image/vnd.dxf", "x.dxf");
        // With the data, can work out it's the ASCII flavour
        assertTypeByData("image/vnd.dxf; format=ascii", "testDXF_ascii.dxf");
        // TODO Get a sample Binary DXF file and test
    }

    @Test
    public void testprtDetection() throws Exception {
       assertTypeByName("application/x-prt", "x.prt");
       assertTypeByData("application/x-prt", "testCADKEY.prt");
   }
    
    /**
     * Formats which are based on plain text
     */
    @Test
    public void testTextBasedFormatsDetection() throws Exception {
       assertTypeByName("text/plain", "testTXT.txt");
       assertType(      "text/plain", "testTXT.txt");
       
       assertTypeByName("text/css", "testCSS.css");
       assertType(      "text/css", "testCSS.css");
       
       assertTypeByName("text/csv", "testCSV.csv");
       assertType(      "text/csv", "testCSV.csv");
       
       assertTypeByName("text/html", "testHTML.html");
       assertType(      "text/html", "testHTML.html");
       
       assertTypeByName("application/javascript", "testJS.js");
       assertType(      "application/javascript", "testJS.js");

       assertType(      "text/vnd.graphviz", "testGRAPHVIZd.dot");
       assertType(      "text/vnd.graphviz", "testGRAPHVIZg.dot");
       assertType(      "text/vnd.graphviz", "testGRAPHVIZdc.dot");
       
       assertTypeByName("application/x-bat", "testBAT.bat");
       assertTypeByName("application/x-bat", "testCMD.cmd");
       assertType(      "application/x-bat", "testBAT.bat");
       assertType(      "application/x-bat", "testBATUpper.bat");
    }
    
    @Test
    public void testJavaDetection() throws Exception {
        // TODO Classloader doesn't seem to find the .class file in test-documents
        //assertTypeDetection("AutoDetectParser.class", "application/java-vm");
        
        // OSX Native Extension
        assertTypeDetection("testJNILIB.jnilib", "application/x-java-jnilib");
    }

    @Test
    public void testXmlAndHtmlDetection() throws Exception {
        assertTypeByData("application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><records><record/></records>"
                .getBytes(UTF_8));
        assertTypeByData("application/xml", "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16\"?><records><record/></records>"
                .getBytes(UTF_16LE));
        assertTypeByData("application/xml", "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16\"?><records><record/></records>"
                .getBytes(UTF_16BE));
        assertTypeByData("application/xml", "<!-- XML without processing instructions --><records><record/></records>"
                .getBytes(UTF_8));
        assertTypeByData("text/html", "<html><body>HTML</body></html>"
                .getBytes(UTF_8));
        assertTypeByData("text/html", "<!-- HTML comment --><html><body>HTML</body></html>"
                .getBytes(UTF_8));
    }

    @Test
    public void testWmfDetection() throws Exception {
        assertTypeByName("image/wmf", "x.wmf");
        assertTypeByData("image/wmf", "testWMF.wmf");
        assertTypeByName("image/wmf", "x.WMF");

        assertTypeByName("image/emf", "x.emf");
        assertTypeByData("image/emf", "testEMF.emf");
        assertTypeByName("image/emf", "x.EMF");

        // GZip Compressed WMF, get subtype by name only
        // TODO: Need a test wmz file
        assertTypeByName("application/x-ms-wmz", "x.wmz");
        assertTypeByName("application/x-ms-wmz", "x.WMZ");

        // GZip Compressed EMF, get subtype by name only
        // TODO: Need a test emz file
        assertTypeByName("image/x-emf-compressed", "x.emz");
        assertTypeByName("image/x-emf-compressed", "x.EMZ");
    }

    @Test
    public void testPsDetection() throws Exception {
        // TODO: Need a test postscript file
        assertTypeByName("application/postscript", "x.ps");
        assertTypeByName("application/postscript", "x.PS");
        assertTypeByName("application/postscript", "x.eps");
        assertTypeByName("application/postscript", "x.epsf");
        assertTypeByName("application/postscript", "x.epsi");
    }
    
    @Test
    public void testMicrosoftMultiMediaDetection() throws Exception {
       assertTypeByName("video/x-ms-asf", "x.asf");
       assertTypeByName("video/x-ms-wmv", "x.wmv");
       assertTypeByName("audio/x-ms-wma", "x.wma");
       
       assertTypeByData("video/x-ms-asf", "testASF.asf");
       assertTypeByData("video/x-ms-wmv", "testWMV.wmv");
       assertTypeByData("video/x-ms-wmv", "testWMV_WMV2.wmv");
       assertTypeByData("audio/x-ms-wma", "testWMA.wma");
    }
    
    /**
     * All 3 DITA types are in theory handled by the same mimetype,
     *  but we specialise them 
     */
    @Test
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
       // Concept inherits from topic
       assertEquals("application/dita+xml; format=topic", 
             repo.getMediaTypeRegistry().getSupertype(getTypeByNameAndData("testDITA2.dita")).toString());
    }

    /**
     * @since TIKA-194
     */
    @Test
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
    
    @Test
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
    @Test
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
    @Test
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
        assertType("audio/vnd.wave", "testWAV.wav");
        assertType("audio/midi", "testMID.mid");
        assertType("application/x-msaccess", "testACCESS.mdb");
        assertType("application/x-font-ttf", "testTrueType3.ttf");
    }
    
    @Test
    public void test7ZipDetection() throws Exception {
       assertTypeByName("application/x-7z-compressed","test-documents.7z");
       assertTypeByData("application/x-7z-compressed","test-documents.7z");
       assertTypeByNameAndData("application/x-7z-compressed", "test-documents.7z");
   }

    @Test
    public void testWebArchiveDetection() throws Exception {
        assertTypeByName("application/x-webarchive","x.webarchive");
        assertTypeByData("application/x-bplist","testWEBARCHIVE.webarchive");
        assertTypeByNameAndData("application/x-webarchive", "testWEBARCHIVE.webarchive");
    }

    /**
     * KML, and KMZ (zipped KML)
     */
    @Test
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

    @Test
    public void testCreativeSuite() throws IOException {
        assertTypeDetection("testINDD.indd", "application/x-adobe-indesign");
        assertTypeDetection("testPSD.psd", "image/vnd.adobe.photoshop");
    }
    
    @Test
    public void testAMR() throws IOException {
        // AMR matches on name, data or both
        assertTypeDetection("testAMR.amr", "audio/amr");
        
        // AMR-WB subtype shares extension, so needs data to identify
        assertTypeDetection("testAMR-WB.amr", "audio/amr", "audio/amr-wb", "audio/amr-wb");
        
        // Ditto for the AMR-WB+ subtype, which we don't have a sample file of yet
        //assertTypeDetection("testAMR-WB+.amr", "audio/amr", "audio/amr-wb+", "audio/amr-wb+");
    }
    
    @Test
    public void testEmail() throws IOException {
        // EMLX
        assertTypeDetection("testEMLX.emlx", "message/x-emlx");
        
        // Groupwise
        assertTypeDetection("testGroupWiseEml.eml", "message/rfc822");
        
        // Lotus
        assertTypeDetection("testLotusEml.eml", "message/rfc822");
        
        // MBOX
        assertTypeDetection("headers.mbox", "application/mbox");

        // MBOX
        assertTypeDetection("testMBOX_lengthy_x-headers.mbox", "application/mbox");


        // Thunderbird
        assertTypeDetection("testThunderbirdEml.eml", "message/rfc822");

        //dkim header
        assertTypeDetection("testThunderbirdEml.eml", "message/rfc822");

        //x- custom header
        assertTypeDetection("testRFC822_x-.eml", "message/rfc822");

        //embedded xhtml and img
        assertTypeDetection("testEML_embedded_xhtml_and_img.eml", "message/rfc822");

    }

    @Test
    public void testMessageNews() throws Exception {
        assertTypeByData("message/news", "testMessageNews.txt");
    }
    @Test
    public void testAxCrypt() throws Exception {
        // test-TXT.txt encrypted with a key of "tika"
        assertTypeDetection("testTXT-tika.axx", "application/x-axcrypt");
    }
    
    @Test
    public void testWindowsEXE() throws Exception {
        assertTypeByName("application/x-msdownload", "x.dll");
        assertTypeByName("application/x-ms-installer", "x.msi");
        assertTypeByName("application/x-dosexec", "x.exe");
        
        assertTypeByData("application/x-msdownload; format=pe", "testTinyPE.exe");
        assertTypeByNameAndData("application/x-msdownload; format=pe", "testTinyPE.exe");
        
        // A jar file with part of a PE header, but not a full one
        //  should still be detected as a zip or jar (without/with name)
        assertTypeByData("application/zip", "testJAR_with_PEHDR.jar");
        assertTypeByNameAndData("application/java-archive", "testJAR_with_PEHDR.jar");
    }
    
    @Test
    public void testMatroskaDetection() throws Exception {
        assertType("video/x-matroska", "testMKV.mkv");
        // TODO: Need custom detector data detection, see TIKA-1180
        assertTypeByData("application/x-matroska", "testMKV.mkv");
        assertTypeByNameAndData("video/x-matroska", "testMKV.mkv");
        assertTypeByName("video/x-matroska", "x.mkv");
        assertTypeByName("video/x-matroska", "x.MKV");
        assertTypeByName("audio/x-matroska", "x.mka");
        assertTypeByName("audio/x-matroska", "x.MKA");
    }
    
    @Test
    public void testWebMDetection() throws Exception {
        assertType("video/webm", "testWEBM.webm");
        // TODO: Need custom detector data detection, see TIKA-1180
        assertTypeByData("application/x-matroska", "testWEBM.webm");
        assertTypeByNameAndData("video/webm", "testWEBM.webm");
        assertTypeByName("video/webm", "x.webm");
        assertTypeByName("video/webm", "x.WEBM");
    }

    /** Test getMimeType(byte[]) */
    @Test
    public void testGetMimeType_byteArray() throws IOException {
        // Plain text detection
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
        assertText(new byte[] { 'a', 'b', 'c' });
        assertText(new byte[] { '\t', '\r', '\n', 0x0C, 0x1B });
        assertNotText(new byte[] { '\t', '\r', '\n', 0x0E, 0x1C });
    }
    
    @Test
    public void testBerkeleyDB() throws IOException {
        assertTypeByData(
                "application/x-berkeley-db; format=btree; version=2", 
                "testBDB_btree_2.db");
        assertTypeByData(
                "application/x-berkeley-db; format=btree; version=3", 
                "testBDB_btree_3.db");
        assertTypeByData(
                "application/x-berkeley-db; format=btree; version=4", 
                "testBDB_btree_4.db");
        // V4 and V5 share the same btree format
        assertTypeByData(
                "application/x-berkeley-db; format=btree; version=4", 
                "testBDB_btree_5.db");
        
        assertTypeByData(
                "application/x-berkeley-db; format=hash; version=2", 
                "testBDB_hash_2.db");
        assertTypeByData(
                "application/x-berkeley-db; format=hash; version=3", 
                "testBDB_hash_3.db");
        assertTypeByData(
                "application/x-berkeley-db; format=hash; version=4", 
                "testBDB_hash_4.db");
        assertTypeByData(
                "application/x-berkeley-db; format=hash; version=5", 
                "testBDB_hash_5.db");
    }
    
    /**
     * CBOR typically contains HTML
     */
    @Test
    public void testCBOR() throws IOException {
        assertTypeByNameAndData("application/cbor", "NUTCH-1997.cbor");
        assertTypeByData("application/cbor", "NUTCH-1997.cbor");
    }
    
    @Test
    public void testZLIB() throws IOException {
        // ZLIB encoded versions of testTXT.txt
        assertTypeByData("application/zlib", "testTXT.zlib");
        assertTypeByData("application/zlib", "testTXT.zlib0");
        assertTypeByData("application/zlib", "testTXT.zlib5");
        assertTypeByData("application/zlib", "testTXT.zlib9");
    }
    
    @Test
    public void testTextFormats() throws Exception {
        assertType("application/x-bibtex-text-file", "testBIBTEX.bib");
        assertTypeByData("application/x-bibtex-text-file", "testBIBTEX.bib");
    }
    
    @Test
    public void testCodeFormats() throws Exception {
        assertType("text/x-csrc", "testC.c");
        assertType("text/x-chdr", "testH.h");
        assertTypeByData("text/x-csrc", "testC.c");
        assertTypeByData("text/x-chdr", "testH.h");
        
        assertTypeByName("text/x-java-source", "testJAVA.java");
        assertType("text/x-java-properties", "testJAVAPROPS.properties");
        
        assertType("text/x-matlab", "testMATLAB.m");
        assertType("text/x-matlab", "testMATLAB_wtsgaus.m");
        assertType("text/x-matlab", "testMATLAB_barcast.m");
        assertTypeByData("text/x-matlab", "testMATLAB.m");
        assertTypeByData("text/x-matlab", "testMATLAB_wtsgaus.m");
        assertTypeByData("text/x-matlab", "testMATLAB_barcast.m");
        
        // By name, or by name+data, gets it as JS
        assertTypeByName("application/javascript", "testJS.js");
        assertTypeByName("application/javascript", "testJS_HTML.js");
        assertType("application/javascript", "testJS.js");
        assertType("application/javascript", "testJS_HTML.js");
        
        // With data only, because we have no JS file magic, can't be
        //  detected. One will come through as plain text, the other
        //  as HTML due to <html> in it. TODO Add JS magic. See TIKA-1141 
        //assertTypeByData("application/javascript", "testJS.js");
        //assertTypeByData("application/javascript", "testJS_HTML.js");
    }

    @Test
    public void testWebVTT() throws Exception {
        // With the most common text header
        assertType("text/vtt", "testWebVTT.vtt");
        assertTypeByData("text/vtt", "testWebVTT.vtt");

        // With no text header, just plain WebVTT one
        assertType("text/vtt", "testWebVTT_simple.vtt");
        assertTypeByData("text/vtt", "testWebVTT_simple.vtt");

        // With a custom text header
        assertType("text/vtt", "testWebVTT_header.vtt");
        assertTypeByData("text/vtt", "testWebVTT_header.vtt");
    }
    
    @Test
    public void testMIF() throws Exception {
        assertType("application/vnd.mif", "testMIF.mif");
        assertTypeByData("application/vnd.mif", "testMIF.mif");
    }
    
    @Test
    public void testPKCSSignatures() throws Exception {
        // PKCS7 Signed XML files
        assertType("application/pkcs7-signature", "testPKCS17Sig.xml.p7m");
        assertType("application/pkcs7-signature", "testPKCS17Sig-v2.xml.p7m");
        assertType("application/pkcs7-signature", "testPKCS17Sig-v3.xml.p7m");
        assertType("application/pkcs7-signature", "testPKCS17Sig-v4.xml.p7m");
        assertTypeByData("application/pkcs7-signature", "testPKCS17Sig.xml.p7m");
        assertTypeByData("application/pkcs7-signature", "testPKCS17Sig-v2.xml.p7m");
        assertTypeByData("application/pkcs7-signature", "testPKCS17Sig-v3.xml.p7m");
        assertTypeByData("application/pkcs7-signature", "testPKCS17Sig-v4.xml.p7m");
    }

    @Test
    public void testVandICalendars() throws Exception {
        assertType("text/calendar", "testICalendar.ics");
        assertType("text/x-vcalendar", "testVCalendar.vcs");
        assertTypeByData("text/calendar", "testICalendar.ics");
        assertTypeByData("text/x-vcalendar", "testVCalendar.vcs");
    }

    @Test
    public void testASX() throws Exception {
        assertType("application/x-ms-asx", "testWindowsMediaMeta.asx");
        assertTypeByData("application/x-ms-asx", "testWindowsMediaMeta.asx");
    }

    @Test
    public void testMSOwner() throws Exception {
        assertType("application/x-ms-owner", "testMSOwnerFile");
    }

    @Test
    public void testDJVU() throws Exception {
        assertType("image/vnd.djvu", "testDJVU.djvu");
        assertTypeByData("image/vnd.djvu", "testDJVU.djvu");
    }

    @Test
    public void testEndNoteImport() throws Exception {
        assertType("application/x-endnote-refer", "testEndNoteImportFile.enw");
        assertTypeByData("application/x-endnote-refer", "testEndNoteImportFile.enw");
    }

    @Test
    public void testStataDTA() throws Exception {
        // Filename only gives base type
        assertTypeByName("application/x-stata-dta", "testStataDTA.dta");
        // With data too, can get specific version
        assertTypeByData("application/x-stata-dta; version=13", "testStataDTA.dta");
        // Name + data gets specific version as well
        assertType("application/x-stata-dta; version=13", "testStataDTA.dta");
    }
    
    @Test
    public void testOneNote() throws Exception {
        // With name or data we can get the full details
        assertTypeByName("application/onenote; format=one", "testOneNote.one");
        assertTypeByData("application/onenote; format=one", "testOneNote2.one");
        assertTypeByData("application/onenote; format=one", "testOneNote3.one");
        assertTypeByData("application/onenote; format=one", "testOneNote4.one");
        assertTypeByData("application/onenote; format=one", "testOneNote1.one");

        // TODO Get sample .onetoc2 and .onepkg files
    }

    @Test
    public void testMSWriteFile() throws Exception {
        //This file is govdocs1's 746255.doc
        assertTypeByName("application/x-mswrite", "testMSWriteFile.wri");
        assertTypeByData("application/x-mswrite", "testMSWriteFile.wri");
    }

    @Test
    public void testSASProgramming() throws Exception {
        // Data files we have magic for
        assertTypeByName("application/x-sas-data-v6", "testSAS.sd2");
        assertTypeByData("application/x-sas-data-v6", "testSAS.sd2");
        
        assertTypeByName("application/x-sas-data", "testSAS.sas7bdat");
        assertTypeByData("application/x-sas-data", "testSAS.sas7bdat");
        
        assertTypeByName("application/x-sas-xport", "testSAS.xpt");
        assertTypeByData("application/x-sas-xport", "testSAS.xpt");
        
        // Programs we don't, so must have mime type to detect
        assertTypeByName("application/x-sas", "testSAS.sas");
        assertTypeByData("text/plain", "testSAS.sas");
    }

    @Test
    public void testNLS() throws Exception {
        assertTypeByData("application/x-ms-nls", "testNLS1.nls");
        assertTypeByData("application/x-ms-nls", "testNLS2.nls");
    }

    @Test
    public void testHPROF() throws Exception {
        assertTypeByData("application/vnd.java.hprof", "testJavaHprofBinary");
        assertTypeByData("application/vnd.java.hprof.text", "testJavaHprofText");
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
        try (InputStream stream = TestMimeTypes.class.getResourceAsStream(
                "/test-documents/" + filename)) {
            assertNotNull("Test file not found: " + filename, stream);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            assertEquals(expected, repo.detect(stream, metadata).toString());
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
        try (InputStream stream = TestMimeTypes.class.getResourceAsStream(
                "/test-documents/" + filename)) {
            assertNotNull("Test file not found: " + filename, stream);
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
        }
    }
    
    private void assertTypeByData(String expected, byte[] data)
            throws IOException {
        try (InputStream stream = new ByteArrayInputStream(data)) {
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
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
        try (InputStream stream = TestMimeTypes.class.getResourceAsStream(
                "/test-documents/" + filename)) {
            assertNotNull("Test document not found: " + filename, stream);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            return repo.detect(stream, metadata);
        }
    }
}
