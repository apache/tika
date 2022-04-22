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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfigTest;
import org.apache.tika.utils.ProcessUtils;

public class TikaServerConfigTest {

    @Test
    public void testBasic() throws Exception {
        Set<String> settings = new HashSet<>();
        CommandLineParser parser = new DefaultParser();
        CommandLine emptyCommandLine = parser.parse(new Options(), new String[]{});
        Path path = Paths.get(TikaConfigTest.class.getResource(
                "/configs/tika-config-server.xml").toURI());
        TikaServerConfig config = TikaServerConfig
                .load(path,
                        emptyCommandLine,
                        settings);
        assertEquals(-1, config.getMaxRestarts());
        assertEquals(54321, config.getTaskTimeoutMillis());
        assertEquals(true, config.isEnableUnsecureFeatures());

        assertTrue(settings.contains("taskTimeoutMillis"));
        assertTrue(settings.contains("enableUnsecureFeatures"));
    }

    @Test
    public void testSupportedFetchersEmitters() throws Exception {
        Set<String> settings = new HashSet<>();
        CommandLineParser parser = new DefaultParser();
        CommandLine emptyCommandLine = parser.parse(new Options(), new String[]{});
        Path path = Paths.get(TikaConfigTest.class.getResource(
                "/configs/tika-config-server-fetchers-emitters.xml").toURI());
        TikaServerConfig config = TikaServerConfig
                .load(path,
                        emptyCommandLine,
                        settings);
        assertEquals(-1, config.getMaxRestarts());
        assertEquals(54321, config.getTaskTimeoutMillis());
        assertEquals(true, config.isEnableUnsecureFeatures());
        assertEquals(1, config.getSupportedFetchers().size());
        assertEquals(1, config.getSupportedEmitters().size());
        assertTrue(config.getSupportedFetchers().contains("fsf"));
        assertTrue(config.getSupportedEmitters().contains("fse"));
    }

    @Test
    public void testPorts() throws Exception {
        CommandLineParser parser = new DefaultParser();
        Path path = Paths.get(TikaConfigTest.class.getResource(
                "/configs/tika-config-server.xml").toURI());
        CommandLine commandLine =
                parser.parse(
                        new Options()
                                .addOption(Option.builder("p").longOpt("port").hasArg().build())
                                .addOption(Option.builder("c").longOpt("config").hasArg().build()
                                ),
                        new String[]{
                                "-p", "9994-9999",
                                "-c",
                                ProcessUtils.escapeCommandLine(path.toAbsolutePath().toString())
                        });
        TikaServerConfig config = TikaServerConfig
                .load(commandLine);
        int[] ports = config.getPorts();
        assertEquals(6, ports.length);
        assertEquals(9994, ports[0]);
        assertEquals(9999, ports[5]);
    }

    @Test
    public void testTlsConfig() throws Exception {
        Set<String> settings = new HashSet<>();
        CommandLineParser parser = new DefaultParser();
        CommandLine emptyCommandLine = parser.parse(new Options(), new String[]{});
        Path path = Paths.get(TikaConfigTest.class.getResource(
                "/configs/tika-config-server-tls.xml").toURI());
        TikaServerConfig config = TikaServerConfig
                .load(path,
                        emptyCommandLine,
                        settings);
        TlsConfig tlsConfig = config.getTlsConfig();
        System.out.println(tlsConfig);
    }

}
