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
package org.apache.tika.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.ProcessUtils;

/**
 * Tests the Tika's cli
 */
public class TikaCLITest {

    static final File TEST_DATA_FILE = new File("src/test/resources/test-data");


    @TempDir
    private Path extractDir;
    private final URI testDataURI = TEST_DATA_FILE.toURI();
    /* Test members */
    private ByteArrayOutputStream outContent = null;
    private ByteArrayOutputStream errContent = null;
    private PrintStream stdout = null;
    private PrintStream stderr = null;
    private String resourcePrefix;



    protected static void assertExtracted(Path p, String allFiles) throws IOException {

        assertTrue(Files.exists(p), "File " + p.getFileName() + " not found in " + allFiles);

        assertFalse(Files.isDirectory(p), "File " + p.getFileName() + " is a directory!");

        assertTrue(Files.size(p) > 0,
                "File " + p.getFileName() + " wasn't extracted with " + "contents");
    }

    /**
     * reset resourcePrefix
     * save original System.out and System.err
     * clear outContent and errContent if they are not empty
     * set outContent and errContent as System.out and System.err
     */
    @BeforeEach
    public void setUp() throws Exception {
        resourcePrefix = testDataURI.toString();
        stdout = System.out;
        stderr = System.err;
        resetContent();
    }

    /**
     * Tears down the test. Returns the System.out and System.err
     */
    @AfterEach
    public void tearDown() {
        System.setOut(stdout);
        System.setErr(stderr);
    }

