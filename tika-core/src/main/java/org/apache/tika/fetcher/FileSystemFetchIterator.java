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
package org.apache.tika.fetcher;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.ServerRuntimeException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class FileSystemFetchIterator extends FetchIterator implements Initializable {

    private static final String NAME = "fs";
    private Path basePath;
    private String fetchPrefix;

    public FileSystemFetchIterator() {
        super(NAME);
    }

    public FileSystemFetchIterator(String fetchPrefix, Path basePath) {
        super(NAME);
        this.fetchPrefix = fetchPrefix;
        this.basePath = basePath;
    }

    /**
     * fetchPrefix not including the colon (:), e.g. "fs"
     * @param fetchPrefix
     */
    @Field
    public void setFetchPrefix(String fetchPrefix) {
        this.fetchPrefix = fetchPrefix;
    }

    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }

    @Override
    protected void enqueue() throws IOException, TimeoutException {

        try {
            Files.walkFileTree(basePath, new FSFileVisitor());
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof TimeoutException) {
                throw (TimeoutException) cause;
            }
            throw e;
        }
        try {
            tryToAdd(POISON);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        if (basePath == null) {
            throw new TikaConfigException("Must specify a \"basePath\"");
        }
        if (! Files.isDirectory(basePath)) {
            throw new TikaConfigException("\"root\" directory does not exist: " +
                    basePath.toAbsolutePath());
        }
        if (fetchPrefix == null || fetchPrefix.trim().length() == 0) {
            throw new TikaConfigException("\"fetchPrefix\" must be specified and must be not blank");
        }
        if (fetchPrefix.contains(":")) {
            throw new TikaConfigException("\"fetchPrefix\" must not contain a colon (:)");
        }
    }


    private class FSFileVisitor implements FileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relPath = basePath.relativize(file).toString();
            String fetcherString = fetchPrefix + ":" + relPath;

            try {
                tryToAdd(new FetchMetadataPair(fetcherString, new Metadata()));
            } catch (TimeoutException e) {
                throw new IOException(e);
            } catch (InterruptedException e) {
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }


}
