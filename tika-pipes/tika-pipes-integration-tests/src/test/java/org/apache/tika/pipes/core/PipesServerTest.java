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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.core.extractor.BasicEmbeddedDocumentBytesHandler;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.plugins.TikaPluginManager;

public class PipesServerTest extends TikaTest {

    /**
     * This test is useful for stepping through the debugger on PipesServer
     * without having to attach the debugger to the forked process.
     *
     * @param tmp
     * @throws Exception
     */
    @Test
    public void testBasic(@TempDir Path tmp) throws Exception {
        String testDoc = "mock_times.xml";
        Path tikaConfig = PluginsTestHelper.getFileSystemFetcherConfig(tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        PipesServer pipesServer = new PipesServer(tikaConfig,
                UnsynchronizedByteArrayInputStream.builder().setByteArray(new byte[0]).get(),
                new PrintStream(UnsynchronizedByteArrayOutputStream.builder().get(), true,
                        StandardCharsets.UTF_8.name()),
                -1, 30000, 30000);

        pipesServer.initializeResources();

        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fsf", testDoc),
                new EmitKey("", ""));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfig);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        Fetcher fetcher = FetcherManager.load(pluginManager, tikaJsonConfig).getFetcher();
        PipesServer.MetadataListAndEmbeddedBytes
                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals("5f3b924303e960ce35d7f705e91d3018dd110a9c3cef0546a91fe013d6dad6fd",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }

    @Test
    public void testEmbeddedStreamEmitter(@TempDir Path tmp) throws Exception {

        String testDoc = "basic_embedded.xml";
        Path tikaConfig = PluginsTestHelper.getFileSystemFetcherConfig(tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);


        PipesServer pipesServer = new PipesServer(tikaConfig,
                UnsynchronizedByteArrayInputStream.builder().setByteArray(new byte[0]).get(),
                new PrintStream(UnsynchronizedByteArrayOutputStream.builder().get(), true,
                        StandardCharsets.UTF_8.name()),
                -1, 30000, 30000);

        pipesServer.initializeResources();
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig =
                new EmbeddedDocumentBytesConfig(true);
        embeddedDocumentBytesConfig.setIncludeOriginal(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, PipesIteratorBaseConfig.DEFAULT_HANDLER_CONFIG);
        parseContext.set(EmbeddedDocumentBytesConfig.class, embeddedDocumentBytesConfig);
        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fs", testDoc),
                new EmitKey("", ""), new Metadata(), parseContext);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfig);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        Fetcher fetcher = FetcherManager.load(pluginManager, tikaJsonConfig).getFetcher();
        PipesServer.MetadataListAndEmbeddedBytes
                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals(2, parseData.metadataList.size());

        byte[] bytes0 =
                IOUtils.toByteArray(
                        ((BasicEmbeddedDocumentBytesHandler)parseData.getEmbeddedDocumentBytesHandler())
                        .getDocument(0));
        byte[] bytes1 =
                IOUtils.toByteArray(
                        ((BasicEmbeddedDocumentBytesHandler)parseData.getEmbeddedDocumentBytesHandler())
                                .getDocument(1));

        assertContains("is to trigger mock on the embedded",
                new String(bytes0, StandardCharsets.UTF_8));

        assertContains("embeddedAuthor</metadata>",
                new String(bytes1, StandardCharsets.UTF_8));
        assertEquals("fdaa937c96d1ed010b8d307ccddf9d11c3b48db732a8771eaafe99d59e076d0a",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }

    @Test
    public void testEmbeddedStreamEmitterLimitBytes(@TempDir Path tmp) throws Exception {
        String testDoc = "basic_embedded.xml";
        Path pipesConfig = PluginsTestHelper.getFileSystemFetcherConfig("tika-config-truncate.json", tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        PipesServer pipesServer = new PipesServer(pipesConfig,
                UnsynchronizedByteArrayInputStream.builder().setByteArray(new byte[0]).get(),
                new PrintStream(UnsynchronizedByteArrayOutputStream.builder().get(), true,
                        StandardCharsets.UTF_8.name()),
                -1, 30000, 30000);

        pipesServer.initializeResources();
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig =
                new EmbeddedDocumentBytesConfig(true);
        embeddedDocumentBytesConfig.setIncludeOriginal(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, PipesIteratorBaseConfig.DEFAULT_HANDLER_CONFIG);
        parseContext.set(EmbeddedDocumentBytesConfig.class, embeddedDocumentBytesConfig);
        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fs", testDoc),
                new EmitKey("", ""), new Metadata(), parseContext);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(pipesConfig);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        Fetcher fetcher = FetcherManager.load(pluginManager, tikaJsonConfig).getFetcher();
        PipesServer.MetadataListAndEmbeddedBytes
                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals(2, parseData.metadataList.size());

        byte[] bytes0 =
                IOUtils.toByteArray(
                        ((BasicEmbeddedDocumentBytesHandler)parseData.getEmbeddedDocumentBytesHandler())
                                .getDocument(0));
        byte[] bytes1 =
                IOUtils.toByteArray(
                        ((BasicEmbeddedDocumentBytesHandler)parseData.getEmbeddedDocumentBytesHandler())
                                .getDocument(1));

        assertContains("is to trigger mock on the embedded",
                new String(bytes0, StandardCharsets.UTF_8));

        assertEquals(10, bytes1.length);
        assertEquals("fdaa937c96d1ed010b8d307ccddf9d11c3b48db732a8771eaafe99d59e076d0a",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }
}
