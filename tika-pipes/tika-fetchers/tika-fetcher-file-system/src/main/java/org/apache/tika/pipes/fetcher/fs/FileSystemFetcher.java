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
package org.apache.tika.pipes.fetcher.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Optional;

import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.ExtensionConfigs;
import org.apache.tika.utils.StringUtils;

/**
 * Fetches files from a local/mounted file system.
 * Config:
 * <pre>{@code
 * "file-system-fetcher": {
 * "basePath": "BASE_PATH",
 * "extractFileSystemMetadata": false
 * }
 * }
 * </pre>
 */

public class FileSystemFetcher extends AbstractTikaExtension implements Fetcher {



    public static FileSystemFetcher build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        FileSystemFetcher fetcher = new FileSystemFetcher(pluginConfig);
        fetcher.configure();
        return fetcher;
    }


    private static final Logger LOG = LoggerFactory.getLogger(FileSystemFetcher.class);

    private FileSystemFetcherConfig defaultFileSystemFetcherConfig;

    public FileSystemFetcher(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    private void configure() throws IOException, TikaConfigException {
        defaultFileSystemFetcherConfig = FileSystemFetcherConfig.load(pluginConfig.jsonConfig());
        checkConfig(defaultFileSystemFetcherConfig);
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws IOException, TikaException {
        if (fetchKey.contains("\u0000")) {
            throw new IllegalArgumentException("Path must not contain 'u0000'. " +
                    "Please review the life decisions that led you to requesting " +
                    "a file name with this character in it.");
        }
        FileSystemFetcherConfig config = defaultFileSystemFetcherConfig;
        ExtensionConfigs pluginConfigManager = parseContext.get(ExtensionConfigs.class);
        if (pluginConfigManager != null) {
            Optional<ExtensionConfig> pluginConfigOpt = pluginConfigManager.getById(getExtensionConfig().id());
            if (pluginConfigOpt.isPresent()) {
                ExtensionConfig pluginConfig = pluginConfigOpt.get();
                config = FileSystemFetcherConfig.load(pluginConfig.jsonConfig());
                checkConfig(config);
            }
        }
        Path p = null;
        if (! StringUtils.isBlank(config.getBasePath())) {
            Path basePath = Paths.get(config.getBasePath());
            if (!Files.isDirectory(basePath)) {
                throw new IOException("BasePath is not a directory: " + basePath);
            }
            p = basePath.resolve(fetchKey);
            if (!p.toRealPath().startsWith(basePath.toRealPath())) {
                throw new IllegalArgumentException(
                        "fetchKey must resolve to be a descendant of the 'basePath'");
            }
        }

        metadata.set(TikaCoreProperties.SOURCE_PATH, fetchKey);
        LOG.trace("about to read from {} with base={}", p.toAbsolutePath(), config.getBasePath());
        if (!Files.isRegularFile(p)) {
            throw new FileNotFoundException(p.toAbsolutePath().toString());
        }
        updateFileSystemMetadata(p, metadata, config);

        return TikaInputStream.get(p, metadata);
    }


    private void updateFileSystemMetadata(Path p, Metadata metadata, FileSystemFetcherConfig config) throws IOException {
        if (! config.isExtractFileSystemMetadata()) {
            return;
        }
        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
        updateFileTime(FileSystem.CREATED, attrs.creationTime(), metadata);
        updateFileTime(FileSystem.MODIFIED, attrs.lastModifiedTime(), metadata);
        updateFileTime(FileSystem.ACCESSED, attrs.lastAccessTime(), metadata);
        //TODO extract owner or group?
    }

    private void updateFileTime(Property property, FileTime fileTime, Metadata metadata) {
        if (fileTime == null) {
            return;
        }
        metadata.set(property, new Date(fileTime.toMillis()));
    }

    private void checkConfig(FileSystemFetcherConfig fetcherConfig) throws TikaConfigException {
        String basePath = fetcherConfig.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            LOG.warn("'basePath' has not been set. " +
                    "This means that client code or clients can read from any file that this " +
                    "process has permissions to read. If you are running tika-server, make " +
                    "absolutely certain that you've locked down " +
                    "access to tika-server and file-permissions for the tika-server process.");
            return;
        }
        if (basePath.toString().startsWith("http://")) {
            throw new TikaConfigException("FileSystemFetcher only works with local file systems. " +
                    " Please use the tika-fetcher-http module for http calls");
        } else if (basePath.toString().startsWith("ftp://")) {
            throw new TikaConfigException("FileSystemFetcher only works with local file systems. " +
                    " Please consider contributing an ftp fetcher module");
        } else if (basePath.toString().startsWith("s3://")) {
            throw new TikaConfigException("FileSystemFetcher only works with local file systems. " +
                    " Please use the tika-fetcher-s3 module");
        }

        if (basePath.contains("\u0000")) {
            throw new TikaConfigException(
                    "base path must not contain \u0000. " + "Seriously, what were you thinking?");
        }
    }

    static boolean isDescendant(Path root, Path descendant) {
        return descendant.toAbsolutePath().normalize()
                         .startsWith(root.toAbsolutePath().normalize());
    }

    @Override
    public String toString() {
        return "FileSystemFetcher{" + "defaultFileSystemFetcherConfig=" + defaultFileSystemFetcherConfig + ", pluginConfig=" + pluginConfig + '}';
    }
}
