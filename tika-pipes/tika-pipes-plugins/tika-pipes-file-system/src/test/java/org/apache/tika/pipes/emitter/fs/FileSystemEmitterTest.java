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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.plugins.ExtensionConfig;

public class FileSystemEmitterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Emitter createEmitter(Path basePath, Boolean allowAbsolutePaths)
            throws TikaConfigException, IOException {
        return createEmitter(basePath, allowAbsolutePaths, "REPLACE");
    }

    private StreamEmitter createEmitter(Path basePath, Boolean allowAbsolutePaths, String onExists)
            throws TikaConfigException, IOException {
        ObjectNode config = MAPPER.createObjectNode();
        if (basePath != null) {
            config.put("basePath", basePath.toAbsolutePath().toString());
        }
        if (allowAbsolutePaths != null) {
            config.put("allowAbsolutePaths", allowAbsolutePaths);
        }
        config.put("onExists", onExists);
        ExtensionConfig pluginConfig = new ExtensionConfig("test", "test", config.toString());
        return (StreamEmitter) new FileSystemEmitterFactory().buildExtension(pluginConfig);
    }

    @Test
    public void testAllowAbsolutePathsRequired() throws Exception {
        // Without basePath and without allowAbsolutePaths, the emitter would write client-controlled
        // keys to arbitrary paths -- build must refuse it (mirrors FileSystemFetcher).
        assertThrows(TikaConfigException.class, () -> createEmitter(null, null));
    }

    @Test
    public void testAllowAbsolutePathsWorks() throws Exception {
        // With allowAbsolutePaths=true and no basePath, the operator has explicitly accepted the
        // risk, so an absolute emit key is written.
        Emitter emitter = createEmitter(null, true);
        Path out = tempDir.resolve("out/result.json");
        emitter.emit(out.toAbsolutePath().toString(), List.of(new Metadata()), new ParseContext());
        assertTrue(Files.isRegularFile(out), "absolute emit key should have been written");
    }

    @Test
    public void testPathTraversalBlocked() throws Exception {
        Path basePath = tempDir.resolve("allowed");
        Files.createDirectories(basePath);
        Emitter emitter = createEmitter(basePath, null);
        // An emit key escaping basePath must be rejected, even with basePath set.
        assertThrows(IOException.class, () -> emitter.emit(
                "../escaped.json", List.of(new Metadata()), new ParseContext()));
    }

    private Path seed(Path basePath, String name, String content) throws IOException {
        Files.createDirectories(basePath);
        Path existing = basePath.resolve(name);
        Files.writeString(existing, content);
        return existing;
    }

    private static long tmpFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(FileSystemEmitter.TMP_SUFFIX))
                    .count();
        }
    }

    @Test
    public void testOnExistsExceptionLeavesOriginalIntact() throws Exception {
        Path basePath = tempDir.resolve("base");
        Path existing = seed(basePath, "a.json", "original");
        StreamEmitter emitter = createEmitter(basePath, null, "EXCEPTION");
        assertThrows(IOException.class, () ->
                emitter.emit("a.json", List.of(new Metadata()), new ParseContext()));
        assertThrows(IOException.class, () -> emitter.emit("a.json",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), new Metadata(),
                new ParseContext()));
        assertEquals("original", Files.readString(existing));
        assertEquals(0, tmpFiles(basePath), "tmp file leaked");
    }

    @Test
    public void testOnExistsSkipLeavesOriginalIntact() throws Exception {
        Path basePath = tempDir.resolve("base");
        Path existing = seed(basePath, "a.json", "original");
        StreamEmitter emitter = createEmitter(basePath, null, "SKIP");
        emitter.emit("a.json", List.of(new Metadata()), new ParseContext());
        emitter.emit("a.json", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                new Metadata(), new ParseContext());
        assertEquals("original", Files.readString(existing));
        assertEquals(0, tmpFiles(basePath), "tmp file leaked");
    }

    @Test
    public void testOnExistsReplaceOverwrites() throws Exception {
        Path basePath = tempDir.resolve("base");
        Path existing = seed(basePath, "a.json", "original");
        StreamEmitter emitter = createEmitter(basePath, null, "REPLACE");
        emitter.emit("a.json", List.of(new Metadata()), new ParseContext());
        assertFalse(Files.readString(existing).equals("original"));
        emitter.emit("a.json", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                new Metadata(), new ParseContext());
        assertEquals("x", Files.readString(existing));
        assertEquals(0, tmpFiles(basePath), "tmp file leaked");
    }

    @Test
    public void testReaderNeverSeesPartialFile() throws Exception {
        // Regression for the AsyncResourceTest flake: a poller that reads as soon as the
        // output exists must get the whole file, never an empty one mid-write.
        Path basePath = tempDir.resolve("base");
        Files.createDirectories(basePath);
        StreamEmitter emitter = createEmitter(basePath, null, "REPLACE");
        Path out = basePath.resolve("big.json");
        Metadata m = new Metadata();
        m.set("x", "y".repeat(1 << 20));
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    emitter.emit("big.json", List.of(m), new ParseContext());
                    Files.delete(out);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writer.start();
        long minSeen = Long.MAX_VALUE;
        while (writer.isAlive()) {
            try {
                minSeen = Math.min(minSeen, Files.size(out));
            } catch (IOException e) {
                //between delete and next publish
            }
        }
        writer.join();
        assertTrue(minSeen == Long.MAX_VALUE || minSeen > 1 << 20,
                "observed partial file of size " + minSeen);
    }
}
