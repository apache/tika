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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;

@Disabled("write actual unit tests")
public class TestS3Fetcher {
    private static final String FETCH_STRING = "";
    private final Path outputFile = Paths.get("");
    private final String region = "us-east-1";
    private final String profile = "";

    @Test
    public void testBasic() throws Exception {
        S3Fetcher fetcher = new S3Fetcher();
        fetcher.setProfile(profile);
        fetcher.setRegion(region);
        fetcher.initialize(Collections.EMPTY_MAP);

        Metadata metadata = new Metadata();
        try (InputStream is = fetcher.fetch(FETCH_STRING, metadata)) {
            Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    public void testConfig() throws Exception {
        FetcherManager fetcherManager = FetcherManager.load(
                Paths.get(this.getClass().getResource("/tika-config-s3.xml").toURI()));
        Fetcher fetcher = fetcherManager.getFetcher("s3");
        Metadata metadata = new Metadata();
        try (InputStream is = fetcher.fetch(FETCH_STRING, metadata)) {
            Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
