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
package org.apache.tika.pipes.fetcher.fs.config;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class FileSystemFetcherConfig extends AbstractConfig {
    private String basePath;
    private boolean extractFileSystemMetadata;

    public String getBasePath() {
        return basePath;
    }

    public FileSystemFetcherConfig setBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    public boolean isExtractFileSystemMetadata() {
        return extractFileSystemMetadata;
    }

    public FileSystemFetcherConfig setExtractFileSystemMetadata(boolean extractFileSystemMetadata) {
        this.extractFileSystemMetadata = extractFileSystemMetadata;
        return this;
    }
}
