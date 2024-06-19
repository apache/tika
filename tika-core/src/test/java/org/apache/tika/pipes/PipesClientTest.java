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
package org.apache.tika.pipes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

class PipesClientTest {
    String fetcherName = "fs";
    String testPdfFile = "testOverlappingText.pdf";

    private PipesClient pipesClient;

    @BeforeEach
    public void init()
            throws TikaConfigException, IOException, ParserConfigurationException, SAXException {
        Path tikaConfigPath =
                Paths.get("src", "test", "resources", "org", "apache", "tika", "pipes",
                        "tika-sample-config.xml");
        PipesConfig pipesConfig = PipesConfig.load(tikaConfigPath);
        pipesClient = new PipesClient(pipesConfig);
    }

    @Test
    void process() throws IOException, InterruptedException {
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testPdfFile, new FetchKey(fetcherName, testPdfFile),
                        new EmitKey(), new Metadata(), new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.getEmitData().getMetadataList());
        Assertions.assertEquals(1, pipesResult.getEmitData().getMetadataList().size());
        Metadata metadata = pipesResult.getEmitData().getMetadataList().get(0);
        Assertions.assertEquals("testOverlappingText.pdf", metadata.get("resourceName"));
    }
}
