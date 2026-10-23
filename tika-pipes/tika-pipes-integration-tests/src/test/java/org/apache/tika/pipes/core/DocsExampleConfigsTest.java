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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Doc examples are symlinks to test configs, so they cannot drift from what is tested; a test-only
 * component in one fails at startup for users who copy it (TIKA-4984).
 */
public class DocsExampleConfigsTest {

    private static final String EXAMPLES_RELATIVE_PATH = "docs/modules/ROOT/examples";

    private static final Pattern MOCK_COMPONENT = Pattern.compile("\"(mock-[a-z0-9-]+)\"\\s*:");

    @Test
    public void testNoMockComponentsInDocExamples() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(findExamplesDir())) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                Matcher m = MOCK_COMPONENT.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    offenders.add(p.getFileName() + ": " + m.group(1));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "test-only components in doc examples: " + offenders);
    }

    @Test
    public void testDocExamplesAreLiveSymlinks() throws Exception {
        List<Path> examples;
        try (Stream<Path> files = Files.list(findExamplesDir())) {
            examples = files.toList();
        }
        assumeTrue(examples.stream().anyMatch(Files::isSymbolicLink),
                "symlinks checked out as plain files (core.symlinks=false)");
        List<String> offenders = new ArrayList<>();
        for (Path p : examples) {
            if (!Files.isSymbolicLink(p)) {
                offenders.add(p.getFileName() + ": not a symlink");
            } else if (!Files.isRegularFile(p)) {
                offenders.add(p.getFileName() + ": dangling");
            }
        }
        assertTrue(offenders.isEmpty(), "doc examples must be symlinks into the source tree: " + offenders);
    }

    private static Path findExamplesDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(EXAMPLES_RELATIVE_PATH);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not locate " + EXAMPLES_RELATIVE_PATH + " above " + Path.of("").toAbsolutePath());
    }
}
