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
package org.apache.tika.pipes.fetcher.azblob;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;

@Disabled("write actual unit tests")
public class TestAZBlobFetcher extends TikaTest {

    private static final String FETCH_STRING = "something-or-other/test-out.json";

    @Test
    public void testConfig() throws Exception {
        FetcherManager fetcherManager = FetcherManager.load(
                Paths.get(this.getClass().getResource("/tika-config-az-blob.xml").toURI()));
        Fetcher fetcher = fetcherManager.getFetcher("az-blob");
        List<Metadata> metadataList = null;
        try (Reader reader = new BufferedReader(new InputStreamReader(
                fetcher.fetch(FETCH_STRING, new Metadata(), new ParseContext()), StandardCharsets.UTF_8))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        debug(metadataList);
    }
}
