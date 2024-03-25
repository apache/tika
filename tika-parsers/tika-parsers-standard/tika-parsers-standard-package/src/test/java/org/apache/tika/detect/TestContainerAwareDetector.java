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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector;
import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.detect.zip.OpenDocumentDetector;
import org.apache.tika.detect.zip.StreamingZipContainerDetector;
import org.apache.tika.detect.zip.ZipContainerDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser;
import org.apache.tika.parser.iwork.iwana.IWork18PackageParser;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Junit test class for {@link org.apache.tika.detect.microsoft.POIFSContainerDetector}
 */
public class TestContainerAwareDetector extends MultiThreadedTikaTest {
    private final TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
    private final MimeTypes mimeTypes = tikaConfig.getMimeRepository();
    private final MediaTypeRegistry mediaTypeRegistry = mimeTypes.getMediaTypeRegistry();
    private final Detector detector = new DefaultDetector(mimeTypes);
    private final StreamingZipContainerDetector streamingZipDetector =
            new StreamingZipContainerDetector();

    @AfterEach
    public void tearDown() throws TikaException {
        //make sure to reset pool size because it is being randomly resized during the tests
        XMLReaderUtils.setPoolSize(10);
    }

    private void assertTypeByData(String file, String type) throws Exception {
        assertTypeByNameAndData(file, null, type);
    }

    private void assertTypeByNameAndData(String file, String type) throws Exception {
        assertTypeByNameAndData(file, file, type);
    }

    private void assertType(String file, String byData, String byNameAndData) throws Exception {
        assertTypeByData(file, byData);
        assertTypeByNameAndData(file, byNameAndData);
    }

    private void assertTypeByNameAndData(String dataFile, String name, String type)
            throws Exception {
        assertTypeByNameAndData(dataFile, name, type, null);
    }

    private void assertTypeByNameAndData(String dataFile, String name, String typeFromDetector,
                                         String typeFromMagic) throws Exception {
        try (TikaInputStream stream = TikaInputStream
                .get(getResourceAsUrl("/test-documents/" + dataFile))) {
            Metadata m = new Metadata();
            if (name != null) {
                m.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            }

            // Mime Magic version is likely to be less precise
            if (typeFromMagic != null) {
                assertEquals(MediaType.parse(typeFromMagic), mimeTypes.detect(stream, m));
            }

            MediaType expected = MediaType.parse(typeFromDetector);
            // All being well, the detector should get it perfect
            assertEquals(expected, detector.detect(stream, m));

            if (mediaTypeRegistry.isSpecializationOf(expected, MediaType.APPLICATION_ZIP) &&
                    !expected.toString().contains("tika-ooxml-protected")) {

                assertEquals(expected, streamingZipDetector.detect(stream, m),
                        "streaming zip detector failed");
            }
        }
    }

