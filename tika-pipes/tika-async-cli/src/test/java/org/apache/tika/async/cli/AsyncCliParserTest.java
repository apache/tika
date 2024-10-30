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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class AsyncCliParserTest {

    @Test
    public void testBasic() throws Exception {
        SimpleAsyncConfig simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"input", "output"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"-o", "output", "-i", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"-output", "output", "-input", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"--output", "output", "--input", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"--output=output", "--input=input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());
    }

    @Test
    public void testAll() throws Exception {
        SimpleAsyncConfig simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(
                new String[]{"-i", "input", "-o", "output", "-n", "5", "-t", "30000", "-x", "1g"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertEquals(5, simpleAsyncConfig.getNumClients());
        assertEquals(30000L, simpleAsyncConfig.getTimeoutMs());
        assertEquals("1g", simpleAsyncConfig.getXmx());
    }

    //TODO -- test for file list with and without inputDir
}
