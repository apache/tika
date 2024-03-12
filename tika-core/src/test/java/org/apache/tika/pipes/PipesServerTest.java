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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;

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
        Path tikaConfig = tmp.resolve("tika-config.xml");
        String xml = IOUtils.toString(
                PipesServerTest.class.getResourceAsStream("TIKA-3941.xml"), StandardCharsets.UTF_8);
        xml = xml.replace("BASE_PATH", tmp.toAbsolutePath().toString());
        Files.write(tikaConfig, xml.getBytes(StandardCharsets.UTF_8));

        Files.copy(PipesServerTest.class.getResourceAsStream("/test-documents/mock_times.xml"),
                tmp.resolve("mock.xml"));

        PipesServer pipesServer = new PipesServer(tikaConfig,
                new UnsynchronizedByteArrayInputStream(new byte[0]),
                new PrintStream(new UnsynchronizedByteArrayOutputStream(), true,
                        StandardCharsets.UTF_8.name()),
                -1, 30000, 30000);

        pipesServer.initializeResources();

        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fs", "mock.xml"),
                new EmitKey("", ""));
        Fetcher fetcher = FetcherManager.load(tikaConfig).getFetcher();
        PipesServer.MetadataListAndEmbeddedBytes
                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals("5f3b924303e960ce35d7f705e91d3018dd110a9c3cef0546a91fe013d6dad6fd",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }

    @Test
    public void testEmbeddedStreamEmitter() throws Exception {
        Path tmp = Paths.get("/home/tallison/Desktop/tmp");
        if (Files.isDirectory(tmp)) {
            FileUtils.deleteDirectory(tmp.toFile());
        }
        Files.createDirectories(tmp);
        Path tikaConfig = tmp.resolve("tika-config.xml");

        String xml = IOUtils.toString(
                PipesServerTest.class.getResourceAsStream("TIKA-4207.xml"),
                StandardCharsets.UTF_8);
        xml = xml.replace("BASE_PATH", tmp.toAbsolutePath().toString());
        Files.write(tikaConfig, xml.getBytes(StandardCharsets.UTF_8));

        Files.copy(PipesServerTest.class.getResourceAsStream("/test-documents/basic_embedded.xml"),
                tmp.resolve("mock.xml"));

        PipesServer pipesServer = new PipesServer(tikaConfig,
                new UnsynchronizedByteArrayInputStream(new byte[0]),
                new PrintStream(new UnsynchronizedByteArrayOutputStream(), true,
                        StandardCharsets.UTF_8.name()),
                -1, 30000, 30000);

        pipesServer.initializeResources();
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig =
                new EmbeddedDocumentBytesConfig(true);
        embeddedDocumentBytesConfig.setIncludeOriginal(true);

        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fs", "mock.xml"),
                new EmitKey("", ""), new Metadata(),
                HandlerConfig.DEFAULT_HANDLER_CONFIG, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT,
                embeddedDocumentBytesConfig);
        Fetcher fetcher = FetcherManager.load(tikaConfig).getFetcher();
        PipesServer.MetadataListAndEmbeddedBytes
                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals(2, parseData.metadataList.size());

        byte[] bytes0 = parseData.getEmbeddedDocumentByteStore().getDocument(0);
        byte[] bytes1 = parseData.getEmbeddedDocumentByteStore().getDocument(1);

        assertContains("is to trigger mock on the embedded",
                new String(bytes0, StandardCharsets.UTF_8));

        assertContains("embeddedAuthor</metadata>",
                new String(bytes1, StandardCharsets.UTF_8));
        assertEquals("fdaa937c96d1ed010b8d307ccddf9d11c3b48db732a8771eaafe99d59e076d0a",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }
}