    @Test
    public void testDetectOLE2() throws Exception {
/*        // Microsoft office types known by POI
        assertTypeByData("testEXCEL.xls", "application/vnd.ms-excel");
        assertTypeByData("testWORD.doc", "application/msword");
        assertTypeByData("testPPT.ppt", "application/vnd.ms-powerpoint");
        
        assertTypeByData("test-outlook.msg", "application/vnd.ms-outlook");
        assertTypeByData("test-outlook2003.msg", "application/vnd.ms-outlook");
        assertTypeByData("testVISIO.vsd", "application/vnd.visio");*/
        assertTypeByData("testPUBLISHER.pub", "application/x-mspublisher");
        assertTypeByData("testWORKS.wps", "application/vnd.ms-works");
        assertTypeByData("testWORKS2000.wps", "application/vnd.ms-works");

        // older Works Word Processor files can't be recognized
        // they were created with Works Word Processor 7.0 (hence the text inside)
        // and exported to the older formats with the "Save As" feature
        assertTypeByData("testWORKSWordProcessor3.0.wps", "application/vnd.ms-works");
        assertTypeByData("testWORKSWordProcessor4.0.wps", "application/vnd.ms-works");
        assertTypeByData("testWORKSSpreadsheet7.0.xlr", "application/x-tika-msworks-spreadsheet");
        assertTypeByData("testPROJECT2003.mpp", "application/vnd.ms-project");
        assertTypeByData("testPROJECT2007.mpp", "application/vnd.ms-project");

        // Excel95 can be detected by not parsed
        assertTypeByData("testEXCEL_95.xls", "application/vnd.ms-excel");

        // Try some ones that POI doesn't handle, that are still OLE2 based
        assertTypeByData("testCOREL.shw", "application/x-corelpresentations");
        assertTypeByData("testQUATTRO.qpw", "application/x-quattro-pro; version=9");
        assertTypeByData("testQUATTRO.wb3", "application/x-quattro-pro; version=7-8");

        assertTypeByData("testHWP_5.0.hwp", "application/x-hwp-v5");


        // With the filename and data
        assertTypeByNameAndData("testEXCEL.xls", "application/vnd.ms-excel");
        assertTypeByNameAndData("testWORD.doc", "application/msword");
        assertTypeByNameAndData("testPPT.ppt", "application/vnd.ms-powerpoint");

        // With the wrong filename supplied, data will trump filename
        assertTypeByNameAndData("testEXCEL.xls", "notWord.doc", "application/vnd.ms-excel");
        assertTypeByNameAndData("testWORD.doc", "notExcel.xls", "application/msword");
        assertTypeByNameAndData("testPPT.ppt", "notWord.doc", "application/vnd.ms-powerpoint");

        // With a filename of a totally different type, data will trump filename
        assertTypeByNameAndData("testEXCEL.xls", "notPDF.pdf", "application/vnd.ms-excel");
        assertTypeByNameAndData("testEXCEL.xls", "notPNG.png", "application/vnd.ms-excel");
        assertTypeByData("testDGN8.dgn", "image/vnd.dgn; version=8");
    }

    @Test
    @Disabled("until we have an actual hwpx example file in our repo")
    public void testHWPX() throws Exception {
        assertTypeByData("testHWPX.hwpx", "application/hwp+zip");
        assertTypeByNameAndData("testHWPX.hwpx", "application/hwp+zip");
    }

    /**
     * There is no way to distinguish "proper" StarOffice files from templates.
     * All templates have the same extension but their actual type depends on
     * the magic. Our current MimeTypes class doesn't allow us to use the same
     * glob pattern in more than one mimetype.
     *
     * @throws Exception
     */
    @Test
    public void testDetectStarOfficeFiles() throws Exception {
        assertType("testStarOffice-5.2-calc.sdc", "application/vnd.stardivision.calc",
                "application/vnd.stardivision.calc");
        assertType("testVORCalcTemplate.vor", "application/vnd.stardivision.calc",
                "application/vnd.stardivision.calc");
        assertType("testStarOffice-5.2-draw.sda", "application/vnd.stardivision.draw",
                "application/vnd.stardivision.draw");
        assertType("testVORDrawTemplate.vor", "application/vnd.stardivision.draw",
                "application/vnd.stardivision.draw");
        assertType("testStarOffice-5.2-impress.sdd", "application/vnd.stardivision.impress",
                "application/vnd.stardivision.impress");
        assertType("testVORImpressTemplate.vor", "application/vnd.stardivision.impress",
                "application/vnd.stardivision.impress");
        assertType("testStarOffice-5.2-writer.sdw", "application/vnd.stardivision.writer",
                "application/vnd.stardivision.writer");
        assertType("testVORWriterTemplate.vor", "application/vnd.stardivision.writer",
                "application/vnd.stardivision.writer");
        //file from open office bug tracker issue #6452
        //star office >6.0
        assertType("testStarOffice-6.0-writer.sxw", "application/vnd.sun.xml.writer",
                "application/vnd.sun.xml.writer");
        //ooo byg #5116
        //can't find a diff in contents btwn sxw and stw...need to rely on file extension
        assertTypeByNameAndData("testStarOffice-6.0-writer-template.stw",
                "application/vnd.sun.xml.writer.template", "application/vnd.sun.xml.writer",
                "application/zip");

        //ooo bug #1151
        assertType("testStarOffice-6.0-calc.sxc", "application/vnd.sun.xml.calc",
                "application/vnd.sun.xml.calc");
        //ooo bug #261
        assertType("testStarOffice-6.0-draw.sxd", "application/vnd.sun.xml.draw",
                "application/vnd.sun.xml.draw");
        //ooo bug #5336
        assertType("testStarOffice-6.0-draw.sxi", "application/vnd.sun.xml.impress",
                "application/vnd.sun.xml.impress");

        //ooo bug #67431 -- had to manually fix the name spacing in the manifest.xml
        assertType("testOpenOffice-autotext.bau", "application/vnd.openofficeorg.autotext",
                "application/vnd.openofficeorg.autotext");
        //ooo bug #110760
        assertType("testOpenOffice-extension.oxt", "application/vnd.openofficeorg.extension",
                "application/vnd.openofficeorg.extension");
    }

