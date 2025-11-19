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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorFactory;
import org.apache.tika.plugins.ExtensionConfigs;
import org.apache.tika.plugins.TikaConfigs;
import org.apache.tika.plugins.TikaPluginManager;

/**
 * Utility class to hold a single pipes iterator
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class PipesIteratorManager {

    private static final Logger LOG = LoggerFactory.getLogger(PipesIteratorManager.class);

    public static PipesIterator load(TikaPluginManager tikaPluginManager) throws IOException, TikaConfigException {
        if (tikaPluginManager.getStartedPlugins().isEmpty()) {
            tikaPluginManager.loadPlugins();
            tikaPluginManager.startPlugins();
        }
        List<PipesIterator> pipesIterators = new ArrayList<>();
        for (PipesIterator pipesIterator : tikaPluginManager.buildConfiguredExtensions(PipesIteratorFactory.class)) {
            LOG.info("Pf4j loaded plugin: " + pipesIterator.getClass());
            pipesIterators.add(pipesIterator);
        }
        if (pipesIterators.size() == 1) {
            return pipesIterators.get(0);
        }
        if (pipesIterators.size() > 1) {
            throw new TikaConfigException("Can only specify one pipesIterator");
        }
        throw new TikaConfigException("Couldn't find a pipesIterator plugin");
    }
}
