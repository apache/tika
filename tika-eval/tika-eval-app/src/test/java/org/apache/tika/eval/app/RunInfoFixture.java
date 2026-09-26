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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * Builds an extracts dir with a {@code .run-info/} beside it at test time. The source release
 * excludes every dot-directory ({@code assembly.xml}: {@code **&#47;.*&#47;**}), so this fixture
 * cannot be stored in {@code src/test/resources}; it is assembled from {@code pipes-reports/}.
 */
final class RunInfoFixture {

    private RunInfoFixture() {
    }

    /** A copy of {@code test-dirs/extractsB} with run B's run-info json and crash ledger discoverable. */
    static Path extractsBWithRunInfo(Path tmp) throws Exception {
        Path testDirs = Paths.get(RunInfoFixture.class.getResource("/test-dirs").toURI());
        Path extracts = tmp.resolve("extractsB");
        FileUtils.copyDirectory(testDirs.resolve("extractsB").toFile(), extracts.toFile());
        Path runInfo = Files.createDirectories(extracts.resolve(RunInfo.RUN_INFO_DIR));
        for (String name : List.of("run-info-run-b1.json", "crashes-run-b1.jsonl")) {
            Files.copy(testDirs.resolve("pipes-reports").resolve(name), runInfo.resolve(name));
        }
        return extracts;
    }
}
