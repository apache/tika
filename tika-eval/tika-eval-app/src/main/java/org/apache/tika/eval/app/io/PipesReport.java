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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The per-tuple ledger written by tika-pipes' {@code file-system-jsonl-reporter} during a
 * batch run, keyed by {@code FetchEmitTuple.id}. For the filesystem iterator the id is the
 * source-relative path, so it joins to {@code containers.file_path}; both sides are
 * normalized to '/' here.
 */
public class PipesReport {

    private static final Logger LOG = LoggerFactory.getLogger(PipesReport.class);

    public record Row(String status, String message, long elapsedMs) {
    }

    private final Path path;
    private final Map<String, Row> rows;
    private final List<String> errors;

    private PipesReport(Path path, Map<String, Row> rows, List<String> errors) {
        this.path = path;
        this.rows = rows;
        this.errors = errors;
    }

    public static PipesReport load(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Row> rows = new HashMap<>();
        List<String> errors = new ArrayList<>();
        int lineNo = 0;
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode n;
                try {
                    n = mapper.readTree(line);
                } catch (IOException e) {
                    throw new IOException("bad json at " + path + ":" + lineNo, e);
                }
                if (n.hasNonNull("error")) {
                    errors.add(n.get("error").asText());
                    continue;
                }
                if (!n.hasNonNull("id") || !n.hasNonNull("status")) {
                    throw new IOException("missing id/status at " + path + ":" + lineNo);
                }
                String id = normalize(n.get("id").asText());
                Row row = new Row(n.get("status").asText(), n.hasNonNull("message") ? n.get("message").asText() : null,
                        n.hasNonNull("elapsedMs") ? n.get("elapsedMs").asLong() : -1);
                // a retried tuple reports more than once; the last word wins
                rows.put(id, row);
            }
        }
        LOG.info("loaded {} pipes report rows ({} pipeline error lines) from {}", rows.size(), errors.size(), path);
        return new PipesReport(path, rows, errors);
    }

    public static String normalize(String id) {
        return id.replace('\\', '/');
    }

    public Row get(Path relativeSourcePath) {
        return rows.get(normalize(relativeSourcePath.toString()));
    }

    public Path getPath() {
        return path;
    }

    public int size() {
        return rows.size();
    }

    /** Final {@code {"error":...}} lines: the pipeline died before the run finished. */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
