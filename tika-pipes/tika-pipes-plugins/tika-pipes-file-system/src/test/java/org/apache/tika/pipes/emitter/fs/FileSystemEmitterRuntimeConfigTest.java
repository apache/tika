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
package org.apache.tika.pipes.emitter.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Tests runtime configuration of FileSystemEmitter via ParseContext's jsonConfigs.
 */
public class FileSystemEmitterRuntimeConfigTest {

    @Test
    public void testRuntimeConfigCannotOverrideBasePath(@TempDir Path tempDir) throws Exception {
        // Create two output directories
        Path dir1 = tempDir.resolve("output1");
        Path dir2 = tempDir.resolve("output2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        // Create emitter with dir1 as default basePath
        String defaultConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                dir1.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-emitter", "test", defaultConfig);
        FileSystemEmitter emitter = FileSystemEmitter.build(pluginConfig);

        // Emit with default config
        List<Metadata> metadataList1 = new ArrayList<>();
        Metadata m1 = new Metadata();
        m1.set(TikaCoreProperties.TIKA_CONTENT, "content1");
        metadataList1.add(m1);

        ParseContext context1 = new ParseContext();
        emitter.emit("test1.json", metadataList1, context1);

        Path output1 = dir1.resolve("test1.json");
        assertTrue(Files.exists(output1), "File should be created in dir1");

        // Try to override basePath at runtime to point to dir2
        // This should throw an exception for security reasons
        String runtimeConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                dir2.toString().replace("\\", "\\\\"));

        ParseContext context2 = new ParseContext();
        context2.setJsonConfig("test-emitter", runtimeConfig);

        // Emit with runtime config - should throw exception
        List<Metadata> metadataList2 = new ArrayList<>();
        Metadata m2 = new Metadata();
        m2.set(TikaCoreProperties.TIKA_CONTENT, "content2");
        metadataList2.add(m2);

        IOException exception = assertThrows(IOException.class, () -> {
            emitter.emit("test2.json", metadataList2, context2);
        });
        assertTrue(exception.getCause() != null &&
                exception.getCause().getMessage().contains("Cannot change 'basePath' at runtime"),
                "Should throw exception when attempting to change basePath at runtime");
    }

    @Test
    public void testRuntimeConfigFileExtension(@TempDir Path tempDir) throws Exception {
        // Create emitter with no file extension
        String defaultConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-emitter", "test", defaultConfig);
        FileSystemEmitter emitter = FileSystemEmitter.build(pluginConfig);

        // Emit with default config - no extension added
        List<Metadata> metadataList1 = new ArrayList<>();
        Metadata m1 = new Metadata();
        m1.set(TikaCoreProperties.TIKA_CONTENT, "content1");
        metadataList1.add(m1);

        ParseContext context1 = new ParseContext();
        emitter.emit("test1", metadataList1, context1);

        assertTrue(Files.exists(tempDir.resolve("test1")),
                "File without extension should exist");

        // Override at runtime to add .json extension
        // Note: basePath is NOT included for security reasons
        String runtimeConfig = "{\"fileExtension\":\"json\", \"onExists\":\"REPLACE\"}";

        ParseContext context2 = new ParseContext();
        context2.setJsonConfig("test-emitter", runtimeConfig);

        // Emit with runtime config
        List<Metadata> metadataList2 = new ArrayList<>();
        Metadata m2 = new Metadata();
        m2.set(TikaCoreProperties.TIKA_CONTENT, "content2");
        metadataList2.add(m2);

        emitter.emit("test2", metadataList2, context2);

        assertTrue(Files.exists(tempDir.resolve("test2.json")),
                "File with .json extension should exist");
    }

    @Test
    public void testRuntimeConfigOnExists(@TempDir Path tempDir) throws Exception {
        // Create emitter with REPLACE as default
        String defaultConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-emitter", "test", defaultConfig);
        FileSystemEmitter emitter = FileSystemEmitter.build(pluginConfig);

        // Create a test file using stream emit
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "original content");

        // Emit with default config (REPLACE) - should succeed
        InputStream inputStream1 = new ByteArrayInputStream("replaced content".getBytes(StandardCharsets.UTF_8));
        Metadata metadata1 = new Metadata();
        ParseContext context1 = new ParseContext();

        emitter.emit("test.txt", inputStream1, metadata1, context1);
        assertEquals("replaced content", Files.readString(testFile),
                "Content should be replaced");

        // Override at runtime to use SKIP
        // Note: basePath is NOT included for security reasons
        String runtimeConfig = "{\"onExists\":\"SKIP\"}";

        ParseContext context2 = new ParseContext();
        context2.setJsonConfig("test-emitter", runtimeConfig);

        // Emit with runtime config (SKIP) - should not replace existing file
        InputStream inputStream2 = new ByteArrayInputStream("new content".getBytes(StandardCharsets.UTF_8));
        Metadata metadata2 = new Metadata();

        emitter.emit("test.txt", inputStream2, metadata2, context2);
        assertEquals("replaced content", Files.readString(testFile),
                "Content should not change with SKIP");
    }

    @Test
    public void testJsonConfigNotPresent(@TempDir Path tempDir) throws Exception {
        // Create emitter with default config
        String defaultConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-emitter", "test", defaultConfig);
        FileSystemEmitter emitter = FileSystemEmitter.build(pluginConfig);

        // Emit with ParseContext that has no jsonConfigs - should use default config
        List<Metadata> metadataList = new ArrayList<>();
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.TIKA_CONTENT, "test content");
        metadataList.add(m);

        ParseContext context = new ParseContext();
        // Don't set jsonConfigs in context

        emitter.emit("test.json", metadataList, context);

        Path output = tempDir.resolve("test.json");
        assertTrue(Files.exists(output), "File should be created with default config");
    }

    @Test
    public void testJsonConfigWithDifferentId(@TempDir Path tempDir) throws Exception {
        // Create emitter with default config
        String defaultConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                tempDir.toString().replace("\\", "\\\\"));
        ExtensionConfig pluginConfig = new ExtensionConfig("test-emitter", "test", defaultConfig);
        FileSystemEmitter emitter = FileSystemEmitter.build(pluginConfig);

        // Create jsonConfigs with config for a different emitter ID
        Path otherDir = tempDir.resolve("other");
        Files.createDirectories(otherDir);

        String runtimeConfig = String.format(Locale.ROOT,
                "{\"basePath\":\"%s\", \"onExists\":\"REPLACE\"}",
                otherDir.toString().replace("\\", "\\\\"));

        ParseContext context = new ParseContext();
        context.setJsonConfig("different-emitter", runtimeConfig);

        // Emit - should use default config since runtime config is for different ID
        List<Metadata> metadataList = new ArrayList<>();
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.TIKA_CONTENT, "test content");
        metadataList.add(m);

        emitter.emit("test.json", metadataList, context);

        assertTrue(Files.exists(tempDir.resolve("test.json")),
                "File should be created in default basePath");
        assertFalse(Files.exists(otherDir.resolve("test.json")),
                "File should not be created in other directory");
    }
}