    @Test
    public void testOpenContainer() throws Exception {
        try (TikaInputStream stream = TikaInputStream
                .get(getResourceAsUrl("/test-documents/testPPT.ppt"))) {
            assertNull(stream.getOpenContainer());
            assertEquals(MediaType.parse("application/vnd.ms-powerpoint"),
                    detector.detect(stream, new Metadata()));
            assertTrue(stream.getOpenContainer() instanceof POIFSFileSystem);
        }
    }

    /**
     * EPub uses a similar mimetype entry to OpenDocument for storing
     * the mimetype within the parent zip file
     */
    @Test
    public void testDetectEPub() throws Exception {
        assertTypeByData("testEPUB.epub", "application/epub+zip");
        assertTypeByData("testiBooks.ibooks", "application/x-ibooks+zip");
    }

    @Test
    public void testDetectLotusNotesEml() throws Exception {
        // Lotus .eml files aren't guaranteed to have any of the magic 
        // matches as the first line, but should have X-Notes-Item and Message-ID
        assertTypeByData("testLotusEml.eml", "message/rfc822");
    }

    @Test
    public void testDetectODF() throws Exception {
        assertTypeByData("testODFwithOOo3.odt", "application/vnd.oasis.opendocument.text");
        assertTypeByData("testOpenOffice2.odf", "application/vnd.oasis.opendocument.formula");
        assertTypeByData("testODTnotaZipFile.odt", "text/plain");
    }

