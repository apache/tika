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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.emitter.EmitKey;
import org.apache.tika.pipes.core.fetcher.FetchKey;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;

public class PassbackFilterTest {

    private Path tmpDir;
    String fetcherName = "fs";
    String testPdfFile = "testOverlappingText.pdf";

    private PipesClient pipesClient;

    @BeforeEach
    public void init() throws TikaConfigException, IOException, ParserConfigurationException, SAXException {
        Path tikaConfigTemplate = Paths.get("src", "test", "resources", "org", "apache", "tika", "pipes", "core", "tika-emit-config.xml");
        tmpDir = Files.createTempDirectory("tika-pipes");
        Path tikaConfigPath = Files.createTempFile(tmpDir, "tika-pipes-", ".xml");
        String template = Files.readString(tikaConfigTemplate, StandardCharsets.UTF_8);
        template = template.replace("EMITTER_BASE_PATH", tmpDir
                .toAbsolutePath()
                .toString());
        Files.writeString(tikaConfigPath, template);
        PipesConfig pipesConfig = PipesConfig.load(tikaConfigPath);
        pipesClient = new PipesClient(pipesConfig);
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir.toFile());
    }

    @Test
    public void testPassbackFilter() throws Exception {
        String emitFileBase = "blah";
        ParseContext parseContext = new ParseContext();
        parseContext.set(PassbackFilter.class, new MyPassbackFilter());
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testPdfFile, new FetchKey(fetcherName, testPdfFile), new EmitKey("fs", emitFileBase), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        assertEquals(PipesResult.STATUS.EMIT_SUCCESS_PASSBACK, pipesResult.getStatus());
        Assertions.assertNotNull(pipesResult
                .getEmitData()
                .getMetadataList());
        assertEquals(1, pipesResult
                .getEmitData()
                .getMetadataList()
                .size());
        Metadata metadata = pipesResult
                .getEmitData()
                .getMetadataList()
                .get(0);
        assertEquals("TESTOVERLAPPINGTEXT.PDF", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertNull(metadata.get(Metadata.CONTENT_TYPE));
        assertNull(metadata.get(Metadata.CONTENT_LENGTH));
        assertEquals(1, metadata.names().length);

        List<Metadata> metadataList = JsonMetadataList.fromJson(Files.newBufferedReader(tmpDir.resolve(emitFileBase + ".json"), StandardCharsets.UTF_8));
        assertEquals(1, metadataList.size());
        assertEquals("application/pdf", metadataList
                .get(0)
                .get(Metadata.CONTENT_TYPE));
        assertEquals("899", metadataList
                .get(0)
                .get(Metadata.CONTENT_LENGTH));
    }

    private static class MyPassbackFilter extends PassbackFilter {
        @Override
        public List<Metadata> filter(List<Metadata> metadataList) throws TikaException {
            List<Metadata> ret = new ArrayList<>();
            for (Metadata m : metadataList) {
                String val = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                if (!StringUtils.isBlank(val)) {
                    Metadata retM = new Metadata();
                    retM.add(TikaCoreProperties.RESOURCE_NAME_KEY, val.toUpperCase(Locale.ROOT));
                    ret.add(retM);
                }
            }
            return ret;
        }
    }

}
