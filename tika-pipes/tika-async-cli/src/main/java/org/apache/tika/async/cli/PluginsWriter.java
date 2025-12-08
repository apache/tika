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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.utils.StringUtils;

public class PluginsWriter {


    private final SimpleAsyncConfig simpleAsyncConfig;
    private final Path pluginsPath;

    public PluginsWriter(SimpleAsyncConfig simpleAsyncConfig, Path pluginsConfig) {
        this.simpleAsyncConfig = simpleAsyncConfig;
        this.pluginsPath = pluginsConfig;
    }

    void write(Path output) throws IOException {
        Path baseInput = Paths.get(simpleAsyncConfig.getInputDir());
        Path baseOutput = Paths.get(simpleAsyncConfig.getOutputDir());
        if (Files.isRegularFile(baseInput)) {
            baseInput = baseInput.toAbsolutePath().getParent();
            if (baseInput == null) {
                throw new IllegalArgumentException("File must be at least one directory below root");
            }
        }
        try {
            String jsonTemplate = new String(getClass().getResourceAsStream("/config-template.json").readAllBytes(), StandardCharsets.UTF_8);
            String json = jsonTemplate.replace("FETCHER_BASE_PATH", baseInput.toAbsolutePath().toString());
            json = json.replace("EMITTER_BASE_PATH", baseOutput.toAbsolutePath().toString());
            String pluginString = StringUtils.isBlank(simpleAsyncConfig.getPluginsDir()) ? "plugins" : simpleAsyncConfig.getPluginsDir();
            Path plugins = Paths.get(pluginString);
            if (Files.isDirectory(plugins)) {
                pluginString = plugins.toAbsolutePath().toString();
            }
            json = json.replace("PLUGIN_ROOTS", pluginString).replace("\\", "/");
            PipesConfig pipesConfig = new PipesConfig();

            pipesConfig.setNumClients(simpleAsyncConfig.getNumClients() == null ? 2 : simpleAsyncConfig.getNumClients());

            if (simpleAsyncConfig.getXmx() != null) {
                pipesConfig.setForkedJvmArgs(new ArrayList<>(List.of(simpleAsyncConfig.getXmx())));
            }
            if (simpleAsyncConfig.getTimeoutMs() != null) {
                pipesConfig.setTimeoutMillis(simpleAsyncConfig.getTimeoutMs());
            }
            ObjectMapper objectMapper = PolymorphicObjectMapperFactory.getMapper();
            ObjectNode root = (ObjectNode) objectMapper.readTree(json.getBytes(StandardCharsets.UTF_8));
            root.set("pipes", objectMapper.valueToTree(pipesConfig));

            Files.writeString(output, root.toString());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