    @Test
    public void test3MF() throws Exception {
        assertTypeByData("test3mf.3mf", "application/vnd.ms-package.3dmanufacturing-3dmodel+xml");
        assertTypeByNameAndData("test3mf.3mf", "application/vnd.ms-package.3dmanufacturing-3dmodel+xml");
    }
    @Test
    public void testODFDifferentOrder() throws Exception {
        //TIKA-3356
        List<ZipContainerDetector> detectors = new ArrayList<>();
        detectors.add(new OPCPackageDetector());
        detectors.add(new OpenDocumentDetector());
        DefaultZipContainerDetector zipContainerDetector = new DefaultZipContainerDetector(detectors);
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testODFwithOOo3.odt"))) {
            //force underlying file to test the proper behavior with the underlying zipfile
            tis.getFile();
            MediaType mt = zipContainerDetector.detect(tis, new Metadata());
            assertEquals("application/vnd.oasis.opendocument.text", mt.toString());
            assertNotNull(tis.getOpenContainer());
            assertEquals("org.apache.commons.compress.archivers.zip.ZipFile",
                    tis.getOpenContainer().getClass().getName());
        }
    }

    @Test
    public void testDetectOOXML() throws Exception {
        assertTypeByData("testEXCEL.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertTypeByData("testWORD.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertTypeByData("testPPT.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // Check some of the less common OOXML types
        assertTypeByData("testPPT.pptm",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12");
        assertTypeByData("testPPT.ppsx",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        assertTypeByData("testPPT.ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
        assertTypeByData("testDOTM.dotm", "application/vnd.ms-word.template.macroEnabled.12");
        assertTypeByData("testEXCEL.strict.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertTypeByData("testEXCEL_macro_enabled_template.xltm",
                "application/vnd.ms-excel.template.macroenabled.12");
        assertTypeByData("testEXCEL_template.xltx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template");

        assertTypeByData("testPPT.xps", "application/vnd.ms-xpsdocument");

        assertTypeByData("testVISIO.vsdm", "application/vnd.ms-visio.drawing.macroenabled.12");
        assertTypeByData("testVISIO.vsdx", "application/vnd.ms-visio.drawing");
        assertTypeByData("testVISIO.vssm", "application/vnd.ms-visio.stencil.macroenabled.12");
        assertTypeByData("testVISIO.vssx", "application/vnd.ms-visio.stencil");
        assertTypeByData("testVISIO.vstm", "application/vnd.ms-visio.template.macroenabled.12");
        assertTypeByData("testVISIO.vstx", "application/vnd.ms-visio.template");

        // .xlsb is an OOXML file containing the binary parts, and not
        //  an OLE2 file as you might initially expect!
        assertTypeByData("testEXCEL.xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12");

        // With the filename and data
        assertTypeByNameAndData("testEXCEL.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertTypeByNameAndData("testWORD.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertTypeByNameAndData("testPPT.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // With the wrong filename supplied, data will trump filename
        assertTypeByNameAndData("testEXCEL.xlsx", "notWord.docx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertTypeByNameAndData("testWORD.docx", "notExcel.xlsx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertTypeByNameAndData("testPPT.pptx", "notWord.docx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // With an incorrect filename of a different container type, data trumps filename
        assertTypeByNameAndData("testEXCEL.xlsx", "notOldExcel.xls",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Password Protected OLE2 files are fairly straightforward to detect, as they
     * have the same structure as regular OLE2 files. (Core streams may be encrypted
     * however)
     */
    @Test
    public void testDetectProtectedOLE2() throws Exception {
        assertTypeByData("testEXCEL_protected_passtika.xls", "application/vnd.ms-excel");
        assertTypeByData("testWORD_protected_passtika.doc", "application/msword");
        assertTypeByData("testPPT_protected_passtika.ppt", "application/vnd.ms-powerpoint");
        assertTypeByNameAndData("testEXCEL_protected_passtika.xls", "application/vnd.ms-excel");
        assertTypeByNameAndData("testWORD_protected_passtika.doc", "application/msword");
        assertTypeByNameAndData("testPPT_protected_passtika.ppt", "application/vnd.ms-powerpoint");
    }

    /**
     * Password Protected OOXML files are much more tricky beasts to work with.
     * They have a very different structure to regular OOXML files, and instead
     * of being ZIP based they are actually an OLE2 file which contains the
     * OOXML structure within an encrypted stream.
     * This makes detecting them much harder...
     */
    @Test
    public void testDetectProtectedOOXML() throws Exception {
        // Encrypted Microsoft Office OOXML files have OLE magic but
        //  special streams, so we can tell they're Protected OOXML
        assertTypeByData("testEXCEL_protected_passtika.xlsx", "application/x-tika-ooxml-protected");
        assertTypeByData("testWORD_protected_passtika.docx", "application/x-tika-ooxml-protected");
        assertTypeByData("testPPT_protected_passtika.pptx", "application/x-tika-ooxml-protected");

        // At the moment, we can't use the name to specialise
        // See discussions on TIKA-790 for details
        assertTypeByNameAndData("testEXCEL_protected_passtika.xlsx",
                "application/x-tika-ooxml-protected");
        assertTypeByNameAndData("testWORD_protected_passtika.docx",
                "application/x-tika-ooxml-protected");
        assertTypeByNameAndData("testPPT_protected_passtika.pptx",
                "application/x-tika-ooxml-protected");
    }

    /**
     * Check that temporary files created by Tika are removed after
     * closing TikaInputStream.
     */
    @Test
    public void testRemovalTempfiles() throws Exception {
        assertRemovalTempfiles("testWORD.docx");
        assertRemovalTempfiles("test-documents.zip");
    }

    private int countTemporaryFiles() {
        //TODO: fix this.  This can prevent multiple parallel builds
        //from running at the same time because there can be more than one
        //process writing to apache-tika-*
        return Objects.requireNonNull(new File(System.getProperty("java.io.tmpdir"))
                .listFiles((dir, name) -> name.startsWith("apache-tika-"))).length;
    }

    private void assertRemovalTempfiles(String fileName) throws Exception {
        int numberOfTempFiles = countTemporaryFiles();

        try (TikaInputStream stream = TikaInputStream
                .get(getResourceAsUrl("/test-documents/" + fileName))) {
            detector.detect(stream, new Metadata());
        }

        assertEquals(numberOfTempFiles, countTemporaryFiles());
    }

    @Test
    public void testDetectIWork() throws Exception {
        assertTypeByData("testKeynote.key", "application/vnd.apple.keynote");
        assertTypeByData("testNumbers.numbers", "application/vnd.apple.numbers");
        assertTypeByData("testPages.pages", "application/vnd.apple.pages");
    }

    @Test
    public void testDetectIWork2013() throws Exception {
        assertTypeByData("testKeynote2013.key",
                IWork13PackageParser.IWork13DocumentType.KEYNOTE13.getType().toString());
        // Without decoding the Document snappy stream, we can't tell the
        //  difference between these two just based on the zip entries
        assertTypeByData("testNumbers2013.numbers",
                IWork13PackageParser.IWork13DocumentType.UNKNOWN13.getType().toString());
        assertTypeByData("testPages2013.pages",
                IWork13PackageParser.IWork13DocumentType.UNKNOWN13.getType().toString());
    }

    @Test
    public void testDetectIWork2018() throws Exception {
        //file from libre office issue tracker, issue #123573
        //manually removed jpegs for the sake of space*/
        assertTypeByData("testKeynote2018.key",
                IWork18PackageParser.IWork18DocumentType.KEYNOTE18.getType().toString());
        //see https://bugs.documentfoundation.org/show_bug.cgi?id=120709 for a 2018 numbers file
        //see https://bugs.documentfoundation.org/show_bug.cgi?id=120707 for a 2018 pages file
    }


    @Test
    public void testDetectKMZ() throws Exception {
        assertTypeByData("testKMZ.kmz", "application/vnd.google-earth.kmz");
    }

    @Test
    public void testDetectIPA() throws Exception {
        assertTypeByNameAndData("testIPA.ipa", "application/x-itunes-ipa");
        assertTypeByData("testIPA.ipa", "application/x-itunes-ipa");
    }

    @Test
    public void testASiC() throws Exception {
        assertTypeByData("testASiCE.asice", "application/vnd.etsi.asic-e+zip");
        assertTypeByData("testASiCS.asics", "application/vnd.etsi.asic-s+zip");
        assertTypeByNameAndData("testASiCE.asice", "application/vnd.etsi.asic-e+zip");
        assertTypeByNameAndData("testASiCS.asics", "application/vnd.etsi.asic-s+zip");
    }

    @Test
    public void testDetectZip() throws Exception {
        assertTypeByData("test-documents.zip", "application/zip");
        assertTypeByData("test-zip-of-zip.zip", "application/zip");

        // JAR based formats
        assertTypeByData("testJAR.jar", "application/java-archive");
        assertTypeByData("testWAR.war", "application/x-tika-java-web-archive");
        assertTypeByData("testEAR.ear", "application/x-tika-java-enterprise-archive");
        assertTypeByData("testAPK.apk", "application/vnd.android.package-archive");

        // JAR with HTML files in it
        assertTypeByNameAndData("testJAR_with_HTML.jar", "testJAR_with_HTML.jar",
                "application/java-archive", "application/java-archive");
    }

    @Test
    public void testTarWithNoMagic() throws Exception {
        assertTypeByData("testTAR_no_magic.tar", "application/x-tar");
    }

    @Test
    public void testLZMAOOM() throws Exception {
        assertTypeByData("testLZMA_oom", "application/x-lzma");
    }

    @Test
    @Disabled("find acceptable test file")
    public void testLyr() throws Exception {
        //file used in development but not added to
        //repo: https://cmgds.marine.usgs.gov/publications/of2005-1346/arcgis/bathy/Bathymetry.lyr
        assertTypeByNameAndData("testLyr.lyr", "x-esri-layer",
                "application/x-esri-layer", "application/x-tika-msoffice");
    }

    @Test
    public void testCompressOOM() throws Exception {
        assertTypeByData("testZ_oom.Z", "application/x-compress");
    }

    private TikaInputStream getTruncatedFile(String name, int n) throws IOException {
        try (InputStream input = getResourceAsStream("/test-documents/" + name)) {
            byte[] bytes = new byte[n];
            int m = 0;
            while (m < bytes.length) {
                int i = input.read(bytes, m, bytes.length - m);
                if (i != -1) {
                    m += i;
                } else {
                    throw new IOException("Unexpected end of stream");
                }
            }
            return TikaInputStream.get(bytes);
        }
    }

    @Test
    public void testTruncatedFiles() throws Exception {
        // First up a truncated OOXML (zip) file

        // With only the data supplied, the best we can do is the container
        Metadata m = new Metadata();
        try (TikaInputStream xlsx = getTruncatedFile("testEXCEL.xlsx", 300)) {
            assertEquals(MediaType.application("x-tika-ooxml"), detector.detect(xlsx, m));
        }

        // With truncated data + filename, we can use the filename to specialise
        m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testEXCEL.xlsx");
        try (TikaInputStream xlsx = getTruncatedFile("testEXCEL.xlsx", 300)) {
            assertEquals(
                    MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    detector.detect(xlsx, m));
        }

        // Now a truncated OLE2 file 
        m = new Metadata();
        try (TikaInputStream xls = getTruncatedFile("testEXCEL.xls", 400)) {
            assertEquals(MediaType.application("x-tika-msoffice"), detector.detect(xls, m));
        }

        // Finally a truncated OLE2 file, with a filename available
        m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testEXCEL.xls");
        try (TikaInputStream xls = getTruncatedFile("testEXCEL.xls", 400)) {
            assertEquals(MediaType.application("vnd.ms-excel"), detector.detect(xls, m));
        }
    }

    @Test
    public void testXMLMultiThreaded() throws Exception {
        Detector detector = new Tika().getDetector();
        FileFilter filter = pathname -> pathname.getName().endsWith(".xml");
        int numThreads = 1;
        XMLReaderUtils.setPoolSize(numThreads);
        testDetector(detector, numThreads, 20, filter, numThreads * 2);
    }

    @Test
    public void testAllMultithreaded() throws Exception {

        Detector detector = new Tika().getDetector();
        FileFilter filter = new FileFilter() {
            //TODO: create proper randomized framework that will record seed, etc...
            private final Random random = new Random();
            //increase this to the number of files for a true smoke test
            //for now, randomly pick 20 files.
            int toProcess = 20;
            int processed = 0;

            @Override
            public boolean accept(File pathname) {
                if (processed >= toProcess) {
                    return false;
                } else if (random.nextBoolean()) {
                    processed++;
                    return true;
                }
                return false;
            }
        };
        int numThreads = 20;
        XMLReaderUtils.setPoolSize(numThreads);

        testDetector(detector, numThreads, 50, filter, numThreads * 3);
    }

    @Test
    public void testOpenOfficeInAZip() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOpenOfficeInAZip.zip");
        assertEquals(3, metadataList.size());
        assertEquals("application/vnd.oasis.opendocument.presentation",
                metadataList.get(2).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testBPList() throws Exception {
        assertTypeByData("testMemgraph.memgraph", "application/x-bplist-memgraph");
        assertTypeByData("testWEBARCHIVE.webarchive", "application/x-bplist-webarchive");
        assertTypeByData("testBPList.bplist", "application/x-bplist-itunes");
    }
}
