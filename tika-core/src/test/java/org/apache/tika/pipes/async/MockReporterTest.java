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
package org.apache.tika.pipes.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.CompositePipesReporter;
import org.apache.tika.pipes.PipesReporter;

public class MockReporterTest {

    @Test
    public void testBasic() throws Exception {
        Path configPath = Paths.get(this.getClass().getResource("TIKA-3507.xml").toURI());
        AsyncConfig asyncConfig = AsyncConfig.load(configPath);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        assertTrue(reporter instanceof MockReporter);
        assertEquals("somethingOrOther", ((MockReporter)reporter).getEndpoint());
    }

    @Test
    public void testOlderCompositePipesReporter() throws Exception {
        Path configPath = Paths.get(this.getClass().getResource("TIKA-3865-deprecated.xml").toURI());
        AsyncConfig asyncConfig = AsyncConfig.load(configPath);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        assertTrue(reporter instanceof CompositePipesReporter);
        List<PipesReporter> reporters = ((CompositePipesReporter)reporter).getPipesReporters();
        assertEquals("somethingOrOther1", ((MockReporter)reporters.get(0)).getEndpoint());
        assertEquals("somethingOrOther2", ((MockReporter)reporters.get(1)).getEndpoint());
    }

    @Test
    public void testCompositePipesReporter() throws Exception {
        Path configPath = Paths.get(this.getClass().getResource("TIKA-3865.xml").toURI());
        AsyncConfig asyncConfig = AsyncConfig.load(configPath);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        assertTrue(reporter instanceof CompositePipesReporter);
        List<PipesReporter> reporters = ((CompositePipesReporter)reporter).getPipesReporters();
        assertEquals("somethingOrOther1", ((MockReporter)reporters.get(0)).getEndpoint());
        assertEquals("somethingOrOther2", ((MockReporter)reporters.get(1)).getEndpoint());
    }
}