    /**
     * clear outContent and errContent if they are not empty by create a new one.
     * set outContent and errContent as System.out and System.err
     */
    private void resetContent() throws Exception {
        if (outContent == null || outContent.size() > 0) {
            outContent = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outContent, true, UTF_8.name()));
        }

        if (errContent == null || errContent.size() > 0) {
            errContent = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errContent, true, UTF_8.name()));
        }
    }

    /**
     * Tests --list-parser-detail option of the cli
     * Tests --list-parser-details option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListParserDetail() throws Exception {
        String content = getParamOutContent("--list-parser-detail");
        assertTrue(content.contains("application/vnd.oasis.opendocument.text-web"));

        content = getParamOutContent("--list-parser-details");
        assertTrue(content.contains("application/vnd.oasis.opendocument.text-web"));
    }

    /**
     * Tests --list-parser option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListParsers() throws Exception {
        String content = getParamOutContent("--list-parser");
        assertTrue(content.contains("org.apache.tika.parser.iwork.IWorkPackageParser"));
    }

    /**
     * Tests -x option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testXMLOutput() throws Exception {
        String content = getParamOutContent("-x", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("?xml version=\"1.0\" encoding=\"UTF-8\"?"));

        content = getParamOutContent("-x", "--digest=SHA256", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains(
                "<meta name=\"X-TIKA:digest:SHA256\" content=\"e90779adbac09c4ee"));

    }

    /**
     * Tests a -h option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testHTMLOutput() throws Exception {
        String content = getParamOutContent("-h", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("html xmlns=\"http://www.w3.org/1999/xhtml"));
        assertTrue(content.contains("<title></title>"),
                "Expanded <title></title> element should be present");

        content = getParamOutContent("-h", "--digest=SHA384", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains(
                "<meta name=\"X-TIKA:digest:SHA384\" content=\"c69ea023f5da95a026"));
    }

    /**
     * Tests -t option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testTextOutput() throws Exception {
        String content = getParamOutContent("-t", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("finished off the cake"));
    }

    /**
     * Tests -A option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testContentAllOutput() throws Exception {
        String[] params = {"-A", resourcePrefix + "testJsonMultipleInts.html"};
        TikaCLI.main(params);
        String out = outContent.toString(UTF_8.name());
        assertTrue(out.contains("this is a title"));
        assertTrue(out.contains("body"));
    }

    /**
     * Tests -f option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testForkParser() throws Exception {
        String content = getParamOutContent("-f", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("finished off the cake"));
    }

    /**
     * Tests -m option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testMetadataOutput() throws Exception {
        String content = getParamOutContent("-m", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("text/plain"));

        content = getParamOutContent("-m", "--digest=SHA512", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("text/plain"));
        assertTrue(content.contains("X-TIKA:digest:SHA512: dd459d99bc19ff78fd31fbae46e0"));
    }

    /**
     * Basic tests for -json option
     *
     * @throws Exception
     */
    @Test
    public void testJsonMetadataOutput() throws Exception {
        String json = getParamOutContent("--json", "--digest=MD2",
                resourcePrefix + "testJsonMultipleInts.html");
        //TIKA-1310
        assertTrue(json.contains("\"fb:admins\":\"1,2,3,4\","));
        assertTrue(json.contains("\"X-TIKA:digest:MD2\":"));
    }

    /**
     * Test for -json with prettyprint option
     *
     * @throws Exception
     */
    @Test
    public void testJsonMetadataPrettyPrintOutput() throws Exception {
        String json =
                getParamOutContent("--json", "-r", resourcePrefix + "testJsonMultipleInts.html");

        assertTrue(json.contains(
                "\"X-TIKA:Parsed-By\" : [ \"org.apache.tika.parser.DefaultParser\", " +
                        "\"org.apache.tika.parser.html.JSoupParser\" ],"));
        //test pretty-print alphabetic sort of keys
        int enc = json.indexOf("\"Content-Encoding\"");
        int fb = json.indexOf("fb:admins");
        int title = json.indexOf("\"dc:title\"");
        assertTrue(enc > -1 && fb > -1 && enc < fb);
        assertTrue(fb > -1 && title > -1 && fb > title);
    }

    /**
     * Tests -l option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testLanguageOutput() throws Exception {
        String content = getParamOutContent("-l", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("en"));
    }

    /**
     * Tests -d option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testDetectOutput() throws Exception {
        String content = getParamOutContent("-d", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("text/plain"));
    }

    /**
     * Tests --list-met-models option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListMetModels() throws Exception {
        String content = getParamOutContent("--list-met-models", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("text/plain"));
    }

    /**
     * Tests --list-supported-types option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListSupportedTypes() throws Exception {
        String content =
                getParamOutContent("--list-supported-types", resourcePrefix + "alice.cli.test");
        assertTrue(content.contains("supertype: application/octet-stream"));
    }

    @Test
    public void testExtractSimple() throws Exception {
        String[] expectedChildren =
                new String[]{"MBD002B040A.cdx", "file4.png", "MBD002B0FA6.bin", "MBD00262FE3.txt",
                        "file0.emf"};
        testExtract("/coffee.xls", expectedChildren, 8);
    }

    @Test
    public void testExtractAbsolute() throws Exception {
        String[] expectedChildren = new String[]{"dangerous/dont/touch.pl",};
        testExtract("testZip_absolutePath.zip", expectedChildren, 2);
    }

    @Test
    public void testExtractRelative() throws Exception {
        String[] expectedChildren = new String[]{"touch.pl",};
        testExtract("testZip_relative.zip", expectedChildren);
    }

    @Test
    public void testExtractOverlapping() throws Exception {
        //there should be two files, one with a prepended uuid-f1.txt
        String[] expectedChildren = new String[]{"f1.txt",};
        testExtract("testZip_overlappingNames.zip", expectedChildren, 2);
    }

    @Test
    public void testExtract0x00() throws Exception {
        String[] expectedChildren = new String[]{"dang erous.pl",};
        testExtract("testZip_zeroByte.zip", expectedChildren);
    }

    private void testExtract(String targetFile, String[] expectedChildrenFileNames)
            throws Exception {
        testExtract(targetFile, expectedChildrenFileNames, expectedChildrenFileNames.length);
    }

    private void testExtract(String targetFile, String[] expectedChildrenFileNames,
                             int expectedLength) throws Exception {

        String[] params = {"--extract-dir=" +
                ProcessUtils.escapeCommandLine(extractDir.toAbsolutePath().toString()), "-z",
                resourcePrefix + "/" + targetFile};

        TikaCLI.main(params);

        String[] tempFileNames = extractDir.toFile().list();
        assertNotNull(tempFileNames);
        assertEquals(expectedLength, tempFileNames.length);
        String allFiles = String.join(" : ", tempFileNames);

        for (String expectedChildName : expectedChildrenFileNames) {
            assertExtracted(extractDir.resolve(expectedChildName), allFiles);
        }
    }

    @Test
    public void testExtractTgz() throws Exception {
        //TIKA-2564

        String[] params = {"--extract-dir=" + extractDir.toAbsolutePath(), "-z",
                resourcePrefix + "/test-documents.tgz"};

        TikaCLI.main(params);

        String[] tempFileNames = extractDir.toFile().list();
        assertNotNull(tempFileNames);
        String allFiles = String.join(" : ", tempFileNames);

        Path expectedTAR = extractDir.resolve("test-documents.tar");

        assertExtracted(expectedTAR, allFiles);
    }

    // TIKA-920
    @Test
    public void testMultiValuedMetadata() throws Exception {
        String content = getParamOutContent("-m", resourcePrefix + "testMultipleSheets.numbers");
        assertTrue(content.contains("sheetNames: Checking"));
        assertTrue(content.contains("sheetNames: Secon sheet"));
        assertTrue(content.contains("sheetNames: Logical Sheet 3"));
        assertTrue(content.contains("sheetNames: Sheet 4"));
    }

    // TIKA-1031
    @Test
    public void testZipWithSubdirs() throws Exception {
        new File("subdir/foo.txt").delete();
        new File("subdir").delete();
        String content = getParamOutContent("-z", "--extract-dir=target",
                resourcePrefix + "testWithSubdirs.zip");
        assertTrue(content.contains("Extracting 'subdir/foo.txt'"));
        // clean up. TODO: These should be in target.
        new File("target/subdir/foo.txt").delete();
        new File("target/subdir").delete();
    }

    @Test
    public void testExtractInlineImages() throws Exception {
        String[] params = {"--extract-dir=" + extractDir.toAbsolutePath(), "-z",
                resourcePrefix + "/testPDF_childAttachments.pdf"};

        TikaCLI.main(params);

        String[] tempFileNames = extractDir.toFile().list();
        assertNotNull(tempFileNames);
        String allFiles = String.join(" : ", tempFileNames);

        Path jpeg = extractDir.resolve("image0.jpg");
        //tiff isn't extracted without optional image dependency
//            File tiff = new File(tempFile, "image1.tif");
        Path jobOptions = extractDir.resolve("Press Quality(1).joboptions");
        Path doc = extractDir.resolve("Unit10.doc");

        assertExtracted(jpeg, allFiles);
        assertExtracted(jobOptions, allFiles);
        assertExtracted(doc, allFiles);
    }

    @Test
    public void testDefaultConfigException() throws Exception {
        //default xml parser will throw TikaException
        //this and TestConfig() are broken into separate tests so that
        //setUp and tearDown() are called each time
        String[] params = {resourcePrefix + "bad_xml.xml"};
        boolean tikaEx = false;
        try {
            TikaCLI.main(params);
        } catch (TikaException e) {
            tikaEx = true;
        }
        assertTrue(tikaEx);
    }

    @Test
    public void testConfig() throws Exception {
        String content =
                getParamOutContent("--config=" + TEST_DATA_FILE.toString() + "/tika-config1.xml",
                        resourcePrefix + "bad_xml.xml");
        assertTrue(content.contains("apple"));
        assertTrue(content.contains("org.apache.tika.parser.html.JSoupParser"));
    }

    @Test
    public void testConfigIgnoreInit() throws Exception {
        String content = getParamOutContent(
                "--config=" + TEST_DATA_FILE.toString() + "/TIKA-2389-ignore-init-problems.xml",
                resourcePrefix + "test_recursive_embedded.docx");
        assertTrue(content.contains("embed_1a"));
        //TODO: add a real unit test that configures logging to a file to test that nothing is
        //written at the various logging levels
    }


    @Test
    public void testJsonRecursiveMetadataParserMetadataOnly() throws Exception {
        String content = getParamOutContent("-m", "-J", "-r",
                resourcePrefix + "test_recursive_embedded.docx");
        assertTrue(content.contains("\"extended-properties:AppVersion\" : \"15.0000\","));
        assertTrue(content.contains(
                "\"extended-properties:Application\" : \"Microsoft Office Word\","));
        assertTrue(content.contains("\"X-TIKA:embedded_resource_path\" : \"/embed1.zip\""));
        assertFalse(content.contains("X-TIKA:content"));
    }

    @Test
    public void testJsonRecursiveMetadataParserDefault() throws Exception {
        String content =
                getParamOutContent("-J", "-r", resourcePrefix + "test_recursive_embedded.docx");
        assertTrue(content.contains(
                "\"X-TIKA:content\" : \"<html xmlns=\\\"http://www.w3.org/1999/xhtml"));
    }

    @Test
    public void testJsonRecursiveMetadataParserText() throws Exception {
        String content = getParamOutContent("-J", "-r", "-t",
                resourcePrefix + "test_recursive_embedded.docx");
        assertTrue(content.contains("\\n\\nembed_4\\n"));
        assertTrue(content.contains("\\n\\nembed_0"));
    }

    @Test
    public void testDigestInJson() throws Exception {
        String content = getParamOutContent("-J", "-r", "-t", "--digest=MD5",
                resourcePrefix + "test_recursive_embedded.docx");
        assertTrue(
                content.contains("\"X-TIKA:digest:MD5\" : \"59f626e09a8c16ab6dbc2800c685f772\","));
        assertTrue(
                content.contains("\"X-TIKA:digest:MD5\" : \"f9627095ef86c482e61d99f0cc1cf87d\""));
    }

    @Test
    public void testConfigSerializationStaticAndCurrent() throws Exception {
        String content = getParamOutContent("--dump-static-config");
        //make sure at least one detector is there
        assertTrue(content.contains(
                "<detector class=\"org.apache.tika.detect.microsoft.POIFSContainerDetector\"/>"));
        //make sure Executable is there because follow on tests of custom config
        //test that it has been turned off.
        assertTrue(content.contains(
                "<parser class=\"org.apache.tika.parser.executable.ExecutableParser\"/>"));

        content = getParamOutContent("--dump-current-config");
        //make sure at least one detector is there
        assertTrue(
                content.contains("<detector class=\"org.apache.tika.detect.DefaultDetector\"/>"));
        //and at least one parser
        assertTrue(content.contains("<parser class=\"org.apache.tika.parser.DefaultParser\"/>"));
    }

    @Test
    public void testConfigSerializationCustomMinimal() throws Exception {
        String content =
                getParamOutContent("--config=" + TEST_DATA_FILE.toString() + "/tika-config2.xml",
                        "--dump-minimal-config").replaceAll("[\r\n\t ]+", " ");

        String expected = "<parser class=\"org.apache.tika.parser.DefaultParser\">" +
                " <mime-exclude>application/pdf</mime-exclude>" +
                " <mime-exclude>image/jpeg</mime-exclude> " + "</parser> " +
                "<parser class=\"org.apache.tika.parser.EmptyParser\">" +
                " <mime>application/pdf</mime> " + "</parser>";
        assertTrue(content.contains(expected));
    }

    @Test
    public void testConfigSerializationCustomStatic() throws Exception {
        String content =
                getParamOutContent("--config=" + TEST_DATA_FILE.toString() + "/tika-config2.xml",
                        "--dump-static-config");
        assertFalse(content.contains("org.apache.tika.parser.executable.Executable"));
    }

    /**
     * Tests --list-detector option of the cli
     * Tests --list-detectors option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListDetectors() throws Exception {
        String content = getParamOutContent("--list-detector");
        assertTrue(content.contains("org.apache.tika.detect.DefaultDetector"));

        content = getParamOutContent("--list-detectors");
        assertTrue(content.contains("org.apache.tika.detect.DefaultDetector"));
    }

    /**
     * Tests --list-parser-detail-apt option of the cli
     * Tests --list-parser-details-apt option of the cli
     *
     * @throws Exception
     */
    @Test
    public void testListParserDetailApt() throws Exception {
        String content = getParamOutContent("--list-parser-detail-apt");
        assertTrue(content.contains("application/vnd.oasis.opendocument.text-web"));

        content = getParamOutContent("--list-parser-details-apt");
        assertTrue(content.contains("application/vnd.oasis.opendocument.text-web"));
    }

    /**
     * reset outContent and errContent if they are not empty
     * run given params in TikaCLI and return outContent String with UTF-8
     */
    String getParamOutContent(String... params) throws Exception {
        resetContent();
        TikaCLI.main(params);
        return outContent.toString("UTF-8");
    }
}
