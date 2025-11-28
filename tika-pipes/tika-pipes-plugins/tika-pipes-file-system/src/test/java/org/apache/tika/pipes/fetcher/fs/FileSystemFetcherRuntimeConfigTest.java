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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Tests runtime configuration of FileSystemFetcher via ConfigContainer and ParseContext.
 */
public class FileSystemFetcherRuntimeConfigTest {

    @Test
    public void testRuntimeConfigViaParseContext(@TempDir Path tempDir) throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Create fetcher with default config (no extractFileSystemMetadata)
        String defaultConfig = String.format(Locale.ROOT, "{\"basePath\":\"%s\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-fetcher", "test", defaultConfig);
        FileSystemFetcher fetcher = new FileSystemFetcher(pluginConfig);

        // Fetch without runtime config - should not extract file system metadata
        Metadata metadata1 = new Metadata();
        ParseContext context1 = new ParseContext();
        try (InputStream is = fetcher.fetch("test.txt", metadata1, context1)) {
            assertNotNull(is);
        }
        assertNull(metadata1.get(FileSystem.CREATED),
                "Without extractFileSystemMetadata, should not have CREATED metadata");

        // Now create runtime config with extractFileSystemMetadata=true
        // Note: basePath is NOT included for security reasons
        String runtimeConfig = "{\"extractFileSystemMetadata\":true}";

        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("test-fetcher", runtimeConfig);

        ParseContext context2 = new ParseContext();
        context2.set(ConfigContainer.class, configContainer);

        // Fetch with runtime config - should extract file system metadata
        Metadata metadata2 = new Metadata();
        try (InputStream is = fetcher.fetch("test.txt", metadata2, context2)) {
            assertNotNull(is);
        }
        assertNotNull(metadata2.get(FileSystem.CREATED),
                "With extractFileSystemMetadata=true, should have CREATED metadata");
        assertNotNull(metadata2.get(FileSystem.MODIFIED),
                "With extractFileSystemMetadata=true, should have MODIFIED metadata");
    }

    @Test
    public void testRuntimeConfigCannotOverrideBasePath(@TempDir Path tempDir) throws Exception {
        // Create two directories with different files
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        Path file1 = dir1.resolve("test.txt");
        Files.writeString(file1, "content from dir1");

        // Create fetcher with dir1 as default basePath
        String defaultConfig = String.format(Locale.ROOT, "{\"basePath\":\"%s\"}",
                dir1.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-fetcher", "test", defaultConfig);
        FileSystemFetcher fetcher = new FileSystemFetcher(pluginConfig);

        // Fetch from default basePath (dir1)
        Metadata metadata1 = new Metadata();
        ParseContext context1 = new ParseContext();
        try (InputStream is = fetcher.fetch("test.txt", metadata1, context1)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("content from dir1", content);
        }

        // Try to override basePath at runtime to point to dir2
        // This should throw an exception for security reasons
        String runtimeConfig = String.format(Locale.ROOT, "{\"basePath\":\"%s\"}",
                dir2.toString().replace("\\", "\\\\"));
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("test-fetcher", runtimeConfig);

        ParseContext context2 = new ParseContext();
        context2.set(ConfigContainer.class, configContainer);

        // Fetch with runtime config - should throw exception
        Metadata metadata2 = new Metadata();
        IOException exception = assertThrows(IOException.class, () -> {
            fetcher.fetch("test.txt", metadata2, context2);
        });
        assertTrue(exception.getCause() != null &&
                exception.getCause().getMessage().contains("Cannot change 'basePath' at runtime"),
                "Should throw exception when attempting to change basePath at runtime");
    }

    @Test
    public void testConfigContainerNotPresent(@TempDir Path tempDir) throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Create fetcher with default config
        String defaultConfig = String.format(Locale.ROOT, "{\"basePath\":\"%s\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-fetcher", "test", defaultConfig);
        FileSystemFetcher fetcher = new FileSystemFetcher(pluginConfig);

        // Fetch with ParseContext that has no ConfigContainer - should use default config
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        // Don't set ConfigContainer in context

        try (InputStream is = fetcher.fetch("test.txt", metadata, context)) {
            assertNotNull(is);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("test content", content);
        }
    }

    @Test
    public void testConfigContainerWithDifferentId(@TempDir Path tempDir) throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Create fetcher with default config
        String defaultConfig = String.format(Locale.ROOT, "{\"basePath\":\"%s\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-fetcher", "test", defaultConfig);
        FileSystemFetcher fetcher = new FileSystemFetcher(pluginConfig);

        // Create ConfigContainer with config for a different fetcher ID
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("different-fetcher", "{\"basePath\":\"/some/other/path\"}");

        ParseContext context = new ParseContext();
        context.set(ConfigContainer.class, configContainer);

        // Fetch - should use default config since runtime config is for different ID
        Metadata metadata = new Metadata();
        try (InputStream is = fetcher.fetch("test.txt", metadata, context)) {
            assertNotNull(is);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("test content", content);
        }
    }
}
