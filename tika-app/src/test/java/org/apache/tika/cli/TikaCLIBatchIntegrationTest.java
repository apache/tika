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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TikaCLIBatchIntegrationTest {

    private Path testInputDir = Paths.get("src/test/resources/test-data");
    private String testInputDirForCommandLine;
    private Path tempOutputDir;
    private String tempOutputDirForCommandLine;
    private OutputStream out = null;
    private OutputStream err = null;
    private ByteArrayOutputStream outBuffer = null;

    @Before
    public void setup() throws Exception {
        tempOutputDir = Files.createTempDirectory("tika-cli-test-batch-");
        outBuffer = new ByteArrayOutputStream();
        PrintStream outWriter = new PrintStream(outBuffer, true, UTF_8.name());
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream errWriter = new PrintStream(errBuffer, true, UTF_8.name());
        out = System.out;
        err = System.err;
        System.setOut(outWriter);
        System.setErr(errWriter);
        testInputDirForCommandLine = testInputDir.toAbsolutePath().toString();
        tempOutputDirForCommandLine = tempOutputDir.toAbsolutePath().toString();
    }

    @After
    public void tearDown() throws Exception {
        System.setOut(new PrintStream(out, true, UTF_8.name()));
        System.setErr(new PrintStream(err, true, UTF_8.name()));
        //TODO: refactor to use our deleteDirectory with straight path
        FileUtils.deleteDirectory(tempOutputDir.toFile());
    }

    @Test
    public void testSimplestBatchIntegration() throws Exception {
        String[] params = {testInputDirForCommandLine,
                tempOutputDirForCommandLine};
        TikaCLI.main(params);

        assertFileExists(tempOutputDir.resolve("bad_xml.xml.xml"));
        assertFileExists(tempOutputDir.resolve("coffee.xls.xml"));
    }

    @Test
    public void testBasicBatchIntegration() throws Exception {
        String[] params = {"-i", testInputDirForCommandLine,
                "-o", tempOutputDirForCommandLine,
                "-numConsumers", "2"
        };
        TikaCLI.main(params);

        assertFileExists(tempOutputDir.resolve("bad_xml.xml.xml"));
        assertFileExists(tempOutputDir.resolve("coffee.xls.xml"));
    }

    @Test
    public void testJsonRecursiveBatchIntegration() throws Exception {
        String[] params = {"-i", testInputDirForCommandLine,
                "-o", tempOutputDirForCommandLine,
                "-numConsumers", "10",
                "-J", //recursive Json
                "-t" //plain text in content
        };
        TikaCLI.main(params);

        Path jsonFile = tempOutputDir.resolve("test_recursive_embedded.docx.json");
        try (Reader reader = Files.newBufferedReader(jsonFile, UTF_8)) {
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(12, metadataList.size());
            assertTrue(metadataList.get(6).get(RecursiveParserWrapper.TIKA_CONTENT).contains("human events"));
        }
    }

    @Test
    public void testProcessLogFileConfig() throws Exception {
        String[] params = {"-i", testInputDirForCommandLine,
                "-o", tempOutputDirForCommandLine,
                "-numConsumers", "2",
                "-JDlog4j.configuration=log4j_batch_process_test.properties"};
        TikaCLI.main(params);

        assertFileExists(tempOutputDir.resolve("bad_xml.xml.xml"));
        assertFileExists(tempOutputDir.resolve("coffee.xls.xml"));
        String sysOutString = new String(outBuffer.toByteArray(), UTF_8);
        assertTrue(sysOutString.contains("MY_CUSTOM_LOG_CONFIG"));
    }

    @Test
    public void testDigester() throws Exception {
/*
        try {
            String[] params = {"-i", escape(testDataFile.getAbsolutePath()),
                    "-o", escape(tempOutputDir.getAbsolutePath()),
                    "-numConsumers", "10",
                    "-J", //recursive Json
                    "-t" //plain text in content
            };
            TikaCLI.main(params);
            reader = new InputStreamReader(
                    new FileInputStream(new File(tempOutputDir, "test_recursive_embedded.docx.json")), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(12, metadataList.size());
            assertEquals("59f626e09a8c16ab6dbc2800c685f772", metadataList.get(0).get("X-TIKA:digest:MD5"));
            assertEquals("22e6e91f408d018417cd452d6de3dede", metadataList.get(5).get("X-TIKA:digest:MD5"));
        } finally {
            IOUtils.closeQuietly(reader);
        }
*/
        String[] params = {"-i", testInputDirForCommandLine,
                "-o", tempOutputDirForCommandLine,
                "-numConsumers", "10",
                "-J", //recursive Json
                "-t", //plain text in content
                "-digest", "sha512"
        };
        TikaCLI.main(params);
        Path jsonFile = tempOutputDir.resolve("test_recursive_embedded.docx.json");
        try (Reader reader = Files.newBufferedReader(jsonFile, UTF_8)) {

            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(12, metadataList.size());
            assertNotNull(metadataList.get(0).get("X-TIKA:digest:SHA512"));
            assertTrue(metadataList.get(0).get("X-TIKA:digest:SHA512").startsWith("ee46d973ee1852c01858"));
        }
    }

    private void assertFileExists(Path path) {
        assertTrue("File doesn't exist: "+path.toAbsolutePath(),
                Files.isRegularFile(path));
    }


}
