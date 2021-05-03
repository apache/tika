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
package org.apache.tika.pipes.fetcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class FileSystemFetcher extends AbstractFetcher implements Initializable {

    private Path basePath = null;

    static boolean isDescendant(Path root, Path descendant) {
        return descendant.toAbsolutePath().normalize()
                .startsWith(root.toAbsolutePath().normalize());
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws IOException, TikaException {
        if (basePath == null) {
            throw new IllegalStateException("must set 'basePath' before calling fetch");

        }
        if (fetchKey.contains("\u0000")) {
            throw new IllegalArgumentException("Path must not contain \u0000. " +
                    "Please review the life decisions that led you to requesting " +
                    "a file name with this character in it.");
        }
        Path p = basePath.resolve(fetchKey);
        if (!p.toRealPath().startsWith(basePath.toRealPath())) {
            throw new IllegalArgumentException(
                    "fetchKey must resolve to be a" + " descendant of the 'basePath'");
        }

        metadata.set(TikaCoreProperties.SOURCE_PATH, fetchKey);

        if (!Files.isRegularFile(p)) {
            if (!Files.isDirectory(basePath)) {
                throw new IOException("BasePath is not a directory: " + basePath);
            } else {
                throw new FileNotFoundException(p.toAbsolutePath().toString());
            }
        }

        return TikaInputStream.get(p, metadata);
    }

    public Path getBasePath() {
        return basePath;
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

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (basePath == null || basePath.toString().trim().length() == 0) {
            throw new TikaConfigException("'basePath' must be specified");
        }
        if (basePath.toAbsolutePath().toString().contains("\u0000")) {
            throw new TikaConfigException(
                    "base path must not contain \u0000. " + "Seriously, what were you thinking?");
        }
    }
}
