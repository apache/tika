/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.AbstractTikaConfigTest;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.CompositePipesReporter;
import org.apache.tika.pipes.async.AsyncConfig;
import org.apache.tika.pipes.async.MockReporter;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.pipes.reporter.PipesReporter;

public class TikaPipesConfigTest extends AbstractTikaConfigTest {
    //this handles tests for the newer pipes type configs.
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
        PipesIterator it =
                PipesIterator.build(getConfigFilePath("pipes-iterator-config.xml"));
        assertEquals("fs1", it.getFetcherName());
    }

    @Test
    public void testMultiplePipesIterators() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            PipesIterator it =
                    PipesIterator.build(getConfigFilePath("pipes-iterator-multiple-config.xml"));
            assertEquals("fs1", it.getFetcherName());
        });
    }
    @Test
    public void testParams() throws Exception {
        //This test makes sure that pre 2.7.x configs that still contain <params/> element
        //in ConfigBase derived objects still work.
        Path configPath = getConfigFilePath("TIKA-3865-params.xml");
        AsyncConfig asyncConfig = AsyncConfig.load(configPath);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        assertTrue(reporter instanceof CompositePipesReporter);
        List<PipesReporter> reporters = ((CompositePipesReporter)reporter).getPipesReporters();
        assertEquals("somethingOrOther1", ((MockReporter)reporters.get(0)).getEndpoint());
        assertEquals("somethingOrOther2", ((MockReporter)reporters.get(1)).getEndpoint());
    }
}
