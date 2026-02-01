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
package org.apache.tika.async.cli;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.TikaTest;
import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * This should be in tika-core, but we want to avoid a dependency mess with tika-serialization
 */
public class AsyncProcessorTest extends TikaTest {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessorTest.class);

    //TODO -- integrate json pipes iterator and run with AyncProcessor.main
    @TempDir
    private Path basedir;
    private Path inputDir;

    private Path outputDir;
    private Path jsonOutputDir;
    private Path bytesOutputDir;

    private Path configDir;

    private Path tikaConfigPath;

    @BeforeEach
    public void setUp() throws Exception {
        inputDir = basedir.resolve("input");

        outputDir = basedir.resolve("output");
        jsonOutputDir = outputDir.resolve("json");
        bytesOutputDir = outputDir.resolve("bytes");


        configDir = basedir.resolve("config");

        Files.createDirectories(basedir);
        Files.createDirectories(configDir);
        Files.createDirectories(inputDir);

        Path pluginsDir = Paths.get("target/plugins");
        if (!Files.isDirectory(pluginsDir)) {
            LOG.warn("CAN'T FIND PLUGINS DIR. pwd={}", Paths.get("").toAbsolutePath().toString());
        }

        tikaConfigPath = configDir.resolve("tika-config.json");

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("FETCHER_BASE_PATH", inputDir);
        replacements.put("JSON_EMITTER_BASE_PATH", jsonOutputDir);
        replacements.put("BYTES_EMITTER_BASE_PATH", bytesOutputDir);
        replacements.put("PLUGIN_ROOTS", pluginsDir);

        JsonConfigHelper.writeConfigFromResource("/configs/config-template.json",
                AsyncProcessorTest.class, replacements, tikaConfigPath);

        Path mock = inputDir.resolve("mock.xml");
        try (OutputStream os = Files.newOutputStream(mock)) {
            IOUtils.copy(getClass().getResourceAsStream("/test-documents/basic_embedded.xml"), os);
        }
    }

    @Test
    public void testRecursiveUnpacking() throws Exception {
        AsyncProcessor processor = AsyncProcessor.load(configDir.resolve("tika-config.json"));

        UnpackConfig embeddedDocumentBytesConfig = new UnpackConfig();
        embeddedDocumentBytesConfig.setIncludeOriginal(true);
        embeddedDocumentBytesConfig.setEmitter("fse-bytes");
        embeddedDocumentBytesConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.NONE);
        embeddedDocumentBytesConfig.setEmbeddedIdPrefix("-");
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        parseContext.set(UnpackConfig.class, embeddedDocumentBytesConfig);
        FetchEmitTuple t =
                new FetchEmitTuple("myId-1", new FetchKey("fsf", "mock.xml"),
                        new EmitKey("fse-json", "emit-1"), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(t, 1000);

        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        String container = Files.readString(bytesOutputDir.resolve("emit-1-embed/0"));
        assertContains("\"dc:creator\">Nikolai Lobachevsky", container);

        String xmlEmbedded = Files.readString(bytesOutputDir.resolve("emit-1-embed/1"));
        assertContains("name=\"dc:creator\"", xmlEmbedded);
        assertContains(">embeddedAuthor</metadata>", xmlEmbedded);

        List<Metadata> metadataList;
        try (BufferedReader reader = Files.newBufferedReader(jsonOutputDir.resolve("emit-1"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(2, metadataList.size());
        assertContains("main_content", metadataList
                .get(0)
                .get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("some_embedded_content", metadataList
                .get(1)
                .get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testStopsOnApplicationError() throws Exception {
        AsyncProcessor processor = AsyncProcessor.load(configDir.resolve("tika-config.json"));

        ParseContext parseContext = new ParseContext();
        FetchEmitTuple badTuple = new FetchEmitTuple(
                "bad-tuple-1",
                new FetchKey("non-existent-fetcher", "some-file.txt"),
                new EmitKey("fse-json", "emit-bad"),
                new Metadata(),
                parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(badTuple, 1000);

        int maxWaitMs = 30000;
        int waited = 0;
        while (!processor.hasApplicationError() && waited < maxWaitMs) {
            Thread.sleep(100);
            waited += 100;
        }

        assertTrue(processor.hasApplicationError(),
                "AsyncProcessor should detect application error from bad fetcher");

        FetchEmitTuple anotherTuple = new FetchEmitTuple(
                "another-tuple",
                new FetchKey("fsf", "mock.xml"),
                new EmitKey("fse-json", "emit-another"),
                new Metadata(),
                parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        assertThrows(PipesException.class, () -> {
            processor.offer(anotherTuple, 1000);
        }, "Should throw PipesException when offering after application error");

        processor.close();
    }
}
