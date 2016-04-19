package org.apache.tika.batch.fs;

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.ParallelFileProcessingResult;
import org.junit.Test;

public class HandlerBuilderTest extends FSBatchTestBase {

    @Test
    public void testXML() throws Exception {

        Path outputDir = getNewOutputDir("handler-xml-");
        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("basicHandlerType", "xml");

        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);
        Path outputFile = outputDir.resolve("test0.xml.xml");
        String resultString = readFileToString(outputFile, UTF_8);
        assertTrue(resultString.contains("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));
        assertTrue(resultString.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(resultString.contains("This is tika-batch's first test file"));
    }


    @Test
    public void testHTML() throws Exception {
        Path outputDir = getNewOutputDir("handler-html-");

        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("basicHandlerType", "html");
        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);
        Path outputFile = outputDir.resolve("test0.xml.html");
        String resultString = readFileToString(outputFile, UTF_8);
        assertTrue(resultString.contains("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));
        assertFalse(resultString.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(resultString.contains("This is tika-batch's first test file"));
    }

    @Test
    public void testText() throws Exception {
        Path outputDir = getNewOutputDir("handler-txt-");

        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("basicHandlerType", "txt");

        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);
        Path outputFile = outputDir.resolve("test0.xml.txt");
        String resultString = readFileToString(outputFile, UTF_8);
        assertFalse(resultString.contains("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));
        assertFalse(resultString.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(resultString.contains("This is tika-batch's first test file"));
    }


    @Test
    public void testXMLWithWriteLimit() throws Exception {
        Path outputDir = getNewOutputDir("handler-xml-write-limit-");

        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("writeLimit", "5");

        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);

        Path outputFile = outputDir.resolve("test0.xml.xml");
        String resultString = readFileToString(outputFile, UTF_8);
        //this is not ideal. How can we change handlers to writeout whatever
        //they've gotten so far, up to the writeLimit?
        assertTrue(resultString.equals(""));
    }

    @Test
    public void testRecursiveParserWrapper() throws Exception {
        Path outputDir = getNewOutputDir("handler-recursive-parser");

        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("basicHandlerType", "txt");
        args.put("recursiveParserWrapper", "true");

        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);
        Path outputFile = outputDir.resolve("test0.xml.json");
        String resultString = readFileToString(outputFile, UTF_8);
        assertTrue(resultString.contains("\"author\":\"Nikolai Lobachevsky\""));
        assertTrue(resultString.contains("tika-batch\\u0027s first test file"));
    }


}
