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
        boolean inputExplicit = !StringUtils.isBlank(simpleAsyncConfig.getInputDir());
        boolean outputExplicit = !StringUtils.isBlank(simpleAsyncConfig.getOutputDir());

        // -i / -o resolution. When unset they're null, in which case we don't
        // override anything the user (or the post-merge placeholder sweep
        // below) put in place.
        Path baseInput = inputExplicit ? Paths.get(simpleAsyncConfig.getInputDir()) : null;
        if (baseInput != null && Files.isRegularFile(baseInput)) {
            baseInput = baseInput.toAbsolutePath().getParent();
            if (baseInput == null) {
                throw new IllegalArgumentException("File must be at least one directory below root");
            }
        }
        Path baseOutput = outputExplicit
                ? Paths.get(simpleAsyncConfig.getOutputDir())
                : null;
        try {
            ObjectMapper objectMapper = TikaObjectMapperFactory.getMapper();
            ObjectNode root = (ObjectNode) objectMapper.readTree(
                    getClass().getResourceAsStream("/config-template.json"));

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

            // Merge user's --config first so the CLI overrides below land on
            // the final merged document. Doing this in the other order means
            // mergeUserConfig's shallow replace silently wipes any patch we
            // applied before the merge — exactly the bug behind TIKA-4739
            // ("-i/-o don't override basePath as documented").
            if (!StringUtils.isBlank(simpleAsyncConfig.getTikaConfig())) {
                Path userConfigPath = Paths.get(simpleAsyncConfig.getTikaConfig());
                JsonNode userRoot = objectMapper.readTree(userConfigPath.toFile());
                mergeUserConfig(root, (ObjectNode) userRoot);
            }

            // Resolve any unfilled placeholders left over from
            // config-template.json. These leak through when --config is
            // supplied but the user's config does not redefine the relevant
            // section (e.g. user overrides only `pipes` and inherits the
            // template's `fetchers`). We replace only the literal placeholder
            // strings, so a user-supplied real basePath is never trampled.
            // Default to CWD; the explicit -i/-o overrides below will further
            // refine when set.
            String defaultBasePath = Paths.get(".").toAbsolutePath().toString();
            replaceFileSystemBasePathPlaceholder(root, "fetchers", "file-system-fetcher",
                    "FETCHER_BASE_PATH", defaultBasePath);
            replaceSingletonFileSystemBasePathPlaceholder(root, "pipes-iterator",
                    "file-system-pipes-iterator", "FETCHER_BASE_PATH", defaultBasePath);
            replaceFileSystemBasePathPlaceholder(root, "emitters", "file-system-emitter",
                    "EMITTER_BASE_PATH", defaultBasePath);

            // Apply -i / -o on top of the merged document by component TYPE
            // rather than hardcoded id ("fsf"/"fse"). This way users who
            // renamed their filesystem fetcher/emitter still get the override,
            // and non-filesystem fetchers/emitters (S3, GCS, etc.) are left
            // untouched. baseInput/baseOutput are null when the user supplied
            // --config without -i/-o, in which case their basePath values stay
            // intact (post-merge they're either the user's real value or the
            // CWD default just installed by the placeholder sweep above).
            if (baseInput != null) {
                patchFileSystemBasePath(root, "fetchers", "file-system-fetcher",
                        baseInput.toAbsolutePath().toString());
                patchSingletonFileSystemBasePath(root, "pipes-iterator",
                        "file-system-pipes-iterator", baseInput.toAbsolutePath().toString());
            }
            if (baseOutput != null) {
                patchFileSystemBasePath(root, "emitters", "file-system-emitter",
                        baseOutput.toAbsolutePath().toString());
            }

            // CLI overrides on the pipes section.
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
                patchFileSystemField(root, "emitters", "file-system-emitter",
                        "fileExtension", ext);
            }

            // Override the emitter's onExists policy if set on the CLI (--on-exists)
            if (!StringUtils.isBlank(simpleAsyncConfig.getOnExists())) {
                patchFileSystemField(root, "emitters", "file-system-emitter",
                        "onExists", simpleAsyncConfig.getOnExists());
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
     * Sets {@code basePath} on every entry in an id-keyed section
     * ({@code fetchers}, {@code emitters}) whose wrapper type matches
     * {@code typeName}. Other component types in the section are left
     * untouched so a config that mixes filesystem + S3 still works.
     */
    private static void patchFileSystemBasePath(ObjectNode root, String section,
                                                 String typeName, String basePath) {
        patchFileSystemField(root, section, typeName, "basePath", basePath);
    }

    /**
     * Sets a single field on every id-keyed entry in {@code section} whose
     * wrapper type matches {@code typeName}.
     */
    private static void patchFileSystemField(ObjectNode root, String section,
                                              String typeName, String field, String value) {
        JsonNode sectionNode = root.get(section);
        if (sectionNode == null || !sectionNode.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> ids = sectionNode.fields();
        while (ids.hasNext()) {
            Map.Entry<String, JsonNode> idEntry = ids.next();
            JsonNode typed = idEntry.getValue();
            if (typed.isObject() && typed.has(typeName)) {
                ObjectNode target = (ObjectNode) typed.get(typeName);
                target.put(field, value);
            }
        }
    }

    /**
     * Sets {@code basePath} on a singleton section ({@code pipes-iterator})
     * whose wrapper type matches {@code typeName}.
     */
    private static void patchSingletonFileSystemBasePath(ObjectNode root, String section,
                                                          String typeName, String basePath) {
        JsonNode sectionNode = root.get(section);
        if (sectionNode == null || !sectionNode.isObject() || !sectionNode.has(typeName)) {
            return;
        }
        ObjectNode target = (ObjectNode) sectionNode.get(typeName);
        target.put("basePath", basePath);
    }

    /**
     * Replaces {@code basePath} with {@code replacement} for every id-keyed
     * entry in {@code section} of wrapper type {@code typeName} whose
     * current value is the literal {@code placeholder} string. Real
     * user-supplied paths are left alone.
     */
    private static void replaceFileSystemBasePathPlaceholder(ObjectNode root, String section,
                                                              String typeName, String placeholder,
                                                              String replacement) {
        JsonNode sectionNode = root.get(section);
        if (sectionNode == null || !sectionNode.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> ids = sectionNode.fields();
        while (ids.hasNext()) {
            JsonNode typed = ids.next().getValue();
            if (typed.isObject() && typed.has(typeName)) {
                ObjectNode target = (ObjectNode) typed.get(typeName);
                JsonNode current = target.get("basePath");
                if (current != null && current.isTextual()
                        && placeholder.equals(current.asText())) {
                    target.put("basePath", replacement);
                }
            }
        }
    }

    /**
     * Replaces the singleton {@code basePath} placeholder under
     * {@code section.typeName} only if its current value is the literal
     * placeholder. Mirrors {@link #patchSingletonFileSystemBasePath} but
     * preserves user-supplied real paths.
     */
    private static void replaceSingletonFileSystemBasePathPlaceholder(ObjectNode root,
                                                                       String section,
                                                                       String typeName,
                                                                       String placeholder,
                                                                       String replacement) {
        JsonNode sectionNode = root.get(section);
        if (sectionNode == null || !sectionNode.isObject() || !sectionNode.has(typeName)) {
            return;
        }
        ObjectNode target = (ObjectNode) sectionNode.get(typeName);
        JsonNode current = target.get("basePath");
        if (current != null && current.isTextual()
                && placeholder.equals(current.asText())) {
            target.put("basePath", replacement);
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
