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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.AttachmentCountingListFilter;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.async.AsyncConfig;

public class PipesClientTest {
    String fetcherName = "fsf";
    String testDoc = "testOverlappingText.pdf";


    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path pipesConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        PipesConfig pipesConfig = TikaLoader.load(pipesConfigPath).configs().load("async", AsyncConfig.class);
        return new PipesClient(pipesConfig);
    }

    @Test
    public void testBasic(@TempDir Path tmp) throws Exception {
        PipesClient pipesClient = init(tmp, testDoc);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDoc, new FetchKey(fetcherName, testDoc),
                        new EmitKey(), new Metadata(), new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals("testOverlappingText.pdf", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataFilter(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new MockUpperCaseFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);
        PipesClient pipesClient = init(tmp, testDoc);
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDoc, new FetchKey(fetcherName, testDoc),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals("TESTOVERLAPPINGTEXT.PDF", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataListFilter(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);

        String testFile = "mock-embedded.xml";

        PipesClient pipesClient = init(tmp, testFile);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(5, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals(4, Integer.parseInt(metadata.get("X-TIKA:attachment_count")));
    }

    @Test
    public void testTimeout(@TempDir Path tmp) throws Exception {
        //TODO -- add unit test for timeout > default
        //TODO -- figure out how to test pipes server timeout alone
        //I did both manually during development, but unit tests are better. :D
        ParseContext parseContext = new ParseContext();
        parseContext.set(TikaTaskTimeout.class, new TikaTaskTimeout(1000));
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);

        String testFile = "mock-timeout-10s.xml";
        PipesClient pipesClient = init(tmp, testFile);
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        assertEquals(PipesResults.TIMEOUT.status(), pipesResult.status());
    }
}
