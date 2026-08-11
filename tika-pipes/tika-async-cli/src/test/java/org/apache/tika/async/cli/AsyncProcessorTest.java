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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.serialization.config.JsonConfigHelper;

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

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setIncludeOriginal(true);
        unpackConfig.setEmitter("fse-bytes");
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.NONE);
        unpackConfig.setEmbeddedIdPrefix("-");
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        parseContext.set(UnpackConfig.class, unpackConfig);
        parseContext.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
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
    public void testContentOnlyFromConfigDefault() throws Exception {
        // TIKA-4735: parseMode set only as a PipesConfig default (not on the request
        // context) must still be honored at emit time - the file should be raw content,
        // not a JSON metadata wrapper.
        Path contentOnlyConfig = configDir.resolve("tika-config-content-only.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("FETCHER_BASE_PATH", inputDir);
        replacements.put("JSON_EMITTER_BASE_PATH", jsonOutputDir);
        replacements.put("BYTES_EMITTER_BASE_PATH", bytesOutputDir);
        replacements.put("PLUGIN_ROOTS", Paths.get("target/plugins"));
        JsonConfigHelper.writeConfigFromResource("/configs/config-content-only-default.json",
                AsyncProcessorTest.class, replacements, contentOnlyConfig);

        AsyncProcessor processor = AsyncProcessor.load(contentOnlyConfig);

        // Deliberately do NOT set ParseMode on the request context - it must come from
        // the config default.
        FetchEmitTuple t = new FetchEmitTuple("co-1", new FetchKey("fsf", "mock.xml"),
                new EmitKey("fse-json", "emit-co"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        processor.offer(t, 1000);
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        String emitted = Files.readString(jsonOutputDir.resolve("emit-co"));
        // Raw concatenated content (markdown may escape underscores), not a JSON wrapper.
        assertContains("content", emitted);
        assertContains("some", emitted);
        assertFalse(emitted.contains(TikaCoreProperties.TIKA_CONTENT.getName()),
                "content-only output must not contain the JSON content key: " + emitted);
        String trimmed = emitted.trim();
        assertFalse(trimmed.startsWith("[") || trimmed.startsWith("{"),
                "content-only output must be raw content, not a JSON wrapper: " + emitted);
    }

    @Test
    public void testContentOnlyDynamicEmitStrategy() throws Exception {
        // TIKA-4735: With emitStrategy=DYNAMIC and a file smaller than the threshold,
        // EmitHandler would previously let the file passback to AsyncEmitter, which called
        // emitter.emit(List<EmitData>) and serialised it as JSON rather than raw content.
        // The fix forces direct server-side emission (via emitContentOnly) when an emitter
        // is configured and parseMode=CONTENT_ONLY, regardless of emit strategy.
        Path contentOnlyConfig = configDir.resolve("tika-config-content-only-dynamic.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("FETCHER_BASE_PATH", inputDir);
        replacements.put("JSON_EMITTER_BASE_PATH", jsonOutputDir);
        replacements.put("BYTES_EMITTER_BASE_PATH", bytesOutputDir);
        replacements.put("PLUGIN_ROOTS", Paths.get("target/plugins"));
        JsonConfigHelper.writeConfigFromResource("/configs/config-content-only-dynamic.json",
                AsyncProcessorTest.class, replacements, contentOnlyConfig);

        AsyncProcessor processor = AsyncProcessor.load(contentOnlyConfig);

        FetchEmitTuple t = new FetchEmitTuple("co-dyn-1", new FetchKey("fsf", "mock.xml"),
                new EmitKey("fse-json", "emit-co-dyn"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        processor.offer(t, 1000);
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        String emitted = Files.readString(jsonOutputDir.resolve("emit-co-dyn"));
        assertContains("content", emitted);
        assertFalse(emitted.contains(TikaCoreProperties.TIKA_CONTENT.getName()),
                "TIKA-4735: DYNAMIC strategy must not produce JSON wrapper in CONTENT_ONLY mode: " + emitted);
        String trimmed = emitted.trim();
        assertFalse(trimmed.startsWith("[") || trimmed.startsWith("{"),
                "TIKA-4735: DYNAMIC strategy must produce raw content, not JSON, in CONTENT_ONLY mode: " + emitted);
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

    @Test
    public void testPartialTimeoutContentIsEmitted() throws Exception {
        // totalTaskTimeoutMillis=500 with a checkpointedSleep of 700ms simulates a real
        // in-flight bounded call (e.g. an external process) that's still running when the
        // deadline passes -- it's allowed to finish, and only the *subsequent* embedded
        // doc is skipped. This is realistic (not the old total=0 "always already
        // exhausted" shortcut) because PipesServer/ConnectionHandler's hard
        // checkTotalTimeout watchdog now fires at totalTaskTimeoutMillis +
        // progressTimeoutMillis (10000ms grace here), not at totalTaskTimeoutMillis
        // itself -- giving the cooperative skip-and-emit wind-down room to complete
        // instead of racing a ~100-200ms watchdog poll at the exact deadline instant.
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<checkpointedSleep millis=\"700\" intervalMillis=\"50\"/>" +
                "<write element=\"p\">main_content</write>" +
                "<embedded filename=\"embed1.xml\" content-type=\"application/mock+xml\">" +
                "&lt;mock&gt;&lt;metadata action=\"add\" name=\"dc:creator\"&gt;embeddedAuthor&lt;/metadata&gt;" +
                "&lt;write element=\"p\"&gt;some_embedded_content&lt;/write&gt;&lt;/mock&gt;" +
                "</embedded>" +
                "</mock>";
        Path mockFile = inputDir.resolve("mock-partial-timeout.xml");
        Files.write(mockFile, mockContent.getBytes(StandardCharsets.UTF_8));

        AsyncProcessor processor = AsyncProcessor.load(configDir.resolve("tika-config.json"));

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        parseContext.set(TimeoutLimits.class, new TimeoutLimits(500, 10000));
        FetchEmitTuple t = new FetchEmitTuple("partial-timeout-1",
                new FetchKey("fsf", "mock-partial-timeout.xml"),
                new EmitKey("fse-json", "emit-partial-timeout"), new Metadata(), parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(t, 1000);
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        Path emitted = jsonOutputDir.resolve("emit-partial-timeout");
        assertTrue(Files.exists(emitted),
                "PARTIAL_TIMEOUT result must still be emitted, not silently dropped: " + emitted);
        List<Metadata> metadataList;
        try (BufferedReader reader = Files.newBufferedReader(emitted)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertFalse(metadataList.isEmpty(), "container metadata must be present");
        // markdown output escapes underscores
        assertContains("main", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("content", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("true", metadataList.get(0).get(TikaCoreProperties.TASK_DEADLINE_REACHED),
                "container metadata should record that the task deadline was reached");
    }

    @Test
    public void testPartialTimeoutEmitAllDoesNotCrashBatch() throws Exception {
        // TIKA-4813 follow-up: under EMIT_ALL, the server already emits directly and
        // returns bare EMIT_SUCCESS with null emitData. EmitHandler still relabels that
        // to PARTIAL_TIMEOUT when the deadline was reached -- before the fix,
        // AsyncProcessor.FetchEmitWorker.shouldEmit() treated PARTIAL_TIMEOUT alone as
        // "batch-emit this", offered the null emitData to the queue, and
        // AsyncEmitter.EmitDataCache.add NPE'd on emitData.getEstimatedSizeBytes(),
        // which killed the whole run via checkActive()'s ExecutionException handling.
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<write element=\"p\">main_content</write>" +
                "<embedded filename=\"embed1.xml\" content-type=\"application/mock+xml\">" +
                "&lt;mock&gt;&lt;metadata action=\"add\" name=\"dc:creator\"&gt;embeddedAuthor&lt;/metadata&gt;" +
                "&lt;write element=\"p\"&gt;some_embedded_content&lt;/write&gt;&lt;/mock&gt;" +
                "</embedded>" +
                "</mock>";
        Path mockFile = inputDir.resolve("mock-partial-timeout-emit-all.xml");
        Files.write(mockFile, mockContent.getBytes(StandardCharsets.UTF_8));

        AsyncProcessor processor = AsyncProcessor.load(configDir.resolve("tika-config.json"));

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        parseContext.set(TimeoutLimits.class, new TimeoutLimits(0, 10000));
        parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
        FetchEmitTuple t = new FetchEmitTuple("partial-timeout-emit-all-1",
                new FetchKey("fsf", "mock-partial-timeout-emit-all.xml"),
                new EmitKey("fse-json", "emit-partial-timeout-emit-all"), new Metadata(), parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

        processor.offer(t, 1000);
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        // Pre-fix, the NPE in AsyncEmitter surfaced here as a RuntimeException out of
        // checkActive(), failing the test.
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();

        // EMIT_ALL means the forked server emitted this directly -- the content must be
        // on disk even though AsyncEmitter never touched it (shouldEmit() must have
        // skipped the null-emitData offer, not silently dropped real content).
        Path emitted = jsonOutputDir.resolve("emit-partial-timeout-emit-all");
        assertTrue(Files.exists(emitted),
                "EMIT_ALL content must be written server-side even when relabeled PARTIAL_TIMEOUT: " + emitted);
        List<Metadata> metadataList;
        try (BufferedReader reader = Files.newBufferedReader(emitted)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertFalse(metadataList.isEmpty(), "container metadata must be present");
        assertContains("main", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("content", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
