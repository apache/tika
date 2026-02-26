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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
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
            ObjectMapper objectMapper = TikaObjectMapperFactory.getMapper();
            ObjectNode root = (ObjectNode) objectMapper.readTree(
                    getClass().getResourceAsStream("/config-template.json"));

            // Set fetcher basePath
            ObjectNode fetchers = (ObjectNode) root.get("fetchers");
            if (fetchers != null && fetchers.has("fsf")) {
                ObjectNode fsf = (ObjectNode) fetchers.get("fsf");
                if (fsf != null && fsf.has("file-system-fetcher")) {
                    ObjectNode fsFetcher = (ObjectNode) fsf.get("file-system-fetcher");
                    fsFetcher.put("basePath", baseInput.toAbsolutePath().toString());
                }
            }

            // Set emitter basePath
            ObjectNode emitters = (ObjectNode) root.get("emitters");
            if (emitters != null && emitters.has("fse")) {
                ObjectNode fse = (ObjectNode) emitters.get("fse");
                if (fse != null && fse.has("file-system-emitter")) {
                    ObjectNode fsEmitter = (ObjectNode) fse.get("file-system-emitter");
                    fsEmitter.put("basePath", baseOutput.toAbsolutePath().toString());
                }
            }

            // Set pipes-iterator basePath
            ObjectNode pipesIterator = (ObjectNode) root.get("pipes-iterator");
            if (pipesIterator != null && pipesIterator.has("file-system-pipes-iterator")) {
                ObjectNode fsIterator = (ObjectNode) pipesIterator.get("file-system-pipes-iterator");
                fsIterator.put("basePath", baseInput.toAbsolutePath().toString());
            }

            // Set plugin-roots
            String pluginString = StringUtils.isBlank(simpleAsyncConfig.getPluginsDir()) ?
                    "plugins" : simpleAsyncConfig.getPluginsDir();
            Path plugins = Paths.get(pluginString);
            if (Files.isDirectory(plugins)) {
                pluginString = plugins.toAbsolutePath().toString();
            }
            root.put("plugin-roots", pluginString);

            // Set pipes config
            PipesConfig pipesConfig = new PipesConfig();
            pipesConfig.setNumClients(simpleAsyncConfig.getNumClients() == null ?
                    2 : simpleAsyncConfig.getNumClients());
            if (simpleAsyncConfig.getXmx() != null) {
                pipesConfig.setForkedJvmArgs(new ArrayList<>(List.of(simpleAsyncConfig.getXmx())));
            }
            if (simpleAsyncConfig.isContentOnly()) {
                pipesConfig.setParseMode(ParseMode.CONTENT_ONLY);
            } else if (simpleAsyncConfig.isConcatenate()) {
                pipesConfig.setParseMode(ParseMode.CONCATENATE);
            }

            // For content-only mode, change the emitter file extension based on handler type
            if (simpleAsyncConfig.isContentOnly()) {
                String ext = getFileExtensionForHandlerType(simpleAsyncConfig.getHandlerType());
                if (emitters != null && emitters.has("fse")) {
                    ObjectNode fse = (ObjectNode) emitters.get("fse");
                    if (fse != null && fse.has("file-system-emitter")) {
                        ObjectNode fsEmitter = (ObjectNode) fse.get("file-system-emitter");
                        fsEmitter.put("fileExtension", ext);
                    }
                }
            }

            root.set("pipes", objectMapper.valueToTree(pipesConfig));

            // Write timeout limits to parse-context if configured
            if (simpleAsyncConfig.getTimeoutMs() != null) {
                ObjectNode parseContext = (ObjectNode) root.get("parse-context");
                if (parseContext == null) {
                    parseContext = objectMapper.createObjectNode();
                    root.set("parse-context", parseContext);
                }
                ObjectNode timeoutNode = objectMapper.createObjectNode();
                timeoutNode.put("progressTimeoutMillis", simpleAsyncConfig.getTimeoutMs());
                parseContext.set("timeout-limits", timeoutNode);
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), root);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String getFileExtensionForHandlerType(
            BasicContentHandlerFactory.HANDLER_TYPE handlerType) {
        return switch (handlerType) {
            case MARKDOWN -> "md";
            case HTML -> "html";
            case XML -> "xml";
            case BODY, TEXT -> "txt";
            default -> "txt";
        };
    }
}
