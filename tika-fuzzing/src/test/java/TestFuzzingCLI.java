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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


import org.apache.tika.fuzzing.cli.FuzzingCLI;
import org.apache.tika.utils.ProcessUtils;

public class TestFuzzingCLI {

    @Test
    @Disabled
    public void testBasic() throws Exception {
        //convert to actual unit test
        String inputDir = "";// fill in
        String outputDir = "";//fill in
        String[] args = new String[]{"-i", inputDir, "-o", outputDir, "-n", "8", // num threads
                "-t", "1", //max transformers
                "-p", "100", //per file iterations
                "-r", "3"};
        FuzzingCLI.main(args);
    }

    @Test
    @Disabled
    public void testMock() throws Exception {
        //convert to actual unit test
        Path inputDir = Paths.get(getClass().getResource("/test-documents").toURI());
        Path outputDir = Files.createTempDirectory("tika-fuzzing-");
        String[] args = new String[]{"-i",
                ProcessUtils.escapeCommandLine(inputDir.toAbsolutePath().toString()), "-o",
                ProcessUtils.escapeCommandLine(outputDir.toAbsolutePath().toString()), "-n", "8",
                // num threads
                "-t", "0", //max transformers
                "-p", "10", //per file iterations
                "-m", "10000", //max ms per file
                "-r", "3"};
        try {
            FuzzingCLI.main(args);
        } finally {
            FileUtils.deleteDirectory(outputDir.toFile());
        }
    }
}
