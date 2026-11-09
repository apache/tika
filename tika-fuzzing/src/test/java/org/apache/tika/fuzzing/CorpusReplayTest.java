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
package org.apache.tika.fuzzing;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tika.io.TikaInputStream;

/**
 * Replays every file under {@code -Dtika.fuzz.corpus=<dir>} through the same parse as
 * {@link AutoDetectParserFuzzTest}, once per input mode. Skipped when the property is unset.
 */
class CorpusReplayTest {

    private static final String CORPUS = System.getProperty("tika.fuzz.corpus", "");

    enum Mode { BYTES, STREAM, PATH }

    static Stream<Arguments> files() throws IOException {
        if (CORPUS.isEmpty()) {
            return Stream.of(Arguments.of(Mode.BYTES, null));
        }
        Path root = Paths.get(CORPUS);
        // collect eagerly: Files.walk holds a directory handle until closed
        return Files.walk(root).filter(Files::isRegularFile).sorted().toList().stream()
                .flatMap(p -> Stream.of(Mode.values()).map(m -> Arguments.of(m, p)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("files")
    void replay(Mode mode, Path file) throws Exception {
        assumeTrue(file != null, "set -Dtika.fuzz.corpus=<dir> to replay a corpus");
        try (TikaInputStream tis = switch (mode) {
            case BYTES -> TikaInputStream.get(Files.readAllBytes(file));
            case STREAM -> TikaInputStream.get(Files.newInputStream(file));
            case PATH -> TikaInputStream.get(file);
        }) {
            AutoDetectParserFuzzTest.parse(tis);
        }
    }
}
