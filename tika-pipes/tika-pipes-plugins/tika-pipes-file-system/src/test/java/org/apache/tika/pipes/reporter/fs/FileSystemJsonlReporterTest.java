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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.plugins.ExtensionConfig;

public class FileSystemJsonlReporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FileSystemJsonlReporter build(Path path, Map<String, Object> extra) throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("path", path.toAbsolutePath().toString());
        cfg.putAll(extra);
        return build(MAPPER.writeValueAsString(cfg));
    }

    private static FileSystemJsonlReporter build(String json) throws Exception {
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

    private static List<Object> ids(Path path) throws IOException {
        return lines(path).stream().map(m -> m.get("id")).toList();
    }

    @Test
    public void testIncludesAndFields(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of("includes", List.of("OOM", "TIMEOUT")))) {
            report(r, "a/b.pdf", PipesResult.RESULT_STATUS.OOM, "boom");
            report(r, "c.doc", PipesResult.RESULT_STATUS.PARSE_SUCCESS, null);
            report(r, "d.doc", PipesResult.RESULT_STATUS.TIMEOUT, null);
        }
        List<Map<String, Object>> lines = lines(path);
        assertEquals(2, lines.size());
        Map<String, Object> first = lines.get(0);
        assertEquals("a/b.pdf", first.get("id"));
        assertEquals("OOM", first.get("status"));
        assertEquals("PROCESS_CRASH", first.get("category"));
        assertEquals("boom", first.get("message"));
        assertEquals(7, first.get("elapsedMs"));
        assertTrue(first.get("timestamp").toString().endsWith("Z"));
        assertEquals(Set.of("id", "status", "category", "message", "elapsedMs", "timestamp"), first.keySet());
        assertEquals("d.doc", lines.get(1).get("id"));
    }

    @Test
    public void testExcludes(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of("excludes", List.of("PARSE_SUCCESS")))) {
            report(r, "a", PipesResult.RESULT_STATUS.PARSE_SUCCESS, null);
            report(r, "b", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(List.of("b"), ids(path));
    }

    @Test
    public void testConcurrentReportsAllLand(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        int threads = 8;
        int perThread = 500;
        Set<String> expected = new HashSet<>();
        try (FileSystemJsonlReporter r = build(path, Map.of())) {
            ExecutorService ex = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(ex.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        report(r, tid + "/" + i, PipesResult.RESULT_STATUS.PARSE_SUCCESS, null);
                    }
                }));
                for (int i = 0; i < perThread; i++) {
                    expected.add(tid + "/" + i);
                }
            }
            for (Future<?> f : futures) {
                f.get();
            }
            ex.shutdown();
        }
        List<Object> ids = ids(path);
        assertEquals(threads * perThread, ids.size());
        assertEquals(expected, new HashSet<>(ids));
    }

    @Test
    public void testOnExists(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of())) {
            report(r, "first", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertThrows(TikaConfigException.class, () -> build(path, Map.of()));
        assertThrows(TikaConfigException.class, () -> build(path, Map.of("onExists", "EXCEPTION")));
        assertEquals(List.of("first"), ids(path));

        try (FileSystemJsonlReporter r = build(path, Map.of("onExists", "APPEND"))) {
            report(r, "second", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(List.of("first", "second"), ids(path));

        try (FileSystemJsonlReporter r = build(path, Map.of("onExists", "REPLACE"))) {
            report(r, "third", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(List.of("third"), ids(path));
    }

    @Test
    public void testAppendAfterPartialLine(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        Files.writeString(path, "{\"id\":\"cut", StandardCharsets.UTF_8);
        try (FileSystemJsonlReporter r = build(path, Map.of("onExists", "APPEND"))) {
            report(r, "next", PipesResult.RESULT_STATUS.OOM, null);
        }
        List<String> raw = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(2, raw.size());
        assertEquals("next", MAPPER.readValue(raw.get(1), Map.class).get("id"));
    }

    @Test
    public void testMessageCap(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of("maxMessageLength", 10))) {
            report(r, "x", PipesResult.RESULT_STATUS.OOM, "0123456789abcdef");
            report(r, "y", PipesResult.RESULT_STATUS.OOM, "012345678😀ab");
        }
        List<Map<String, Object>> lines = lines(path);
        assertEquals("0123456789...[truncated 6 chars]", lines.get(0).get("message"));
        // cut lands on a surrogate pair: back off one so the pair isn't split
        assertEquals("012345678...[truncated 4 chars]", lines.get(1).get("message"));
    }

    @Test
    public void testLoneSurrogateDoesNotKillWriter(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of())) {
            report(r, "bad" + (char) 0xD83D, PipesResult.RESULT_STATUS.OOM, (char) 0xDE00 + " lone low");
            report(r, "after", PipesResult.RESULT_STATUS.OOM, null);
        }
        List<Map<String, Object>> lines = lines(path);
        assertEquals(2, lines.size());
        assertEquals("after", lines.get(1).get("id"));
    }

    @Test
    public void testMultilineMessageStaysOneLine(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of())) {
            report(r, "x", PipesResult.RESULT_STATUS.OOM, "line1\nline2\r\n line3");
        }
        assertEquals(1, Files.readAllLines(path).size());
        assertEquals("line1\nline2\r\n line3", lines(path).get(0).get("message"));
    }

    @Test
    public void testErrorFlushesWithoutClose(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("audit.jsonl");
        FileSystemJsonlReporter r = build(path, Map.of());
        report(r, "x", PipesResult.RESULT_STATUS.OOM, null);
        r.error(new RuntimeException("fatal"));
        List<Map<String, Object>> lines = lines(path);
        assertEquals(2, lines.size());
        assertEquals(Set.of("error", "timestamp"), lines.get(1).keySet());
        assertTrue(lines.get(1).get("error").toString().contains("fatal"));
        // late reports after error/close are dropped, not thrown
        assertDoesNotThrow(() -> report(r, "y", PipesResult.RESULT_STATUS.OOM, null));
        assertDoesNotThrow(r::close);
        assertEquals(2, lines(path).size());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testWriteFailureIsLoud() throws Exception {
        Path devFull = Path.of("/dev/full");
        try (FileSystemJsonlReporter r = build(devFull, Map.of("onExists", "APPEND"))) {
            assertThrows(IllegalStateException.class, () -> report(r, "x", PipesResult.RESULT_STATUS.OOM, null));
            assertThrows(IllegalStateException.class, () -> report(r, "y", PipesResult.RESULT_STATUS.OOM, null));
            assertThrows(IOException.class, r::close);
        } catch (IOException expected) {
            //try-with-resources close
        }
    }

    @Test
    public void testConfigErrors(@TempDir Path tmp) throws Exception {
        assertThrows(TikaConfigException.class, () -> build("{}"));
        assertThrows(TikaConfigException.class, () -> build(tmp.resolve("a.jsonl"), Map.of("maxMessageLength", -1)));
        assertThrows(TikaConfigException.class, () -> build(tmp, Map.of()));
        assertFalse(Files.exists(tmp.resolve("a.jsonl")));
    }

    @Test
    public void testCreatesParentDirs(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("a/b/audit.jsonl");
        try (FileSystemJsonlReporter r = build(path, Map.of())) {
            report(r, "x", PipesResult.RESULT_STATUS.OOM, null);
        }
        assertEquals(1, lines(path).size());
    }
}
