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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.PresetRegistry;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ParseContextUtils;

/**
 * Preset content is operator config resolved at config-tier trust in the worker:
 * a preset's timeout limits must survive {@code clampRequestTimeoutLimits} (only
 * request-supplied limits are clamped), and an unknown name is a task-level error,
 * not a crash.
 */
public class PresetMergeTest {

    @TempDir
    Path tmp;

    private PresetRegistry registry(String configJson) throws Exception {
        Path p = tmp.resolve("config-" + configJson.hashCode() + ".json");
        Files.writeString(p, configJson);
        return PresetRegistry.load(TikaJsonConfig.load(p), getClass().getClassLoader());
    }

    @Test
    public void testPresetTimeoutLimitsAreNotClamped() throws Exception {
        PresetRegistry registry = registry("""
                {"presets": {"slow-ocr": {"timeout-limits": {"totalTaskTimeoutMillis": 3600000}}}}
                """);
        ParseContext merged = new ParseContext();
        PipesServer.mergePreset(registry, "slow-ocr", merged);
        ParseContext requestContext = new ParseContext();
        merged.copyFrom(requestContext);
        ParseContextUtils.resolveAll(merged, getClass().getClassLoader());

        // the clamp fires only on request-supplied limits; the preset's ride at config tier
        ServerProtocolIO.clampRequestTimeoutLimits(requestContext, merged, 60_000);
        assertEquals(3600000, TimeoutLimits.get(merged).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testRequestLimitsStillClampedOverPreset() throws Exception {
        PresetRegistry registry = registry("""
                {"presets": {"slow-ocr": {"timeout-limits": {"totalTaskTimeoutMillis": 3600000}}}}
                """);
        ParseContext merged = new ParseContext();
        PipesServer.mergePreset(registry, "slow-ocr", merged);
        ParseContext requestContext = new ParseContext();
        requestContext.setJsonConfig("timeout-limits",
                "{\"totalTaskTimeoutMillis\": 7200000}");
        merged.copyFrom(requestContext);
        ParseContextUtils.resolveAll(merged, getClass().getClassLoader());

        ServerProtocolIO.clampRequestTimeoutLimits(requestContext, merged, 60_000);
        assertEquals(60_000, TimeoutLimits.get(merged).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testUnknownPresetIsTaskLevelError() throws Exception {
        PresetRegistry registry = registry("{}");
        assertThrows(PresetNotFoundException.class,
                () -> PipesServer.mergePreset(registry, "nope", new ParseContext()));
    }
}
