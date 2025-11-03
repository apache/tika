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

public class PluginsWriter {

    final static String JSON_TEMPLATE = """
            {
              "plugins" : {
                "fetchers": {
                  "file-system-fetcher": {
                    "basePath": "FETCHER_BASE_PATH",
                    "extractFileSystemMetadata": false
                  }
                },
                "emitters": {
                  "file-system-emitter": {
                    "basePath": "EMITTER_BASE_PATH",
                    "fileExtension": "json"
                  }
                },
                "pf4j.pluginsDir": "PLUGINS_DIR"
              }
            }
            """;
    private final SimpleAsyncConfig simpleAsyncConfig;

    public PluginsWriter(SimpleAsyncConfig simpleAsyncConfig) {
        this.simpleAsyncConfig = simpleAsyncConfig;
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
            String json = JSON_TEMPLATE.replace("FETCHER_BASE_PATH", baseInput.toAbsolutePath().toString());
            json = json.replace("EMITTER_BASE_PATH", baseOutput.toAbsolutePath().toString());
            String pluginString = "plugins";
            Path plugins = Paths.get(pluginString);
            if (Files.isDirectory(plugins)) {
                pluginString = plugins.toAbsolutePath().toString();
            }
            json = json.replace("PLUGINS_DIR", pluginString);
            Files.writeString(output, json);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
