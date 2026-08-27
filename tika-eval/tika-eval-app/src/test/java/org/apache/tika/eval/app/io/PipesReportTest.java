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
package org.apache.tika.eval.app.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.eval.app.RunInfo;

public class PipesReportTest {

    private static Path res(String name) throws Exception {
        return Paths.get(PipesReportTest.class.getResource("/test-dirs/pipes-reports/" + name).toURI());
    }

    @Test
    public void testLoadAndJoin() throws Exception {
        PipesReport r = PipesReport.load(res("crashes-run-a1.jsonl"));
        assertEquals(5, r.size());
        assertEquals(1, r.getErrors().size());
        assertTrue(r.getErrors().get(0).contains("pipeline died"));
        assertEquals("OOM", r.get(Paths.get("file9_noextract.txt")).status());
        assertEquals("EMIT_SUCCESS", r.get(Paths.get("file1.pdf")).status());
        assertNull(r.get(Paths.get("file1.pdf")).message());
        assertNull(r.get(Paths.get("nope.pdf")));
    }

    @Test
    public void testBackslashIdsJoinOnSlash() throws Exception {
        PipesReport r = PipesReport.load(res("crashes-run-b1.jsonl"));
        assertEquals("TIMEOUT", r.get(Paths.get("sub", "file9_noextract.txt")).status());
        assertEquals("TIMEOUT", r.get(Paths.get("sub\\file9_noextract.txt")).status());
    }

    @Test
    public void testRunInfoFlattenAndRunId() throws Exception {
        Map<String, String> batch = RunInfo.loadBatch(res("run-info-run-a1.json"));
        assertEquals("run-a1", batch.get("batch.run.id"));
        assertEquals("4.0.0", batch.get("batch.tika.version"));
        assertEquals("[\"-Xmx4g\",\"-XX:+UseG1GC\"]", batch.get("batch.jvm.args"));

        RunInfo.checkRunId(batch, res("crashes-run-a1.jsonl"));
        RunInfo.checkRunId(batch, null);
        RunInfo.checkRunId(Map.of(), res("crashes-run-b1.jsonl"));
        assertThrows(IllegalArgumentException.class, () -> RunInfo.checkRunId(batch, res("crashes-run-b1.jsonl")));
    }
}
