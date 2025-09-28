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
package org.apache.tika.pipes.fetchers.filesystem;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.core.exception.TikaPipesException;
import org.apache.tika.pipes.fetchers.core.Fetcher;
import org.apache.tika.pipes.fetchers.core.FetcherConfig;

@Extension
public class FileSystemFetcher implements Fetcher {
    @Override
    public InputStream fetch(FetcherConfig fetcherConfig, String fetchKey, Map<String, Object> fetchMetadata, Map<String, Object> responseMetadata) {
        FileSystemFetcherConfig fileSystemFetcherConfig = (FileSystemFetcherConfig) fetcherConfig;
        try {
            if (fetchKey.contains("\u0000")) {
                throw new IllegalArgumentException("Path must not contain 'u0000'.");
            }
            Path basePath = StringUtils.isBlank(fileSystemFetcherConfig.getBasePath()) ?
                    null : Paths.get(fileSystemFetcherConfig.getBasePath());
            Path pathToFetch;
            if (basePath != null) {
                pathToFetch = basePath.resolve(fetchKey);
                if (!pathToFetch
                        .toRealPath()
                        .startsWith(basePath.toRealPath())) {
                    throw new IllegalArgumentException("fetchKey must resolve to be a descendant of the 'basePath'");
                }
            } else {
                pathToFetch = Paths.get(fetchKey);
            }

            responseMetadata.put(TikaCoreProperties.SOURCE_PATH.getName(), fetchKey);
            updateFileSystemMetadata(fileSystemFetcherConfig, pathToFetch, responseMetadata);

            if (!Files.isRegularFile(pathToFetch)) {
                if (basePath != null && !Files.isDirectory(basePath)) {
                    throw new IOException("BasePath is not a directory: " + basePath);
                } else {
                    throw new FileNotFoundException(pathToFetch
                            .toAbsolutePath()
                            .toString());
                }
            }

            return new FileInputStream(pathToFetch.toFile());
        } catch (IOException e) {
            throw new TikaPipesException("Could not fetch " + fetchKey, e);
        }
    }

    private void updateFileSystemMetadata(FileSystemFetcherConfig fileSystemFetcherConfig, Path p, Map<String, Object> responseMetadata) throws IOException {
        if (!fileSystemFetcherConfig.isExtractFileSystemMetadata()) {
            return;
        }
        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
        updateFileTime(FileSystem.CREATED, attrs.creationTime(), responseMetadata);
        updateFileTime(FileSystem.MODIFIED, attrs.lastModifiedTime(), responseMetadata);
        updateFileTime(FileSystem.ACCESSED, attrs.lastAccessTime(), responseMetadata);
    }

    private void updateFileTime(Property property, FileTime fileTime, Map<String, Object> responseMetadata) {
        if (fileTime == null) {
            return;
        }
        responseMetadata.put(property.getName(), new Date(fileTime.toMillis()));
    }
}
