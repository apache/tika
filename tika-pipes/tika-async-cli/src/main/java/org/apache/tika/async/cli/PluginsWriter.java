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
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
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
        Path baseInput = StringUtils.isBlank(simpleAsyncConfig.getInputDir())
                ? Paths.get(".").toAbsolutePath()
                : Paths.get(simpleAsyncConfig.getInputDir());
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
            String pluginString;
            if (!StringUtils.isBlank(simpleAsyncConfig.getPluginsDir())) {
                pluginString = simpleAsyncConfig.getPluginsDir();
                Path plugins = Paths.get(pluginString);
                if (Files.isDirectory(plugins)) {
                    pluginString = plugins.toAbsolutePath().toString();
                }
            } else {
                pluginString = TikaAsyncCLI.resolveDefaultPluginsDir();
            }
            root.put("plugin-roots", pluginString);

            // If the user provided a -c config, merge their settings first.
            // This brings in parsers, parse-context, metadata-filters, and
            // optionally pipes config (e.g. forkedJvmArgs with log4j settings).
            if (!StringUtils.isBlank(simpleAsyncConfig.getTikaConfig())) {
                Path userConfigPath = Paths.get(simpleAsyncConfig.getTikaConfig());
                JsonNode userRoot = objectMapper.readTree(userConfigPath.toFile());
                mergeUserConfig(root, (ObjectNode) userRoot);
            }

            // Now apply CLI overrides on top of whatever pipes config exists.
            // This lets the user have forkedJvmArgs in their config (e.g. log4j)
            // while still controlling numClients and Xmx from the command line.
            ObjectNode pipesNode = root.has("pipes")
                    ? (ObjectNode) root.get("pipes")
                    : objectMapper.createObjectNode();

            if (simpleAsyncConfig.getNumClients() != null) {
                pipesNode.put("numClients", simpleAsyncConfig.getNumClients());
            } else if (!pipesNode.has("numClients")) {
                pipesNode.put("numClients", 2);
            }

            if (simpleAsyncConfig.getXmx() != null) {
                String xmx = simpleAsyncConfig.getXmx();
                if (!xmx.startsWith("-")) {
                    xmx = "-Xmx" + xmx;
                }
                // Replace or add -Xmx in forkedJvmArgs, preserving other args
                mergeXmxIntoJvmArgs(pipesNode, xmx, objectMapper);
            }

            if (simpleAsyncConfig.isContentOnly()) {
                pipesNode.put("parseMode", "CONTENT_ONLY");
            } else if (simpleAsyncConfig.isConcatenate()) {
                pipesNode.put("parseMode", "CONCATENATE");
            }

            root.set("pipes", pipesNode);

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

            // Write timeout limits to parse-context if configured on CLI
            if (simpleAsyncConfig.getTimeoutMs() != null) {
                ObjectNode parseContext = root.has("parse-context")
                        ? (ObjectNode) root.get("parse-context")
                        : objectMapper.createObjectNode();
                ObjectNode timeoutNode = objectMapper.createObjectNode();
                timeoutNode.put("progressTimeoutMillis", simpleAsyncConfig.getTimeoutMs());
                parseContext.set("timeout-limits", timeoutNode);
                root.set("parse-context", parseContext);
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), root);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Merges user config fields into the auto-generated root.
     * All user fields override the auto-generated template values.
     */
    private static void mergeUserConfig(ObjectNode root, ObjectNode userConfig) {
        Iterator<Map.Entry<String, JsonNode>> fields = userConfig.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            root.set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Merges an -Xmx arg into the forkedJvmArgs array, replacing any existing -Xmx
     * and preserving all other args (e.g. -Dlog4j2.configurationFile=...).
     */
    private static void mergeXmxIntoJvmArgs(ObjectNode pipesNode, String xmx,
                                              ObjectMapper objectMapper) {
        com.fasterxml.jackson.databind.node.ArrayNode argsArray =
                objectMapper.createArrayNode();

        // Preserve existing args, skipping any old -Xmx
        if (pipesNode.has("forkedJvmArgs") && pipesNode.get("forkedJvmArgs").isArray()) {
            for (JsonNode arg : pipesNode.get("forkedJvmArgs")) {
                String val = arg.asText();
                if (!val.startsWith("-Xmx")) {
                    argsArray.add(val);
                }
            }
        }
        argsArray.add(xmx);
        pipesNode.set("forkedJvmArgs", argsArray);
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
