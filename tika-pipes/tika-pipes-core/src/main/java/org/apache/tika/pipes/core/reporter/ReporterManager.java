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
package org.apache.tika.pipes.core.reporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.pipes.api.reporter.PipesReporterFactory;
import org.apache.tika.plugins.ExtensionConfigs;
import org.apache.tika.plugins.TikaConfigs;
import org.apache.tika.plugins.TikaPluginManager;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class ReporterManager {

    private static final Logger LOG = LoggerFactory.getLogger(ReporterManager.class);

    public static PipesReporter load(TikaPluginManager tikaPluginManager) throws IOException, TikaConfigException {
        if (tikaPluginManager.getStartedPlugins().isEmpty()) {
            tikaPluginManager.loadPlugins();
            tikaPluginManager.startPlugins();
        }
        List<PipesReporter> pipesReporters = new ArrayList<>();
        for (PipesReporter pipesReporter : tikaPluginManager.buildConfiguredExtensions(PipesReporterFactory.class)) {
            LOG.info("Pf4j loaded plugin: " + pipesReporter.getClass());
            pipesReporters.add(pipesReporter);
        }
        if (pipesReporters.size() == 1) {
            return pipesReporters.get(0);
        }
        if (pipesReporters.size() > 1) {
            return new CompositePipesReporter(pipesReporters);
        }
        return NoOpReporter.NO_OP;
    }
}
