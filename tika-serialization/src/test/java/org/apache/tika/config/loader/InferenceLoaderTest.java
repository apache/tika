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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.EngineRegistry;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceDispatcher;
import org.apache.tika.parser.inference.InferenceSelection;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.TextInferenceFilter;

public class InferenceLoaderTest {

    private static final String ENGINES = "\"engines\": {"
            + " \"one\": { \"test-engine\": { \"label\": \"first\" } },"
            + " \"two\": { \"test-engine\": { \"label\": \"second\" } } }";

    @TempDir
    Path tmp;

    private TikaLoader load(String json) throws Exception {
        Path config = Files.createTempFile(tmp, "tika-config", ".json");
        Files.writeString(config, json);
        return TikaLoader.load(config);
    }

    @Test
    public void testEnginesAndBindingsLoad() throws Exception {
        TikaLoader loader = load("{" + ENGINES + ", \"inference\": ["
                + " { \"id\": \"pngs\", \"engine\": \"one\", \"input\": \"IMAGES\","
                + "   \"tasks\": [\"test-task\"], \"_mime-include\": [\"image/png\"],"
                + "   \"maxChunks\": 3 },"
                + " { \"engine\": \"two\", \"input\": \"images\", \"tasks\": [\"test-task\"],"
                + "   \"enabled\": false } ] }");
        EngineRegistry engines = loader.get(EngineRegistry.class);
        assertEquals("first", ((TestEngine) engines.get("one")).getLabel());
        assertEquals("second", ((TestEngine) engines.get("two")).getLabel());

        InferenceDispatcher dispatcher = loader.get(InferenceDispatcher.class);
        assertEquals(2, dispatcher.getBound().size());
        InferenceDispatcher.Bound pngs = dispatcher.getBound().get(0);
        assertEquals("pngs", pngs.binding().getId());
        assertSame(engines.get("one"), pngs.engine());
        assertEquals(3, pngs.binding().getMaxChunks());
        assertEquals(InferenceLoader.DEFAULT_MAX_BYTES, pngs.binding().getMaxBytes());
        assertTrue(pngs.binding().accepts(InputKind.IMAGES, MediaType.image("png")));
        assertFalse(pngs.binding().accepts(InputKind.IMAGES, MediaType.image("jpeg")));
        InferenceDispatcher.Bound second = dispatcher.getBound().get(1);
        assertEquals("two-images", second.binding().getId(), "default id is engine-input");
        assertFalse(second.binding().isEnabled());
        assertTrue(second.binding().accepts(InputKind.IMAGES, MediaType.image("png")));
        assertFalse(second.binding().accepts(InputKind.IMAGES, MediaType.image("svg+xml")),
                "without an include list, non-raster image types are excluded by default");

        AutoDetectParser parser = (AutoDetectParser) loader.loadAutoDetectParser();
        assertEquals(List.of(dispatcher), parser.getParseHooks().getHooks(),
                "the dispatcher rides every parse as a hook");
    }

    /** A recognizer is an engine a binding may name; the task decides whether it fits. */
    @Test
    public void testRecognizerEngineIsValidatedByTheTask() {
        TikaConfigException e = assertThrows(TikaConfigException.class, () -> load("{"
                + " \"engines\": { \"ocr\": { \"mock-enricher\": {} } },"
                + " \"inference\": [ { \"engine\": \"ocr\", \"input\": \"IMAGES\","
                + "   \"tasks\": [\"test-task\"] } ] }").get(InferenceDispatcher.class));
        assertTrue(e.getMessage().contains("test-task needs a test-engine"), e.getMessage());
    }

    @Test
    public void testPagesBindingLoads() throws Exception {
        TikaLoader loader = load("{" + ENGINES + ", \"inference\": ["
                + " { \"id\": \"page-vectors\", \"engine\": \"one\", \"input\": \"PAGES\","
                + "   \"tasks\": [\"test-task\"], \"maxChunks\": 50 } ] }");
        InferenceDispatcher dispatcher = loader.get(InferenceDispatcher.class);
        InferenceBinding pages = dispatcher.getBound().get(0).binding();
        assertEquals(InputKind.PAGES, pages.getInput());
        assertTrue(pages.accepts(InputKind.PAGES, MediaType.image("png")));
        assertFalse(pages.accepts(InputKind.IMAGES, MediaType.image("png")),
                "a PAGES binding never takes an image document");
        assertTrue(dispatcher.wantsPages(MediaType.image("png"), new Metadata(),
                new ParseContext()));
    }

