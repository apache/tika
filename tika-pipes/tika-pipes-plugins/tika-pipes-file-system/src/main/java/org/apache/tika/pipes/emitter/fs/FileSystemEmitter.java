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
package org.apache.tika.pipes.emitter.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractStreamEmitter;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write to a file system.
 * <p>
 * This calculates the path to write to based on the {@link FileSystemEmitterConfig#basePath()}
 * and the value of the {@link TikaCoreProperties#SOURCE_PATH} value.
 *
 * <pre class="prettyprint">
 * </pre>
 */
public class FileSystemEmitter extends AbstractStreamEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemEmitter.class);

    public static FileSystemEmitter build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        FileSystemEmitter emitter = new FileSystemEmitter(pluginConfig);
        emitter.configure();
        return emitter;
    }

    private FileSystemEmitterConfig fileSystemEmitterConfig;

    public FileSystemEmitter(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    private void configure() throws TikaConfigException, IOException {
        fileSystemEmitterConfig = FileSystemEmitterConfig.load(pluginConfig.json());
        checkConfig(fileSystemEmitterConfig);
    }

    private void checkConfig(FileSystemEmitterConfig fileSystemEmitterConfig) {
        if (fileSystemEmitterConfig.onExists() == null) {
            throw new IllegalArgumentException("Must configure 'onExists' as 'skip', 'exception' or 'replace'");
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IOException("metadata list must not be null or of size 0");
        }

        FileSystemEmitterConfig config = null;
        try {
            config = getConfig(parseContext);
        } catch (TikaConfigException e) {
            throw new IOException(e);
        }

        Path output;

        if (!StringUtils.isBlank(config.fileExtension())) {
            emitKey += "." + config.fileExtension();
        }

        if (config.basePath() != null) {
            Path basePath = Paths.get(config.basePath());
            output = basePath.resolve(emitKey);
            if (!output.toAbsolutePath().normalize().startsWith(basePath.toAbsolutePath().normalize())) {
                throw new IOException("path traversal?! " + output.toAbsolutePath());
            }
        } else {
            output = Paths.get(emitKey);
        }

        if (output.getParent() != null && !Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }

        // Check onExists configuration
        if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.SKIP) {
            if (Files.exists(output)) {
                LOG.debug("Skipping existing file: {}", output);
                return;
            }
        }
        if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.EXCEPTION) {
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW)) { //CREATE_NEW forces an IOException if the file already exists
                JsonMetadataList.toJson(metadataList, writer, config.prettyPrint());
            }
        } else {
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                JsonMetadataList.toJson(metadataList, writer, config.prettyPrint());
            }

        }
    }

    @Override
    public void emit(String emitKey, InputStream inputStream, Metadata userMetadata, ParseContext parseContext) throws IOException {

        FileSystemEmitterConfig config = null;
        try {
            config = getConfig(parseContext);
        } catch (TikaConfigException e) {
            throw new IOException(e);
        }

        Path output;
        if (config.basePath() != null) {
            Path basePath = Paths.get(config.basePath());
            output = basePath.resolve(emitKey);
            if (!output.toAbsolutePath().normalize().startsWith(basePath.toAbsolutePath().normalize())) {
                throw new IOException("path traversal?! " + output.toAbsolutePath());
            }
        } else {
            output = Paths.get(emitKey);
        }

        if (!Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }
        if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.REPLACE) {
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
        } else if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.EXCEPTION) {
            Files.copy(inputStream, output);
        } else if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.SKIP) {
            if (!Files.isRegularFile(output)) {
                try {
                    Files.copy(inputStream, output);
                } catch (FileAlreadyExistsException e) {
                    //swallow
                }
            }
        }
    }

    private FileSystemEmitterConfig getConfig(ParseContext parseContext) throws TikaConfigException, IOException {
        FileSystemEmitterConfig config = fileSystemEmitterConfig;
        ConfigContainer configContainer = parseContext.get(ConfigContainer.class);
        if (configContainer != null) {
            Optional<JsonConfig> configJson = configContainer.get(getExtensionConfig().id());
            if (configJson.isPresent()) {
                // Check if basePath is present in runtime config - this is not allowed for security
                if (configJson
                        .get()
                        .json()
                        .contains("\"basePath\"")) {
                    throw new TikaConfigException("Cannot change 'basePath' at runtime for security reasons. " + "basePath can only be set during initialization.");
                }

                // Load runtime config (excludes basePath for security)
                FileSystemEmitterRuntimeConfig runtimeConfig = FileSystemEmitterRuntimeConfig.load(configJson.get().json());

                // Merge runtime config into default config while preserving basePath
                config = new FileSystemEmitterConfig(fileSystemEmitterConfig.basePath(), runtimeConfig.getFileExtension(), runtimeConfig.getOnExists(),
                        runtimeConfig.isPrettyPrint());
                checkConfig(config);
            }

        }
        return config;
    }
}
