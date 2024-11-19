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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TikaCLIAsyncTest {


    static final File TEST_DATA_FILE = new File("src/test/resources/test-data");

    /* Test members */
    private ByteArrayOutputStream outContent = null;
    private ByteArrayOutputStream errContent = null;
    private PrintStream stdout = null;
    private PrintStream stderr = null;

    private static Path ASYNC_CONFIG;
    @TempDir
    private static Path ASYNC_OUTPUT_DIR;

    @BeforeAll
    public static void setUpClass() throws Exception {
        ASYNC_CONFIG = Files.createTempFile(ASYNC_OUTPUT_DIR, "async-config-", ".xml");
        String xml = "<properties>" + "<async>" + "<numClients>3</numClients>" + "<tikaConfig>" + ASYNC_CONFIG.toAbsolutePath() + "</tikaConfig>" + "</async>" + "<fetchers>" +
                "<fetcher class=\"org.apache.tika.pipes.fetcher.fs.FileSystemFetcher\">" + "<name>fsf</name>" + "<basePath>" + TEST_DATA_FILE.getAbsolutePath() + "</basePath>" +
                "</fetcher>" + "</fetchers>" + "<emitters>" + "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" + "<name>fse</name>" + "<basePath>" +
                ASYNC_OUTPUT_DIR.toAbsolutePath() + "</basePath>" + "<prettyPrint>true</prettyPrint>" + "</emitter>" + "</emitters>" +
                "<pipesIterator class=\"org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator\">" + "<basePath>" + TEST_DATA_FILE.getAbsolutePath() + "</basePath>" +
                "<fetcherName>fsf</fetcherName>" + "<emitterName>fse</emitterName>" + "</pipesIterator>" + "</properties>";
        Files.write(ASYNC_CONFIG, xml.getBytes(UTF_8));
    }

    /**
     * reset resourcePrefix
     * save original System.out and System.err
     * clear outContent and errContent if they are not empty
     * set outContent and errContent as System.out and System.err
     */
    @BeforeEach
    public void setUp() throws Exception {
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


    @Test
    public void testAsync() throws Exception {
        String content = getParamOutContent("-a", "--config=" + ASYNC_CONFIG.toAbsolutePath());

        int json = 0;
        for (File f : ASYNC_OUTPUT_DIR
                .toFile()
                .listFiles()) {
            if (f
                    .getName()
                    .endsWith(".json")) {
                //check one file for pretty print
                if (f
                        .getName()
                        .equals("coffee.xls.json")) {
                    checkForPrettyPrint(f);
                }
                json++;
            }
        }
        assertEquals(18, json);
    }

    private void checkForPrettyPrint(File f) throws IOException {
        String json = FileUtils.readFileToString(f, UTF_8);
        int previous = json.indexOf("Content-Length");
        assertTrue(previous > -1);
        for (String k : new String[]{"Content-Type", "dc:creator", "dcterms:created", "dcterms:modified", "X-TIKA:content\""}) {
            int i = json.indexOf(k);
            assertTrue(i > -1, "should have found " + k);
            assertTrue(i > previous, "bad order: " + k + " at " + i + " not less than " + previous);
            previous = i;
        }
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
