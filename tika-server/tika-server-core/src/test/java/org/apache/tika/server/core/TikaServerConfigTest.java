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
package org.apache.tika.server.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;

import org.apache.tika.config.TikaConfigTest;

public class TikaServerConfigTest {

    @Test
    public void testBasic() throws Exception {
        Set<String> settings = new HashSet<>();
        CommandLineParser parser = new DefaultParser();
        CommandLine emptyCommandLine = parser.parse(new Options(), new String[]{});
        TikaServerConfig config = TikaServerConfig
                .load(TikaConfigTest.class.getResourceAsStream("/configs/tika-config-server.xml"),
                        emptyCommandLine,
                        settings);
        assertEquals(-1, config.getMaxRestarts());
        assertEquals(54321, config.getTaskTimeoutMillis());
        assertEquals(true, config.isEnableUnsecureFeatures());

        assertTrue(settings.contains("taskTimeoutMillis"));
        assertTrue(settings.contains("enableUnsecureFeatures"));
    }
}
