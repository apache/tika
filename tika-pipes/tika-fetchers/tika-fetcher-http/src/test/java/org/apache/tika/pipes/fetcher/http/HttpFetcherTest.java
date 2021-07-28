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
package org.apache.tika.pipes.fetcher.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.FetcherManager;

@Disabled("requires network connectivity")
public class HttpFetcherTest {

    @Test
    public void testRange() throws Exception {
        String url =
                "https://commoncrawl.s3.amazonaws.com/crawl-data/CC-MAIN-2020-45/segments/1603107869785.9/warc/CC-MAIN-20201020021700-20201020051700-00529.warc.gz";
        long start = 969596307;
        long end = start + 1408 - 1;
        Metadata metadata = new Metadata();
        HttpFetcher httpFetcher =
                (HttpFetcher) getFetcherManager("tika-config-http.xml")
                        .getFetcher("http");
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path tmpPath = tmp.createTempFile();
            try (InputStream is = httpFetcher.fetch(url, start, end, metadata)) {
                Files.copy(new GZIPInputStream(is), tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(2461, Files.size(tmpPath));
        }
    }


    FetcherManager getFetcherManager(String path) throws Exception {
        return FetcherManager.load(
                Paths.get(HttpFetcherTest.class.getResource("/" + path).toURI()));
    }


}
