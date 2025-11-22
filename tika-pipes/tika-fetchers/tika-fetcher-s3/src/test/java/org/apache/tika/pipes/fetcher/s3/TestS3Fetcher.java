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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.ExtensionConfig;

@Disabled("write actual unit tests")
public class TestS3Fetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FETCH_STRING = "";
    private final Path outputFile = Paths.get("");
    private final String region = "us-east-1";
    private final String profile = "";

    @Test
    public void testBasic() throws Exception {
        ObjectNode jsonConfig = OBJECT_MAPPER.createObjectNode();
        jsonConfig.put("region", region);
        jsonConfig.put("profile", profile);
        jsonConfig.put("credentialsProvider", "profile");

        ExtensionConfig extensionConfig = new ExtensionConfig("test-s3-fetcher", "s3-fetcher",
                OBJECT_MAPPER.writeValueAsString(jsonConfig));
        S3Fetcher fetcher = S3Fetcher.build(extensionConfig);

        Metadata metadata = new Metadata();
        try (InputStream is = fetcher.fetch(FETCH_STRING, metadata, new ParseContext())) {
            Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
