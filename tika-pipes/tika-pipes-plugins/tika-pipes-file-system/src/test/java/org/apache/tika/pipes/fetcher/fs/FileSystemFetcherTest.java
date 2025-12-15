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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.plugins.ExtensionConfig;


public class FileSystemFetcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Fetcher createFetcher(Path basePath, Boolean allowAbsolutePaths)
            throws TikaConfigException {
        ObjectNode config = MAPPER.createObjectNode();
        if (basePath != null) {
            config.put("basePath", basePath.toAbsolutePath().toString());
        }
        if (allowAbsolutePaths != null) {
            config.put("allowAbsolutePaths", allowAbsolutePaths);
        }
        ExtensionConfig pluginConfig = new ExtensionConfig("test", "test", config.toString());
        return new FileSystemFetcherFactory().buildExtension(pluginConfig);
    }

    @Test
    public void testNullByte() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            ObjectNode config = MAPPER.createObjectNode();
            config.put("basePath", "bad\u0000path");
            ExtensionConfig pluginConfig = new ExtensionConfig("test", "test", config.toString());
            new FileSystemFetcherFactory().buildExtension(pluginConfig);
        });
    }

    @Test
    public void testPathTraversalBlocked() throws Exception {
        // Create a subdirectory as basePath and a file outside it
        Path basePath = tempDir.resolve("allowed");
        Files.createDirectories(basePath);

        Path fileInBase = basePath.resolve("safe.txt");
        Files.writeString(fileInBase, "safe content");

        Path fileOutsideBase = tempDir.resolve("secret.txt");
        Files.writeString(fileOutsideBase, "secret content");

        // Create fetcher with basePath set to the subdirectory
        Fetcher fetcher = createFetcher(basePath, null);

        // Valid path within basePath should work
        try (TikaInputStream tis = fetcher.fetch("safe.txt", new Metadata(), new ParseContext())) {
            assertNotNull(tis);
        }

        // Path traversal attempt should be rejected
        assertThrows(SecurityException.class, () -> {
            fetcher.fetch("../secret.txt", new Metadata(), new ParseContext());
        });
    }

    @Test
    public void testDeepPathTraversalBlocked() throws Exception {
        // Create nested directories
        Path basePath = tempDir.resolve("a/b/c");
        Files.createDirectories(basePath);

        Path fileInBase = basePath.resolve("file.txt");
        Files.writeString(fileInBase, "nested content");

        Path fileOutsideBase = tempDir.resolve("outside.txt");
        Files.writeString(fileOutsideBase, "outside content");

        Fetcher fetcher = createFetcher(basePath, null);

        // Deep path traversal should be rejected
        assertThrows(SecurityException.class, () -> {
            fetcher.fetch("../../../outside.txt", new Metadata(), new ParseContext());
        });

        // Even deeper traversal should be rejected
        assertThrows(SecurityException.class, () -> {
            fetcher.fetch("../../../../../../../../etc/passwd", new Metadata(), new ParseContext());
        });
    }

    @Test
    public void testAllowAbsolutePathsRequired() throws Exception {
        // Without basePath and without allowAbsolutePaths, should throw
        assertThrows(TikaConfigException.class, () -> {
            createFetcher(null, null);
        });
    }

    @Test
    public void testAllowAbsolutePathsWorks() throws Exception {
        // Create a file to fetch
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // With allowAbsolutePaths=true and no basePath, should work
        Fetcher fetcher = createFetcher(null, true);

        // Fetch using absolute path
        try (TikaInputStream tis = fetcher.fetch(
                testFile.toAbsolutePath().toString(), new Metadata(), new ParseContext())) {
            assertNotNull(tis);
        }
    }
}
