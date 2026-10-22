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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.JsonMetadataList;

public class FileSystemEmitterTest {

    @TempDir
    Path tmp;

    @Test
    public void testGzip() throws Exception {
        FileSystemEmitter emitter = emitter("GZIP");
        emitter.emit("sub/a.pdf", metadataList(), new ParseContext());

        Path gz = tmp.resolve("sub/a.pdf.json.gz");
        assertTrue(Files.isRegularFile(gz));
        assertFalse(Files.exists(tmp.resolve("sub/a.pdf.json")));
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(gz)),
                StandardCharsets.UTF_8)) {
            List<Metadata> back = JsonMetadataList.fromJson(r);
            assertEquals("hello world", back.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        }
    }

    @Test
    public void testDefaultIsUncompressed() throws Exception {
        FileSystemEmitter emitter = emitter("none");
        emitter.emit("a.pdf", metadataList(), new ParseContext());
        try (Reader r = Files.newBufferedReader(tmp.resolve("a.pdf.json"), StandardCharsets.UTF_8)) {
            assertEquals("hello world",
                    JsonMetadataList.fromJson(r).get(0).get(TikaCoreProperties.TIKA_CONTENT));
        }
    }

    @Test
    public void testBadCompression() {
        assertThrows(IllegalArgumentException.class, () -> new FileSystemEmitter().setCompression("zstd"));
    }

    private FileSystemEmitter emitter(String compression) {
        FileSystemEmitter emitter = new FileSystemEmitter();
        emitter.setBasePath(tmp.toString());
        emitter.setCompression(compression);
        return emitter;
    }

    private static List<Metadata> metadataList() {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.TIKA_CONTENT, "hello world");
        return Collections.singletonList(m);
    }
}
