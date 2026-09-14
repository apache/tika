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
package org.apache.tika.eval.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;

public class StructureCompareCLITest {

    @TempDir
    Path tmp;

    @Test
    public void testRun() throws Exception {
        Path a = Paths.get(getClass().getResource("/extracts-a").toURI());
        Path b = Paths.get(getClass().getResource("/extracts-b").toURI());
        List<StructureScores> scores = StructureCompareCLI.run(a, b, tmp, "pdf:producer", 0.5);
        assertEquals(1, scores.size());
        StructureScores s = scores.get(0);
        assertEquals("doc.pdf", s.file);
        assertEquals("Test Producer", s.group);
        assertEquals("x.docx", StructureCompareCLI.pairingKey("x.docx.pdf.json", ".pdf"));
        assertEquals("x.docx.pdf", StructureCompareCLI.pairingKey("x.docx.pdf.json", ""));
        assertEquals("x.docx", StructureCompareCLI.pairingKey("x.docx.json", ".pdf"));
        Metadata m = new Metadata();
        m.set("pdf:producer", "dompdf 0.8.6\n + CPDF");
        assertEquals("dompdf", StructureCompareCLI.group(m, "pdf:producer"));
        m.set("pdf:producer", "Word\n2013; modified");
        assertEquals("Word", StructureCompareCLI.group(m, "pdf:producer"));
        assertEquals(3, s.matched);
        // B put the third paragraph first
        assertEquals(1.0 - 2.0 * 2 / 3, s.tau, 1e-9);
        assertEquals(0.5, s.adjacent, 1e-9);
        assertTrue(s.artifactShareB > 0);
        assertEquals(0.0, s.artifactShareA, 1e-9);

        String tsv = Files.readString(tmp.resolve("per-file.tsv"), StandardCharsets.UTF_8);
        assertTrue(tsv.startsWith("file\tgroup\t"));
        assertTrue(tsv.contains("doc.pdf\tTest Producer\t"));
        String summary = Files.readString(tmp.resolve("summary.md"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("| all | 1 |"));
        assertTrue(summary.contains("Missing in B: 1"));
    }
}
