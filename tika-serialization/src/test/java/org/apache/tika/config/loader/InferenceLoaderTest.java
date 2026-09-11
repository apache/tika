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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.EngineRegistry;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceDispatcher;
import org.apache.tika.parser.inference.InferenceSelection;
import org.apache.tika.parser.inference.InputKind;

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