    /** A TEXT binding carries its chunker and puts the TEXT stage at the end of the filters. */
    @Test
    public void testTextBindingLoadsWithChunkerAndFilter() throws Exception {
        TikaLoader loader = load("{" + ENGINES + ", \"inference\": ["
                + " { \"id\": \"text-vectors\", \"engine\": \"one\", \"input\": \"TEXT\","
                + "   \"tasks\": [\"test-task\"], \"chunker\": { \"test-chunker\": { \"size\": 3 } } } ],"
                + " \"metadata-filters\": [ { \"no-op-filter\": {} } ] }");
        InferenceBinding text = loader.get(InferenceDispatcher.class).getBound().get(0).binding();
        assertEquals(InputKind.TEXT, text.getInput());
        assertTrue(text.getChunker() instanceof TestChunker);
        assertEquals(3, ((TestChunker) text.getChunker()).getSize());
        assertEquals(2, text.getChunker().spans("abcde").size());

        MetadataFilter filters = loader.get(MetadataFilter.class);
        assertTrue(filters instanceof CompositeMetadataFilter);
        List<MetadataFilter> chain = ((CompositeMetadataFilter) filters).getFilters();
        assertEquals(2, chain.size(), "the configured filter, then the TEXT stage");
        assertTrue(chain.get(1) instanceof TextInferenceFilter);

        Path dump = Files.createTempFile(tmp, "dump", ".json");
        loader.save(dump.toFile());
        JsonNode dumped = new ObjectMapper().readTree(Files.readString(dump));
        assertEquals(1, dumped.get("metadata-filters").size(),
                "the dump keeps the configured filter; the TEXT stage is derived from inference");
        assertEquals("TEXT", dumped.get("inference").get(0).get("input").asText());

        TikaLoader noText = load("{" + ENGINES + ", \"inference\": ["
                + " { \"engine\": \"one\", \"input\": \"IMAGES\", \"tasks\": [\"test-task\"] } ] }");
        assertSame(NoOpFilter.NOOP_FILTER, noText.get(MetadataFilter.class),
                "no TEXT binding, no filters: the chain is untouched");
    }

    @Test
    public void testChunkerMisconfigurationsFailLoad() {
        String[] bad = {
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"], \"chunker\": { \"test-chunker\": {} } } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"TEXT\","
                    + " \"tasks\": [\"test-task\"], \"chunker\": { \"no-such-chunker\": {} } } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"TEXT\","
                    + " \"tasks\": [\"test-task\"], \"chunker\": \"test-chunker\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"TEXT\","
                    + " \"tasks\": [\"test-task\"], \"chunker\": { \"test-engine\": {} } } ] }",
        };
        for (String json : bad) {
            assertThrows(TikaConfigException.class,
                    () -> load(json).get(InferenceDispatcher.class), json);
        }
    }

    @Test
    public void testPerRequestSelectionResolves() throws Exception {
        TikaLoader loader = load("{" + ENGINES + ", \"inference\": ["
                + " { \"id\": \"pngs\", \"engine\": \"one\", \"input\": \"IMAGES\","
                + "   \"tasks\": [\"test-task\"] } ],"
                + " \"parse-context\": { \"inference\": { \"bindings\": [\"pngs\"],"
                + "   \"enabled\": true } } }");
        InferenceSelection selection = loader.loadParseContext().get(InferenceSelection.class);
        assertNotNull(selection, "the parse-context block resolves to the class-keyed DTO");
        assertEquals(List.of("pngs"), selection.getBindings());
        assertTrue(selection.isEnabled());
    }

    @Test
    public void testAbsentSectionsLoadNothing() throws Exception {
        TikaLoader loader = load("{ \"parsers\": [ { \"default-parser\": {} } ] }");
        assertNull(loader.get(EngineRegistry.class));
        assertNull(loader.get(InferenceDispatcher.class));
        assertNull(((AutoDetectParser) loader.loadAutoDetectParser()).getParseHooks());
    }

    @Test
    public void testMisconfigurationsFailLoad() throws Exception {
        String[] bad = {
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"nope\", \"input\": \"IMAGES\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"no-such-task\"] } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"SLIDES\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"] }, { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"] } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"], \"when\": \"always\" } ] }",
            "{ \"engines\": { \"one\": { \"test-engine\": {}, \"test-engine-2\": {} } } }",
            "{ \"engines\": { \"bad name\": { \"test-engine\": {} } } }",
            "{ \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [] } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": \"test-task\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"], \"maxChunks\": -2 } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"id\": \"bad id\", \"engine\": \"one\","
                    + " \"input\": \"IMAGES\", \"tasks\": [\"test-task\"] } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"], \"_mime-include\": \"image/png\" } ] }",
            "{" + ENGINES + ", \"inference\": [ { \"engine\": \"one\", \"input\": \"IMAGES\","
                    + " \"tasks\": [\"test-task\"], \"chunker\": { \"identity\": {} } } ] }",
        };
        for (String json : bad) {
            TikaConfigException e = assertThrows(TikaConfigException.class, () -> {
                TikaLoader loader = load(json);
                loader.get(EngineRegistry.class);
                loader.get(InferenceDispatcher.class);
            }, json);
            assertNotNull(e.getMessage());
        }
    }
}
