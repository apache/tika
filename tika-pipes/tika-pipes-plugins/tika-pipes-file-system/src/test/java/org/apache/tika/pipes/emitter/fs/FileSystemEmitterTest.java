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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.plugins.ExtensionConfig;

public class FileSystemEmitterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Emitter createEmitter(Path basePath, Boolean allowAbsolutePaths)
            throws TikaConfigException, IOException {
        ObjectNode config = MAPPER.createObjectNode();
        if (basePath != null) {
            config.put("basePath", basePath.toAbsolutePath().toString());
        }
        if (allowAbsolutePaths != null) {
            config.put("allowAbsolutePaths", allowAbsolutePaths);
        }
        config.put("onExists", "REPLACE");
        ExtensionConfig pluginConfig = new ExtensionConfig("test", "test", config.toString());
        return new FileSystemEmitterFactory().buildExtension(pluginConfig);
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
}
