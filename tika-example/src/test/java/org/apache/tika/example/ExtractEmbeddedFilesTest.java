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

package org.apache.tika.example;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExtractEmbeddedFilesTest {

    ParsingExample parsingExample;
    Path outputPath;

    @Before
    public void setUp() throws IOException {
        parsingExample = new ParsingExample();
        outputPath = Files.createTempDirectory("tika-ext-emb-example-");
    }

    @After
    public void tearDown() throws IOException {
        //this does not act recursively, this only assumes single level directory
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(outputPath)) {
            for (Path entry : dirStream) {
                Files.delete(entry);
            }
        }
        Files.delete(outputPath);

    }

    @Test
    public void testExtractEmbeddedFiles() throws Exception {
        List<Path> extracted = parsingExample.extractEmbeddedDocumentsExample(outputPath);
        //this number should be bigger!!!
        assertEquals(2, extracted.size());
    }

}
