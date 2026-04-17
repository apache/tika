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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * PipesIterator that reads file paths from a text file (one path per line).
 * <p>
 * If a {@code basePath} is provided, lines are treated as relative paths
 * under that directory.  The fetch key uses the relative path so that the
 * file-system fetcher (whose basePath is the input directory) can resolve it.
 * <p>
 * Blank lines and lines starting with {@code #} are skipped.
 */
class FileListPipesIterator implements PipesIterator {

    private final Path fileListPath;
    private final Path basePath;

    FileListPipesIterator(Path fileListPath, Path basePath) {
        this.fileListPath = fileListPath;
        this.basePath = basePath;
    }

    @Override
    public Iterator<FetchEmitTuple> iterator() {
        BufferedReader reader;
        try {
            reader = Files.newBufferedReader(fileListPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open file list: " + fileListPath, e);
        }

        return new Iterator<>() {
            private FetchEmitTuple next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                if (done) {
                    return false;
                }
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            next = new FetchEmitTuple(
                                    line,
                                    new FetchKey(TikaConfigAsyncWriter.FETCHER_NAME, line),
                                    new EmitKey(TikaConfigAsyncWriter.EMITTER_NAME, line));
                            return true;
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed reading file list", e);
                }
                done = true;
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
                return false;
            }

            @Override
            public FetchEmitTuple next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                FetchEmitTuple t = next;
                next = null;
                return t;
            }
        };
    }

    @Override
    public Integer call() throws Exception {
        return (int) Files.lines(fileListPath)
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .count();
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return null;
    }
}
