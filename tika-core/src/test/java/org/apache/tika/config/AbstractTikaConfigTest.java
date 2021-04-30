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
package org.apache.tika.config;

import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;

import org.apache.tika.TikaTest;
import org.apache.tika.parser.ParseContext;

/**
 * Parent of Junit test classes for {@link TikaConfig}, including
 * Tika Core based ones, and ones in Tika Parsers that do things
 * that {@link TikaConfigTest} can't, do due to a need for the
 * full set of "real" classes of parsers / detectors
 */
public abstract class AbstractTikaConfigTest extends TikaTest {
    protected static ParseContext context = new ParseContext();

    protected static Path getConfigFilePath(String config) throws Exception {
        URL url = TikaConfig.class.getResource(config);
        assertNotNull("Test Tika Config not found: " + config, url);
        return Paths.get(url.toURI());
    }


    protected static String getConfigPath(String config) throws Exception {
        URL url = TikaConfig.class.getResource(config);
        assertNotNull("Test Tika Config not found: " + config, url);
        return url.toExternalForm();
    }

    protected static TikaConfig getConfig(String config) throws Exception {
        System.setProperty("tika.config", getConfigPath(config));
        return new TikaConfig();
    }

    @After
    public void resetConfig() {
        System.clearProperty("tika.config");
    }
}
