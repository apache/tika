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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
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

    // in-progress writes; crawlers of the output dir should ignore these
    static final String TMP_SUFFIX = ".tmp";

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

    private void checkConfig(FileSystemEmitterConfig fileSystemEmitterConfig) throws TikaConfigException {
        if (fileSystemEmitterConfig.onExists() == null) {
            throw new TikaConfigException("Must configure 'onExists' as 'skip', 'exception' or 'replace'");
        }
        if (StringUtils.isBlank(fileSystemEmitterConfig.basePath())
                && !fileSystemEmitterConfig.allowAbsolutePaths()) {
            throw new TikaConfigException(
                    "'basePath' must be set, or 'allowAbsolutePaths' must be true. "
                            + "Without basePath, clients can write to any file this process "
                            + "has access to. Set 'allowAbsolutePaths: true' to explicitly "
                            + "allow this behavior and accept the security risks.");
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

        if (!config.atomicWrites()) {
            writeInPlace(metadataList, output, config);
            return;
        }
        Path tmp = tmpFor(output);
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW)) {
                JsonMetadataList.toJson(metadataList, writer, config.prettyPrint());
            }
            publish(tmp, output, config.onExists());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // atomicWrites=false: the pre-TIKA-4848 behavior; readers can observe a partial file
    private static void writeInPlace(List<Metadata> metadataList, Path output,
                                     FileSystemEmitterConfig config) throws IOException {
        if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.EXCEPTION) {
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW)) {
                JsonMetadataList.toJson(metadataList, writer, config.prettyPrint());
            } catch (FileAlreadyExistsException e) {
                throw alreadyExistsException(output);
            }
        } else {
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                JsonMetadataList.toJson(metadataList, writer, config.prettyPrint());
            }
        }
    }

    private static Path tmpFor(Path output) {
        // sibling so the rename stays on one filesystem (and therefore atomic)
        return output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + TMP_SUFFIX);
    }

    /**
     * Moves the fully written {@code tmp} onto {@code output} with a single rename, so a
     * concurrent reader never sees a partial file. Ownership of {@code tmp} passes to this
     * method: it is gone on return, whether moved or discarded.
     */
    private static void publish(Path tmp, Path output, FileSystemEmitterConfig.ON_EXISTS onExists)
            throws IOException {
        if (onExists == FileSystemEmitterConfig.ON_EXISTS.REPLACE) {
            Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        // no REPLACE_EXISTING: Files.move refuses an existing target rather than clobbering it
        try {
            Files.move(tmp, output);
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(tmp);
            if (onExists == FileSystemEmitterConfig.ON_EXISTS.EXCEPTION) {
                throw alreadyExistsException(output);
            }
            LOG.debug("Skipping existing file: {}", output);
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

        // CONTENT_ONLY emit keys are the input's own name, so without the extension the output
        // collides with the input. UNPACK keys already carry the embedded file's own name and
        // extension, so appending there would rename every unpacked file.
        if (parseContext.get(ParseMode.class) == ParseMode.CONTENT_ONLY
                && !StringUtils.isBlank(config.fileExtension())) {
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

        if (!Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }
        if (config.onExists() == FileSystemEmitterConfig.ON_EXISTS.SKIP && Files.exists(output)) {
            LOG.debug("Skipping existing file: {}", output);
            return;
        }
        if (!config.atomicWrites()) {
            copyInPlace(inputStream, output, config.onExists());
            return;
        }
        Path tmp = tmpFor(output);
        try {
            Files.copy(inputStream, tmp);
            publish(tmp, output, config.onExists());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void copyInPlace(InputStream inputStream, Path output,
                                    FileSystemEmitterConfig.ON_EXISTS onExists) throws IOException {
        if (onExists == FileSystemEmitterConfig.ON_EXISTS.REPLACE) {
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try {
            Files.copy(inputStream, output);
        } catch (FileAlreadyExistsException e) {
            if (onExists == FileSystemEmitterConfig.ON_EXISTS.EXCEPTION) {
                throw alreadyExistsException(output);
            }
        }
    }

    /**
     * Actionable error for the {@code onExists=EXCEPTION} case; the bare
     * {@link FileAlreadyExistsException} reports only the path (TIKA-4736).
     */
    private static IOException alreadyExistsException(Path output) {
        return new IOException("Output already exists (onExists=EXCEPTION, not overwritten): "
                + output.toAbsolutePath()
                + ". Use an empty output dir, delete the file, or set onExists to REPLACE or SKIP.");
    }

    private FileSystemEmitterConfig getConfig(ParseContext parseContext) throws TikaConfigException, IOException {
        FileSystemEmitterConfig config = fileSystemEmitterConfig;
        String configKey = getExtensionConfig().id();
        if (parseContext.hasJsonConfig(configKey)) {
            JsonConfig configJson = parseContext.getJsonConfig(configKey);
            if (configJson != null) {
                // Check if basePath is present in runtime config - this is not allowed for security
                if (configJson.json().contains("\"basePath\"")) {
                    throw new TikaConfigException("Cannot change 'basePath' at runtime for security reasons. " + "basePath can only be set during initialization.");
                }

                // Load runtime config (excludes basePath for security)
                FileSystemEmitterRuntimeConfig runtimeConfig = FileSystemEmitterRuntimeConfig.load(configJson.json());

                // Merge runtime config into default config while preserving basePath and the
                // init-time allowAbsolutePaths -- neither may be changed at runtime.
                config = new FileSystemEmitterConfig(fileSystemEmitterConfig.basePath(), runtimeConfig.getFileExtension(), runtimeConfig.getOnExists(),
                        runtimeConfig.isPrettyPrint(), fileSystemEmitterConfig.allowAbsolutePaths(),
                        fileSystemEmitterConfig.atomicWrites());
                checkConfig(config);
            }
        }
        return config;
    }
}
