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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.fetcher.AbstractFetcher;

public class FileSystemFetcher extends AbstractFetcher implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemFetcher.class);

    //Warning! basePath can be null!
    private Path basePath = null;

    private boolean extractFileSystemMetadata = false;

    static boolean isDescendant(Path root, Path descendant) {
        return descendant.toAbsolutePath().normalize()
                .startsWith(root.toAbsolutePath().normalize());
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws IOException, TikaException {

        if (fetchKey.contains("\u0000")) {
            throw new IllegalArgumentException("Path must not contain \u0000. " +
                    "Please review the life decisions that led you to requesting " +
                    "a file name with this character in it.");
        }
        Path p = null;
        if (basePath != null) {
            p = basePath.resolve(fetchKey);
            if (!p.toRealPath().startsWith(basePath.toRealPath())) {
                throw new IllegalArgumentException(
                        "fetchKey must resolve to be a descendant of the 'basePath'");
            }
        } else {
            p = Paths.get(fetchKey);
        }

        metadata.set(TikaCoreProperties.SOURCE_PATH, fetchKey);
        updateFileSystemMetadata(p, metadata);

        if (!Files.isRegularFile(p)) {
            if (basePath != null && !Files.isDirectory(basePath)) {
                throw new IOException("BasePath is not a directory: " + basePath);
            } else {
                throw new FileNotFoundException(p.toAbsolutePath().toString());
            }
        }

        return TikaInputStream.get(p, metadata);
    }

    private void updateFileSystemMetadata(Path p, Metadata metadata) throws IOException {
        if (! extractFileSystemMetadata) {
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

    /**
     *
     * @return the basePath or <code>null</code> if no base path was set
     */
    public Path getBasePath() {
        return basePath;
    }

    /**
     * Default behavior si that clients will send in relative paths, this
     * must be set to allow this fetcher to fetch the
     * full path.
     *
     * @param basePath
     */
    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }

    /**
     * Extract file system metadata (created, modified, accessed) when fetching file.
     * The default is <code>false</code>.
     *
     * @param extractFileSystemMetadata
     */
    @Field
    public void setExtractFileSystemMetadata(boolean extractFileSystemMetadata) {
        this.extractFileSystemMetadata = extractFileSystemMetadata;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (basePath == null || basePath.toString().trim().length() == 0) {
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

        if (basePath.toAbsolutePath().toString().contains("\u0000")) {
            throw new TikaConfigException(
                    "base path must not contain \u0000. " + "Seriously, what were you thinking?");
        }
    }
}
