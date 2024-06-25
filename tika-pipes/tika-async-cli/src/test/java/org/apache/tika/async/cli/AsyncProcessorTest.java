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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.async.AsyncProcessor;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * This should be in tika-core, but we want to avoid a dependency mess with tika-serialization
 */
public class AsyncProcessorTest extends TikaTest {
    //TODO -- integrate json pipes iterator and run with AyncProcessor.main
    @TempDir
    private Path basedir;
    private Path inputDir;

    private Path bytesDir;

    private Path jsonDir;

    private Path configDir;

    @BeforeEach
    public void setUp() throws IOException {
        inputDir = basedir.resolve("input");

        bytesDir = basedir.resolve("bytes");

        jsonDir = basedir.resolve("json");

        configDir = basedir.resolve("config");
        Path tikaConfig = configDir.resolve("tika-config.xml");

        Files.createDirectories(basedir);
        Files.createDirectories(configDir);
        Files.createDirectories(inputDir);

        String xml = IOUtils.toString(AsyncProcessorTest.class.getResourceAsStream("/configs/TIKA-4207-emitter.xml"), StandardCharsets.UTF_8);
        //do stuff to xml
        xml = xml.replace("BASE_PATH", inputDir
                .toAbsolutePath()
                .toString());
        xml = xml.replace("JSON_PATH", jsonDir
                .toAbsolutePath()
                .toString());
        xml = xml.replace("BYTES_PATH", bytesDir
                .toAbsolutePath()
                .toString());

        Files.writeString(tikaConfig, xml, StandardCharsets.UTF_8);

        Path mock = inputDir.resolve("mock.xml");
        try (OutputStream os = Files.newOutputStream(mock)) {
            IOUtils.copy(getClass().getResourceAsStream("/test-documents/basic_embedded.xml"), os);
        }
    }

    @Test
    public void testBasic() throws Exception {
//        TikaAsyncCLI cli = new TikaAsyncCLI();
        //      cli.main(new String[]{ configDir.resolve("tika-config.xml").toAbsolutePath().toString()});
        AsyncProcessor processor = new AsyncProcessor(configDir.resolve("tika-config.xml"));

        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = new EmbeddedDocumentBytesConfig(true);
        embeddedDocumentBytesConfig.setIncludeOriginal(true);
        embeddedDocumentBytesConfig.setEmitter("bytes");
        embeddedDocumentBytesConfig.setSuffixStrategy(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.NONE);
        embeddedDocumentBytesConfig.setEmbeddedIdPrefix("-");
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, HandlerConfig.DEFAULT_HANDLER_CONFIG);
        parseContext.set(EmbeddedDocumentBytesConfig.class, embeddedDocumentBytesConfig);
        FetchEmitTuple t =
                new FetchEmitTuple("myId-1", new FetchKey("fs", "mock.xml"), new EmitKey("json", "emit-1"), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(t, 1000);

        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        //TODO clean this up
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        String container = Files.readString(bytesDir.resolve("emit-1/emit-1-0"));
        assertContains("\"dc:creator\">Nikolai Lobachevsky", container);

        String xmlEmbedded = Files.readString(bytesDir.resolve("emit-1/emit-1-1"));
        assertContains("name=\"dc:creator\"", xmlEmbedded);
        assertContains(">embeddedAuthor</metadata>", xmlEmbedded);

        List<Metadata> metadataList;
        try (BufferedReader reader = Files.newBufferedReader(jsonDir.resolve("emit-1.json"))) {
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
