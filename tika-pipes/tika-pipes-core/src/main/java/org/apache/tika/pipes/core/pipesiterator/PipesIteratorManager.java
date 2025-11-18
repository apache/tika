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
package org.apache.tika.pipes.core.pipesiterator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorFactory;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.ExtensionConfigs;
import org.apache.tika.plugins.TikaExtensionConfigsManager;

/**
 * Utility class to hold a single pipes iterator
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class PipesIteratorManager {

    private static final Logger LOG = LoggerFactory.getLogger(PipesIteratorManager.class);

    public static PipesIterator load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    public static PipesIterator load(InputStream is) throws IOException, TikaConfigException {
        //this will throw a TikaConfigException if "fetchers" is not loaded
        TikaExtensionConfigsManager tikaExtensionConfigsManager = TikaExtensionConfigsManager.load(is, true, TikaExtensionConfigsManager.EXTENSION_TYPES.PIPES_ITERATOR);
        return load(tikaExtensionConfigsManager);
    }

    public static PipesIterator load(TikaExtensionConfigsManager tikaExtensionConfigsManager) throws IOException, TikaConfigException {
        Optional<ExtensionConfigs> pipesIteratorPluginConfigsOpt = tikaExtensionConfigsManager.get(TikaExtensionConfigsManager.EXTENSION_TYPES.PIPES_ITERATOR);
        return null;
        /*
        if (pipesIteratorPluginConfigsOpt.isEmpty()) {
            throw new TikaConfigException("Forgot to load 'pipesIterator'?");
        }
        ExtensionConfigs pipesIteratorConfig = pipesIteratorPluginConfigsOpt.get();
        PluginManager pluginManager = null;
        if (! tikaExtensionConfigsManager
                .getPluginRoots().isEmpty()) {
            LOG.warn("LOADING WITH PLUGINS PATHS: {}", tikaExtensionConfigsManager.getPluginRoots());
            pluginManager = new DefaultPluginManager(tikaExtensionConfigsManager
                    .getPluginRoots().toArray(new Path[0]));
        } else {
            LOG.warn("NOT LOADING WITH PLUGINS PATHS: {}", tikaExtensionConfigsManager.getPluginRoots());
            pluginManager = new DefaultPluginManager();
        }
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        for (PipesIteratorFactory pipesIteratorFactory : pluginManager.getExtensions(PipesIteratorFactory.class)) {
            LOG.warn("Pf4j loaded plugin: " + pipesIteratorFactory.getClass());
            PluginWrapper pluginWrapper = pluginManager.whichPlugin(pipesIteratorFactory.getClass());
            if (pluginWrapper == null) {
                LOG.warn("Couldn't find plugin wrapper for class={}", pipesIteratorFactory.getClass());
                continue;
            }
            String pluginId = pluginWrapper.getPluginId();
            Set<String> ids = pipesIteratorConfig.getIdsByPluginId(pluginId);
            if (ids.isEmpty()) {
                String msg = "Couldn't find config for class=" + pipesIteratorFactory.getClass() +
                        "pluginId=" + pluginId;
                throw new TikaConfigException(msg);
            }
            if (ids.size() > 1) {
                String msg = "Can't have more than a single PipesIterator. I see: " + ids.size();
                throw new TikaConfigException(msg);
            }
            for (String id : ids) {
                Optional<ExtensionConfig> pluginConfigOpt = pipesIteratorConfig.getById(id);
                if (pluginConfigOpt.isEmpty()) {
                    throw new TikaConfigException("Couldn't find config for id=" + id);
                } else {
                    ExtensionConfig pluginConfig = pluginConfigOpt.get();
                    PipesIterator pipesIterator = pipesIteratorFactory.buildPlugin(pluginConfig);
                    return pipesIterator;
                }
            }
        }
        throw new TikaConfigException("Couldn't find a pipes_iterator plugin");*/
    }

}
