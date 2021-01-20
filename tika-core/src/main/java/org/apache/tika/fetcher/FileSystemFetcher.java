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
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

public class FileSystemFetcher implements Fetcher {

    private static String PREFIX = "fs";
    private static final Set<String> SUPPORTED = Collections.singleton(PREFIX);
    private Path basePath = null;
    @Override
    public Set<String> getSupportedPrefixes() {
        return SUPPORTED;
    }

    @Override
    public InputStream fetch(String fetcherString, Metadata metadata)
            throws IOException, TikaException {
        FetchPrefixKeyPair fetchPrefixKeyPair = FetchPrefixKeyPair.create(fetcherString);
        metadata.set(TikaCoreProperties.SOURCE_PATH, fetchPrefixKeyPair.getKey());
        Path p = null;
        if (basePath != null) {
            p = basePath.resolve(fetchPrefixKeyPair.getKey());
            if (!Files.isRegularFile(p)) {
                if (!Files.isDirectory(basePath)) {
                    throw new IOException("BasePath is not a directory: "+basePath);
                } else {
                    throw new FileNotFoundException(p.toAbsolutePath().toString());
                }
            }
        } else {
            p = Paths.get(fetchPrefixKeyPair.getKey());
            if (!Files.isRegularFile(p)) {
                throw new FileNotFoundException(p.toAbsolutePath().toString());
            }
        }
        return TikaInputStream.get(p, metadata);
    }

    /**
     * If clients will send in relative paths, this
     * must be set to allow this fetcher to fetch the
     * full path.
     *
     * @param basePath
     */
    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }
}
