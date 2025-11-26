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
package org.apache.tika.pipes.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginsTestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(PluginsTestHelper.class);

    public static Path getFileSystemFetcherConfig(Path configBase) throws Exception {
        return getFileSystemFetcherConfig(configBase, configBase.resolve("input"), configBase.resolve("output"), null);
    }

    public static Path getFileSystemFetcherConfig(Path configBase, Path fetcherBase, Path emitterBase) throws Exception {
        return getFileSystemFetcherConfig(configBase, fetcherBase, emitterBase, null);
    }

    public static Path getFileSystemFetcherConfig(Path configBase, Path fetcherBase, Path emitterBase, Path tikaConfigPath) throws Exception {
        return getFileSystemFetcherConfig(configBase, fetcherBase, emitterBase, tikaConfigPath, false);
    }

    public static Path getFileSystemFetcherConfig(Path configBase, Path fetcherBase, Path emitterBase, Path tikaConfigPath, boolean emitIntermediateResults) throws Exception {
        Path pipesConfig = configBase.resolve("pipes-config.json");

        Path tikaPluginsTemplate = Paths.get("src", "test", "resources", "configs", "fetchers-emitters.json");
        String json = Files.readString(tikaPluginsTemplate, StandardCharsets.UTF_8);

        json = json.replace("FETCHER_BASE_PATH", fetcherBase
                .toAbsolutePath()
                .toString());

        if (emitterBase != null) {
            json = json.replace("EMITTER_BASE_PATH", emitterBase
                    .toAbsolutePath()
                    .toString());
        }
        Path pwd = Paths.get("");
        Path plugins = pwd.resolve("target/plugins");
        if (Files.isDirectory(plugins)) {
            json = json.replace("PLUGINS_PATHS", plugins.toAbsolutePath().toString());
            LOG.info("found plugins path");
        } else {
            LOG.warn("Couldn't find plugins from {}",  pwd.toAbsolutePath());
        }
        if (tikaConfigPath != null) {
            json = json.replace("TIKA_CONFIG", tikaConfigPath.toAbsolutePath().toString());
            json = json.replace("PLUGINS_CONFIG", pipesConfig.toAbsolutePath().toString());
        }
        json = json.replace("EMIT_INTERMEDIATE_RESULTS", String.valueOf(emitIntermediateResults));
        json = json.replace("\\", "/");
        Files.write(pipesConfig, json.getBytes(StandardCharsets.UTF_8));
        return pipesConfig;
    }

    public static void copyTestFilesToTmpInput(Path tmp, String... testDocs) throws IOException {
        Path inputDir = tmp.resolve("input");
        if (!Files.isDirectory(inputDir)) {
            Files.createDirectories(inputDir);
        }
        for (String testDoc : testDocs) {
            Files.copy(PipesServerTest.class.getResourceAsStream("/test-documents/" + testDoc), inputDir.resolve(testDoc));
        }
    }
}
