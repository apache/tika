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
import java.util.Set;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.pipes.api.reporter.PipesReporterFactory;
import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.PluginConfigs;
import org.apache.tika.plugins.TikaPluginsManager;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class ReporterManager {

    private static final Logger LOG = LoggerFactory.getLogger(ReporterManager.class);

    public static PipesReporter load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    public static PipesReporter load(InputStream is) throws IOException, TikaConfigException {
        //this will throw a TikaConfigException if "fetchers" is not loaded
        TikaPluginsManager tikaPluginsManager = TikaPluginsManager.load(is, TikaPluginsManager.PLUGIN_TYPES.FETCHERS);
        return load(tikaPluginsManager);
    }

    public static PipesReporter load(TikaPluginsManager tikaPluginsManager) throws IOException, TikaConfigException {
        Optional<PluginConfigs> reporterPluginConfigsOpt = tikaPluginsManager.get(TikaPluginsManager.PLUGIN_TYPES.REPORTERS);

        PluginManager pluginManager = null;
        if (! tikaPluginsManager.getPluginsPaths().isEmpty()) {
            LOG.warn("LOADING WITH PLUGINS PATHS: {}", tikaPluginsManager.getPluginsPaths());
            pluginManager = new DefaultPluginManager(tikaPluginsManager.getPluginsPaths());
        } else {
            LOG.warn("NOT LOADING WITH PLUGINS PATHS: {}", tikaPluginsManager.getPluginsPaths());
            pluginManager = new DefaultPluginManager();
        }
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        List<PipesReporter> pipesReporterList = new ArrayList<>();
        for (PipesReporterFactory reporterFactory : pluginManager.getExtensions(PipesReporterFactory.class)) {
            LOG.warn("Pf4j loaded plugin: " + reporterFactory.getClass());
            PluginWrapper pluginWrapper = pluginManager.whichPlugin(reporterFactory.getClass());
            if (pluginWrapper == null) {
                LOG.warn("Couldn't find plugin wrapper for class={}", reporterFactory.getClass());
                continue;
            }
            String pluginId = pluginWrapper.getPluginId();
            if (reporterPluginConfigsOpt.isPresent()) {
                PluginConfigs reporterPluginConfigs = reporterPluginConfigsOpt.get();
                Set<String> ids = reporterPluginConfigs.getIdsByPluginId(pluginId);
                if (ids.isEmpty()) {
                    LOG.warn("Couldn't find config for class={} pluginId={}. Skipping", reporterFactory.getClass(), pluginId);
                }
                for (String id : ids) {
                    Optional<PluginConfig> pluginConfigOpt = reporterPluginConfigs.getById(id);
                    if (pluginConfigOpt.isEmpty()) {
                        LOG.warn("Couldn't find config for id={}", id);
                    } else {
                        PluginConfig pluginConfig = pluginConfigOpt.get();
                        PipesReporter reporter = reporterFactory.buildPlugin(pluginConfig);
                        pipesReporterList.add(reporter);
                    }
                }
            } else {
                LOG.debug("found no config for pluginId={}", pluginId);
            }
        }
        if (pipesReporterList.isEmpty()) {
            return NoOpReporter.NO_OP;
        } else if (pipesReporterList.size() == 1) {
            return pipesReporterList.get(0);
        } else {
            return new CompositePipesReporter(pipesReporterList);
        }
    }
}
