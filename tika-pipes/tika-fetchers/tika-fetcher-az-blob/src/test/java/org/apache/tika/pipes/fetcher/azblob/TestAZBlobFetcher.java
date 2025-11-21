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
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.serialization.JsonMetadataList;

@Disabled("write actual unit tests")
public class TestAZBlobFetcher extends TikaTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FETCH_STRING = "something-or-other/test-out.json";

    @Test
    public void testConfig() throws Exception {
        ObjectNode jsonConfig = OBJECT_MAPPER.createObjectNode();
        jsonConfig.put("endpoint", "https://myaccount.blob.core.windows.net");
        jsonConfig.put("container", "my-container");
        jsonConfig.put("sasToken", "my-sas-token");

        ExtensionConfig extensionConfig = new ExtensionConfig("test-az-blob-fetcher", "az-blob-fetcher", jsonConfig);
        AZBlobFetcher fetcher = AZBlobFetcher.build(extensionConfig);

        List<Metadata> metadataList = null;
        try (Reader reader = new BufferedReader(new InputStreamReader(fetcher.fetch(FETCH_STRING, new Metadata(), new ParseContext()), StandardCharsets.UTF_8))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        debug(metadataList);
    }
}
