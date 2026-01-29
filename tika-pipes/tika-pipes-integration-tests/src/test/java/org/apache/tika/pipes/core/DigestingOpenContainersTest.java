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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.extractor.RUnpackExtractorFactory;

public class DigestingOpenContainersTest extends TikaTest {

    @Test
    public void testDigestingOpenContainers() throws Exception {
        //TIKA-4533 -- this tests both that a very large embedded OLE doc doesn't cause a zip bomb
        //exception AND that the sha for the embedded OLE doc is not the sha for a zero-byte file
        String expectedSha = "bbc2057a1ff8fe859a296d2fbb493fc0c3e5796749ba72507c0e13f7a3d81f78";
        TikaLoader loader = getLoader("tika-4533.json");
        AutoDetectParser autoDetectParser = (AutoDetectParser) loader.loadAutoDetectParser();
        ParseContext parseContext = loader.loadParseContext();
        //this models what happens in tika-pipes
        if (parseContext.get(EmbeddedDocumentExtractorFactory.class) == null) {
            parseContext.set(EmbeddedDocumentExtractorFactory.class, new RUnpackExtractorFactory());
        }
        List<Metadata> metadataList = getRecursiveMetadata("testLargeOLEDoc.doc",
                autoDetectParser, parseContext);
        assertEquals(expectedSha, metadataList.get(2).get("X-TIKA:digest:SHA256"));
        assertNull(metadataList.get(2).get(TikaCoreProperties.EMBEDDED_EXCEPTION));
        assertEquals(2049290L, Long.parseLong(metadataList.get(2).get(Metadata.CONTENT_LENGTH)));
    }

    private TikaLoader getLoader(String config) {
        try {
            return TikaLoader.load(Paths.get(getClass()
                    .getResource("/configs/" + config)
                    .toURI()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
