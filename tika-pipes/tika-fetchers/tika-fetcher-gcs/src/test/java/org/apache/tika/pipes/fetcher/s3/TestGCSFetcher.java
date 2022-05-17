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
package org.apache.tika.pipes.fetcher.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;

@Disabled("write actual unit tests")
public class TestGCSFetcher {

    private static final String FETCH_STRING = "testExtraSpaces.pdf";

    @TempDir
    private static Path TEMP_DIR;
    private static Path outputFile;

    @BeforeAll
    public static void setUp() throws Exception {
        outputFile = Files.createTempFile(TEMP_DIR, "tika-test", ".pdf");
    }


    @Test
    public void testConfig() throws Exception {
        FetcherManager fetcherManager = FetcherManager.load(
                Paths.get(this.getClass().getResource("/tika-config-gcs.xml").toURI()));
        Fetcher fetcher = fetcherManager.getFetcher("gcs");
        Metadata metadata = new Metadata();
        try (InputStream is = fetcher.fetch(FETCH_STRING, metadata)) {
            Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
        assertEquals(20743, Files.size(outputFile));
    }
}
