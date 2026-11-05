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
package org.apache.tika.ml.junkdetect.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TrainingProvenanceTest {

    @Test
    void chainRecordsCorpusDataAndModel(@TempDir Path tmp) throws IOException {
        Path corpus = Files.createDirectories(tmp.resolve("corpus-x"));
        Path built = Files.createDirectories(tmp.resolve("built"));
        Path model = tmp.resolve("m.bin");

        assertThrows(IOException.class, () -> TrainingProvenance.writeSource(corpus, built));
        Files.writeString(corpus.resolve("manifest.tsv"), "lang\tscript\n", StandardCharsets.UTF_8);
        Files.createDirectories(corpus.resolve("eng"));
        Files.writeString(corpus.resolve("eng/sentences_wikipedia.txt"), "1\tHello.\n", StandardCharsets.UTF_8);
        TrainingProvenance.writeSource(corpus, built);
        String content = TrainingProvenance.readKv(built.resolve(TrainingProvenance.SOURCE_FILE))
                .get("source_content_sha256");
        // editing a sentence file without touching the manifest changes the content digest
        Files.writeString(corpus.resolve("eng/sentences_wikipedia.txt"), "1\tHullo.\n", StandardCharsets.UTF_8);
        assertTrue(!content.equals(TrainingProvenance.contentSha256(corpus)));
        Files.writeString(corpus.resolve("eng/sentences_wikipedia.txt"), "1\tHello.\n", StandardCharsets.UTF_8);
        assertEquals(content, TrainingProvenance.contentSha256(corpus));

        Files.writeString(model, "model", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> TrainingProvenance.writeModelProvenance(
                model, built, new String[0], "abc", List.of()));
        Files.writeString(built.resolve("manifest.tsv"), "script\n", StandardCharsets.UTF_8);
        TrainingProvenance.writeModelProvenance(model, built,
                new String[]{"--data-dir", built.toString(), "--git-commit", "abc123"}, "abc123",
                List.of(JunkDetectorTrainingConfig.class));

        Map<String, String> kv = TrainingProvenance.readKv(tmp.resolve("m.bin.provenance"));
        assertEquals(TrainingProvenance.sha256(model), kv.get("model_sha256"));
        assertEquals("corpus-x", kv.get("source_corpus"));
        assertEquals(TrainingProvenance.sha256(corpus.resolve("manifest.tsv")),
                kv.get("source_manifest_sha256"));
        assertEquals("abc123", kv.get("git_commit"));
        assertEquals("--data-dir " + TrainingProvenance.shortPath(built) + " --git-commit abc123",
                kv.get("args"));
        assertTrue(!kv.get("args").contains(tmp.toString()), "machine path leaked: " + kv.get("args"));
        assertEquals(String.valueOf(JunkDetectorTrainingConfig.MIN_BIGRAM_COUNT),
                kv.get("config.JunkDetectorTrainingConfig.MIN_BIGRAM_COUNT"));
        assertTrue(kv.containsKey("training_data_manifest_sha256"));
    }
}
