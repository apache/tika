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
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.pipes.api.reporter.PipesReporterFactory;
import org.apache.tika.plugins.PluginComponentLoader;
import org.apache.tika.plugins.TikaConfigs;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class ReporterManager {

    public static final String CONFIG_KEY = "pipes-reporters";

    public static PipesReporter load(PluginManager pluginManager, TikaConfigs tikaConfigs) throws IOException, TikaConfigException {

        JsonNode node = tikaConfigs.getRoot().get(CONFIG_KEY);

        List<PipesReporter> reporters =  PluginComponentLoader.loadUnnamedInstances(pluginManager, PipesReporterFactory.class, node);
        if (reporters.isEmpty()) {
            return NoOpReporter.NO_OP;
        } else if (reporters.size() == 1) {
            return reporters.get(0);
        } else {
            return new CompositePipesReporter(reporters);
        }
    }
}
