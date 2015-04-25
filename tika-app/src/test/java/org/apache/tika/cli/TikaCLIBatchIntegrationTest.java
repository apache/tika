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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TikaCLIBatchIntegrationTest {

    private File testDataFile = new File("src/test/resources/test-data");

    private File tempDir;
    private OutputStream out = null;
    private OutputStream err = null;
    private ByteArrayOutputStream outBuffer = null;

    @Before
    public void setup() throws Exception {
        tempDir = File.createTempFile("tika-cli-test-batch-", "");
        tempDir.delete();
        tempDir.mkdir();
        outBuffer = new ByteArrayOutputStream();
        PrintStream outWriter = new PrintStream(outBuffer, true, IOUtils.UTF_8.name());
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream errWriter = new PrintStream(errBuffer, true, IOUtils.UTF_8.name());
        out = System.out;
        err = System.err;
        System.setOut(outWriter);
        System.setErr(errWriter);
    }

    @After
    public void tearDown() throws Exception {
        System.setOut(new PrintStream(out, true, IOUtils.UTF_8.name()));
        System.setErr(new PrintStream(err, true, IOUtils.UTF_8.name()));
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void testSimplestBatchIntegration() throws Exception {
        String[] params = {escape(testDataFile.getAbsolutePath()),
                escape(tempDir.getAbsolutePath())};
        TikaCLI.main(params);

        assertTrue("bad_xml.xml.xml", new File(tempDir, "bad_xml.xml.xml").isFile());
        assertTrue("coffee.xls.xml", new File(tempDir, "coffee.xls.xml").exists());
    }

    @Test
    public void testBasicBatchIntegration() throws Exception {
        String[] params = {"-i", escape(testDataFile.getAbsolutePath()),
                "-o", escape(tempDir.getAbsolutePath()),
                "-numConsumers", "2"
        };
        TikaCLI.main(params);

        assertTrue("bad_xml.xml.xml", new File(tempDir, "bad_xml.xml.xml").isFile());
        assertTrue("coffee.xls.xml", new File(tempDir, "coffee.xls.xml").exists());
    }

    @Test
    public void testJsonRecursiveBatchIntegration() throws Exception {
        Reader reader = null;
        try {
            String[] params = {"-i", escape(testDataFile.getAbsolutePath()),
                    "-o", escape(tempDir.getAbsolutePath()),
                    "-numConsumers", "10",
                    "-J", //recursive Json
                    "-t" //plain text in content
            };
            TikaCLI.main(params);
            reader = new InputStreamReader(
                    new FileInputStream(new File(tempDir, "test_recursive_embedded.docx.json")), IOUtils.UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(12, metadataList.size());
            assertTrue(metadataList.get(6).get(RecursiveParserWrapper.TIKA_CONTENT).contains("human events"));
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    @Test
    public void testProcessLogFileConfig() throws Exception {
        String[] params = {"-i", escape(testDataFile.getAbsolutePath()),
                "-o", escape(tempDir.getAbsolutePath()),
                "-numConsumers", "2",
                "-JDlog4j.configuration=log4j_batch_process_test.properties"};
        TikaCLI.main(params);

        assertTrue("bad_xml.xml.xml", new File(tempDir, "bad_xml.xml.xml").isFile());
        assertTrue("coffee.xls.xml", new File(tempDir, "coffee.xls.xml").exists());
        String sysOutString = new String(outBuffer.toByteArray(), IOUtils.UTF_8);
        assertTrue(sysOutString.contains("MY_CUSTOM_LOG_CONFIG"));
    }

    public static String escape(String path) {
        if (path.indexOf(' ') > -1) {
            return '"' + path + '"';
        }
        return path;
    }

}
