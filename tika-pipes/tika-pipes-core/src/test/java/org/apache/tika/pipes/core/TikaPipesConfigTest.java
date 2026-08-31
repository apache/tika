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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.server.ServerProtocolIO;

public class TikaPipesConfigTest extends TikaTest {

    /** A configured tempDirectory must reach the filesystem; a consumerless setter is silent. */
    @Test
    void testTempDirectoryIsHonored(@TempDir Path tmp) throws Exception {
        PipesConfig unset = new PipesConfig();
        Path systemDefault = unset.createTempDirectory("tika-pipes-config-test-");
        try {
            assertNotEquals(tmp, systemDefault.getParent());
        } finally {
            Files.deleteIfExists(systemDefault);
        }

        Path configured = tmp.resolve("nested-does-not-exist-yet");
        PipesConfig config = new PipesConfig();
        config.setTempDirectory(configured.toString());
        Path made = config.createTempDirectory("tika-pipes-config-test-");
        assertEquals(configured, made.getParent());
        assertTrue(Files.isDirectory(made));
    }

    @Test
    void testMaxIpcPayloadBytesDefault() {
        PipesConfig config = new PipesConfig();
        assertEquals(PipesConfig.DEFAULT_MAX_IPC_PAYLOAD_BYTES, config.getMaxIpcPayloadBytes());
        assertEquals(100 * 1024 * 1024, config.getMaxIpcPayloadBytes());
    }

    @Test
    void testExplicitNullParseContextDefaultsKeepDefaults() throws Exception {
        // an explicit JSON null must not bind a null policy that NPEs on the crash path
        String json = """
                {
                  "pipes": {},
                  "parse-context": {
                    "timeout-limits": null,
                    "exception-reporting": null
                  }
                }
                """;
        PipesConfig config = PipesConfig.load(TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        assertNotNull(config.getDefaultTimeoutLimits());
        assertNotNull(config.getDefaultExceptionReporting());
    }

    @Test
    void testMaxIpcPayloadBytesFromJson() throws Exception {
        String json = """
                {
                  "pipes": {
                    "maxIpcPayloadBytes": 209715200
                  }
                }
                """;
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        PipesConfig config = PipesConfig.load(tikaJsonConfig);
        assertEquals(209715200, config.getMaxIpcPayloadBytes());
        // The global constant is unchanged — the configured limit is passed per-read
        assertEquals(100 * 1024 * 1024, PipesMessage.MAX_PAYLOAD_BYTES);
    }

    @Test
    void testNumClientsRejectsNonPositive() {
        // The client queue's message-less IAE pointed at nothing; this names the field.
        PipesConfig config = new PipesConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setNumClients(0));
        assertThrows(IllegalArgumentException.class, () -> config.setNumClients(-1));
    }

