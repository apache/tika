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
package org.apache.tika.eval.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RunInfoTest {

    private static Path res(String name) throws Exception {
        return Paths.get(RunInfoTest.class.getResource("/test-dirs/pipes-reports/" + name).toURI());
    }

    private static Path runInfoDir(Path extracts) throws Exception {
        return Files.createDirectories(extracts.resolve(RunInfo.RUN_INFO_DIR));
    }

    @Test
    public void testDiscoverRefusesTwoRuns(@TempDir Path extracts) throws Exception {
        Path dir = runInfoDir(extracts);
        Files.copy(res("run-info-run-a1.json"), dir.resolve("run-info-run-a1.json"));
        Files.copy(res("run-info-run-b1.json"), dir.resolve("run-info-run-b1.json"));
        assertThrows(IllegalArgumentException.class, () -> RunInfo.loadSide(null, null, extracts));
        // an explicit run-info bypasses run-info discovery but the ledger is still discovered
        RunInfo.Side side = RunInfo.loadSide(null, dir.resolve("run-info-run-a1.json"), extracts);
        assertNull(side.pipesReport());
        assertEquals("run-a1", side.batchInfo().get(RunInfo.RUN_ID_KEY));

        Files.copy(res("crashes-run-a1.jsonl"), dir.resolve("crashes-run-a1.jsonl"));
        Files.copy(res("crashes-run-b1.jsonl"), dir.resolve("crashes-run-b1.jsonl"));
        assertThrows(IllegalArgumentException.class, () -> RunInfo.loadSide(null, dir.resolve("run-info-run-a1.json"), extracts));
    }

    @Test
    public void testDiscoverRefusesMismatchedPair(@TempDir Path extracts) throws Exception {
        Path dir = runInfoDir(extracts);
        Files.copy(res("run-info-run-a1.json"), dir.resolve("run-info-run-a1.json"));
        Files.copy(res("crashes-run-b1.jsonl"), dir.resolve("crashes-run-b1.jsonl"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RunInfo.loadSide(null, null, extracts));
        assertTrue(e.getMessage().contains("crashes-run-a1.jsonl"), e.getMessage());
    }

    @Test
    public void testDiscoverMatchedPairAndNone(@TempDir Path extracts) throws Exception {
        RunInfo.Side none = RunInfo.loadSide(null, null, extracts);
        assertNull(none.pipesReport());
        assertTrue(none.batchInfo().isEmpty());

        Path dir = runInfoDir(extracts);
        Files.copy(res("run-info-run-a1.json"), dir.resolve("run-info-run-a1.json"));
        Files.copy(res("crashes-run-a1.jsonl"), dir.resolve("crashes-run-a1.jsonl"));
        RunInfo.Side side = RunInfo.loadSide(null, null, extracts);
        assertEquals(5, side.pipesReport().size());
        assertEquals("run-a1", side.batchInfo().get(RunInfo.RUN_ID_KEY));
    }

    @Test
    public void testCheckRunId() throws Exception {
        Map<String, String> a1 = Map.of(RunInfo.RUN_ID_KEY, "run-a1");
        RunInfo.checkRunId(a1, res("crashes-run-a1.jsonl"));
        RunInfo.checkRunId(a1, null);
        RunInfo.checkRunId(Map.of(), res("crashes-run-b1.jsonl"));
        assertThrows(IllegalArgumentException.class, () -> RunInfo.checkRunId(a1, res("crashes-run-b1.jsonl")));
        // substring is not enough
        assertThrows(IllegalArgumentException.class, () -> RunInfo.checkRunId(Map.of(RunInfo.RUN_ID_KEY, "run-a"), res("crashes-run-a1.jsonl")));
        assertThrows(IllegalArgumentException.class, () -> RunInfo.checkRunId(Map.of(RunInfo.RUN_ID_KEY, ""), res("crashes-run-a1.jsonl")));
    }

    @Test
    public void testExtractsInfo(@TempDir Path extracts) throws Exception {
        Files.writeString(extracts.resolve("a.json"), "aaa");
        Files.createDirectories(extracts.resolve("sub"));
        Files.writeString(extracts.resolve("sub/b.json"), "bb");
        Files.writeString(runInfoDir(extracts).resolve("run-info-x.json"), "{}");
        Map<String, String> m = RunInfo.extractsInfo(extracts);
        assertEquals("2", m.get("extracts.count"));
        assertEquals(64, m.get("extracts.fingerprint").length());
        assertThrows(IllegalArgumentException.class, () -> RunInfo.extractsInfo(extracts.resolve("nope")));
    }

    @Test
    public void testRedact() {
        assertEquals("jdbc:postgresql://h/db?user=u&password=***",
                RunInfo.redact("jdbc:postgresql://h/db?user=u&password=s3cr3t"));
        assertEquals("jdbc:mysql://bob:***@h/db", RunInfo.redact("jdbc:mysql://bob:hunter2@h/db"));
        assertEquals("-Dfoo=1 -Djavax.net.ssl.keyStorePassword=*** -Dbar=2",
                RunInfo.redact("-Dfoo=1 -Djavax.net.ssl.keyStorePassword=changeit -Dbar=2"));
        assertEquals("EvalConfig{jdbcString='jdbc:h2:file:/x'}", RunInfo.redact("EvalConfig{jdbcString='jdbc:h2:file:/x'}"));
    }
}
