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


import static org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig.DEFAULT_HANDLER_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * This should be in tika-core, but we want to avoid a dependency mess with tika-serialization
 */
public class AsyncProcessorTest extends TikaTest {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessorTest.class);


    final static String JSON_TEMPLATE_TEST = """
                {
                  "fsf": {
                    "file-system-fetcher": {
                      "basePath": "FETCHER_BASE_PATH",
                      "extractFileSystemMetadata": false
                    }
                  },
                  "fse-json": {
                    "file-system-emitter": {
                      "basePath": "JSON_EMITTER_BASE_PATH",
                      "fileExtension": "",
                      "onExists": "EXCEPTION"
                    }
                  },
                  "fse-bytes": {
                    "file-system-emitter": {
                      "basePath": "BYTES_EMITTER_BASE_PATH",
                      "fileExtension": "",
                      "onExists": "EXCEPTION"
                    }
                  },
                  "pluginsPaths": "PLUGINS_PATHS"
                }
            """;

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
    public void setUp() throws IOException {
        inputDir = basedir.resolve("input");

        outputDir = basedir.resolve("output");
        jsonOutputDir = outputDir.resolve("json");
        bytesOutputDir = outputDir.resolve("bytes");


        configDir = basedir.resolve("config");

        Files.createDirectories(basedir);
        Files.createDirectories(configDir);
        Files.createDirectories(inputDir);

        Path pluginsDir = Paths.get("target/plugins");
        if (! Files.isDirectory(pluginsDir)) {
            LOG.warn("CAN'T FIND PLUGINS DIR. pwd={}", Paths.get("").toAbsolutePath().toString());
        }

        tikaConfigPath = configDir.resolve("tika-config.xml");
        Files.copy(AsyncProcessorTest.class.getResourceAsStream("/configs/tika-config-default.xml"), tikaConfigPath);
        Path pipesConfig = configDir.resolve("tika-pipes.json");
        String jsonTemp = JSON_TEMPLATE_TEST
                .replace("FETCHER_BASE_PATH", inputDir.toAbsolutePath().toString())
                .replace("JSON_EMITTER_BASE_PATH", jsonOutputDir.toAbsolutePath().toString())
                .replace("BYTES_EMITTER_BASE_PATH", bytesOutputDir.toAbsolutePath().toString())
                .replace("PLUGINS_PATHS", pluginsDir.toAbsolutePath().toString());


        Files.writeString(pipesConfig, jsonTemp, StandardCharsets.UTF_8);

        Path mock = inputDir.resolve("mock.xml");
        try (OutputStream os = Files.newOutputStream(mock)) {
            IOUtils.copy(getClass().getResourceAsStream("/test-documents/basic_embedded.xml"), os);
        }
    }

    @Test
    public void testRecursiveUnpacking() throws Exception {
//        TikaAsyncCLI cli = new TikaAsyncCLI();
        //      cli.main(new String[]{ configDir.resolve("tika-config.xml").toAbsolutePath().toString()});
        AsyncProcessor processor = new AsyncProcessor(tikaConfigPath, configDir.resolve("tika-pipes.json"));

        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = new EmbeddedDocumentBytesConfig(true);
        embeddedDocumentBytesConfig.setIncludeOriginal(true);
        embeddedDocumentBytesConfig.setEmitter("fse-bytes");
        embeddedDocumentBytesConfig.setSuffixStrategy(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.NONE);
        embeddedDocumentBytesConfig.setEmbeddedIdPrefix("-");
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, DEFAULT_HANDLER_CONFIG);
        parseContext.set(EmbeddedDocumentBytesConfig.class, embeddedDocumentBytesConfig);
        FetchEmitTuple t =
                new FetchEmitTuple("myId-1", new FetchKey("fsf", "mock.xml"),
                        new EmitKey("fse-json", "emit-1"), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(t, 1000);

        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        //TODO clean this up
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        String container = Files.readString(bytesOutputDir.resolve("emit-1-embed/emit-1-0"));
        assertContains("\"dc:creator\">Nikolai Lobachevsky", container);

        String xmlEmbedded = Files.readString(bytesOutputDir.resolve("emit-1-embed/emit-1-1"));
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
}
