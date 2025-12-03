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
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorFactory;
import org.apache.tika.plugins.PluginComponentLoader;
import org.apache.tika.plugins.TikaConfigs;

/**
 * Utility class to hold a single pipes iterator
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class PipesIteratorManager {

    public static final String CONFIG_KEY = "pipes-iterator";

    public static Optional<PipesIterator> load(PluginManager pluginManager, TikaConfigs tikaConfigs) throws IOException, TikaConfigException {

        JsonNode node = tikaConfigs.getTikaJsonConfig()
                                   .getRootNode().get(CONFIG_KEY);

        return PluginComponentLoader.loadSingleton(pluginManager, PipesIteratorFactory.class, node);
    }
}