    @Test
    void testMaxIpcPayloadBytesRejectsTooSmall() {
        PipesConfig config = new PipesConfig();
        // 0 and -1 are rejected (below MIN_FALLBACK_PAYLOAD_BYTES)
        assertThrows(IllegalArgumentException.class, () -> config.setMaxIpcPayloadBytes(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxIpcPayloadBytes(-1));
        // A small-but-positive value below the minimum is also rejected
        int belowMin = ServerProtocolIO.MIN_FALLBACK_PAYLOAD_BYTES - 1;
        if (belowMin > 0) {
            assertThrows(IllegalArgumentException.class, () -> config.setMaxIpcPayloadBytes(belowMin));
        }
        // A value at or above the minimum is accepted
        int atMin = ServerProtocolIO.MIN_FALLBACK_PAYLOAD_BYTES;
        config.setMaxIpcPayloadBytes(atMin);
        assertEquals(atMin, config.getMaxIpcPayloadBytes());
    }

    /**
     * The inline/ipc pair is validated after binding, so an inline threshold that is only
     * legal because ipc was raised must load no matter which key comes first.
     */
    @Test
    void testPayloadLimitPairIsKeyOrderIndependent() throws Exception {
        String inlineFirst = """
                {"pipes": {"maxInlineBytes": 99614720, "maxIpcPayloadBytes": 209715200}}
                """;
        String ipcFirst = """
                {"pipes": {"maxIpcPayloadBytes": 209715200, "maxInlineBytes": 99614720}}
                """;
        for (String json : new String[]{inlineFirst, ipcFirst}) {
            PipesConfig config = PipesConfig.load(TikaJsonConfig.load(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
            assertEquals(99614720, config.getMaxInlineBytes());
            assertEquals(209715200, config.getMaxIpcPayloadBytes());
        }
    }

    /** Lowering only ipc must re-check the untouched inline default (10MB > 90% of 5MB). */
    @Test
    void testLoweringIpcAloneRechecksInlineDefault() throws Exception {
        String json = """
                {"pipes": {"maxIpcPayloadBytes": 5242880}}
                """;
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> PipesConfig.load(tikaJsonConfig));
    }

    @Test
    void testInconsistentPayloadPairRejectedInEitherOrder() throws Exception {
        // inline 95MB against the default 100MB ipc limit fails the 10% headroom rule
        String inlineFirst = """
                {"pipes": {"maxInlineBytes": 99614720}}
                """;
        String withExplicitIpc = """
                {"pipes": {"maxIpcPayloadBytes": 104857600, "maxInlineBytes": 99614720}}
                """;
        for (String json : new String[]{inlineFirst, withExplicitIpc}) {
            TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            assertThrows(Exception.class, () -> PipesConfig.load(tikaJsonConfig));
        }
    }

    @Test
    void testCheckPayloadLimitsForSetterBuiltConfigs() {
        PipesConfig config = new PipesConfig();
        config.setMaxInlineBytes(99614720);
        assertThrows(IllegalArgumentException.class, config::checkPayloadLimits);
        config.setMaxIpcPayloadBytes(209715200);
        config.checkPayloadLimits();
    }

    @Test
    void testMaxIpcPayloadBytesFromJsonRejectsZero() throws Exception {
        String json = """
                {"pipes": {"maxIpcPayloadBytes": 0}}
                """;
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> PipesConfig.load(tikaJsonConfig));
    }

    //this handles tests for the newer pipes type configs.
/*
    TODO -- reimplent these with json
    @Test
    public void testFetchers() throws Exception {
        FetcherManager m = FetcherManager.load(getConfigFilePath("fetchers-config.xml"));
        Fetcher f1 = m.getFetcher("fs1");
        assertEquals(Paths.get("/my/base/path1"), ((FileSystemFetcher) f1).getBasePath());

        Fetcher f2 = m.getFetcher("fs2");
        assertEquals(Paths.get("/my/base/path2"), ((FileSystemFetcher) f2).getBasePath());
    }

    @Test
    public void testDuplicateFetchers() throws Exception {
        //can't have two fetchers with the same name
        assertThrows(TikaConfigException.class, () -> {
            FetcherManager.load(getConfigFilePath("fetchers-duplicate-config.xml"));
        });
    }

    @Test
    public void testNoNameFetchers() throws Exception {
        //can't have two fetchers with an empty name
        assertThrows(TikaConfigException.class, () -> {
            FetcherManager.load(getConfigFilePath("fetchers-noname-config.xml"));
        });
    }

    @Test
    public void testNoBasePathFetchers() throws Exception {
        //no basepath is allowed as of > 2.3.0
        //test that this does not throw an exception.

        FetcherManager fetcherManager = FetcherManager.load(
                getConfigFilePath("fetchers-nobasepath-config.xml"));
    }

    @Test
    public void testEmitters() throws Exception {
        EmitterManager emitterManager =
                EmitterManager.load(getConfigFilePath("emitters-config.xml"));
        Emitter em1 = emitterManager.getEmitter("em1");
        assertNotNull(em1);
        Emitter em2 = emitterManager.getEmitter("em2");
        assertNotNull(em2);
    }

    @Test
    public void testDuplicateEmitters() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            EmitterManager.load(getConfigFilePath("emitters-duplicate-config.xml"));
        });
    }



    @Test
    public void testPipesIterator() throws Exception {
        PipesIteratorBase it =
                PipesIteratorBase.build(getConfigFilePath("pipes-iterator-config.xml"));
        assertEquals("fsf1", it.getFetcherId());
    }

    @Test
    public void testMultiplePipesIterators() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            PipesIteratorBase it =
                    PipesIteratorBase.build(getConfigFilePath("pipes-iterator-multiple-config.xml"));
            assertEquals("fsf1", it.getFetcherId());
        });
    }
    */

}
