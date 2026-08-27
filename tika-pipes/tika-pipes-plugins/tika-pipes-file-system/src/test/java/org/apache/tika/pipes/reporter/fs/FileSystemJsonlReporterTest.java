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
package org.apache.tika.pipes.reporter.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.plugins.ExtensionConfig;

public class FileSystemJsonlReporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FileSystemJsonlReporter build(Path path, String extraJson) throws Exception {
        String json = "{\"path\":\"" + path.toAbsolutePath().toString().replace("\\", "/") + "\"" + extraJson + "}";
        return new FileSystemJsonlReporterFactory().buildExtension(new ExtensionConfig("test", FileSystemJsonlReporterFactory.NAME, json));
    }

    private static void report(FileSystemJsonlReporter r, String id, PipesResult.RESULT_STATUS status, String msg) {
        r.report(new FetchEmitTuple(id, new FetchKey("f", id), new EmitKey("e", id)), new PipesResult(status, msg), 7);
    }

    private static List<Map<String, Object>> lines(Path path) throws IOException {
        List<Map<String, Object>> ret = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            ret.add(MAPPER.readValue(line, Map.class));
        }
        return ret;
    }

    @Test
    public void testIncludesAndFields(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, ",\"includes\":[\"OOM\",\"TIMEOUT\"]")) {
            report(r, "a/b.pdf", PipesResult.RESULT_STATUS.OOM, "boom");
            report(r, "c.doc", PipesResult.RESULT_STATUS.PARSE_SUCCESS, null);
            report(r, "d.doc", PipesResult.RESULT_STATUS.TIMEOUT, null);
        }
        List<Map<String, Object>> lines = lines(path);
        assertEquals(2, lines.size());
        assertEquals("a/b.pdf", lines.get(0).get("id"));
        assertEquals("OOM", lines.get(0).get("status"));
        assertEquals("boom", lines.get(0).get("message"));
        assertEquals(7, lines.get(0).get("elapsedMs"));
        assertTrue(lines.get(0).get("ts").toString().endsWith("Z"));
        assertEquals("d.doc", lines.get(1).get("id"));
    }

    @Test
    public void testConcurrentReportsAllLand(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        int threads = 8;
        int perThread = 500;
        try (FileSystemJsonlReporter r = build(path, "")) {
            ExecutorService ex = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(ex.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        report(r, tid + "/" + i, PipesResult.RESULT_STATUS.PARSE_SUCCESS, null);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
            ex.shutdown();
        }
        assertEquals(threads * perThread, lines(path).size());
    }

    @Test
    public void testOnExists(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, "")) {
            report(r, "first", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertThrows(TikaConfigException.class, () -> build(path, ""));
        assertThrows(TikaConfigException.class, () -> build(path, ",\"onExists\":\"EXCEPTION\""));
        assertEquals(1, lines(path).size());

        try (FileSystemJsonlReporter r = build(path, ",\"onExists\":\"APPEND\"")) {
            report(r, "second", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(List.of("first", "second"), lines(path).stream().map(m -> m.get("id")).toList());

        try (FileSystemJsonlReporter r = build(path, ",\"onExists\":\"REPLACE\"")) {
            report(r, "third", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(List.of("third"), lines(path).stream().map(m -> m.get("id")).toList());
    }

    @Test
    public void testMessageCap(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, ",\"maxMessageLength\":10")) {
            report(r, "x", PipesResult.RESULT_STATUS.OOM, "0123456789abcdef");
        }
        String msg = (String) lines(path).get(0).get("message");
        assertTrue(msg.startsWith("0123456789...[truncated 6 chars]"), msg);
    }

    @Test
    public void testErrorFlushesWithoutClose(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        FileSystemJsonlReporter r = build(path, "");
        report(r, "x", PipesResult.RESULT_STATUS.OOM, null);
        r.error(new RuntimeException("fatal"));
        List<Map<String, Object>> lines = lines(path);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).get("error").toString().contains("fatal"));
        assertThrows(IllegalStateException.class, () -> report(r, "y", PipesResult.RESULT_STATUS.OOM, null));
    }

    @Test
    public void testCreatesParentDirs(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("a/b/audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, "")) {
            report(r, "x", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(1, lines(path).size());
    }
}
