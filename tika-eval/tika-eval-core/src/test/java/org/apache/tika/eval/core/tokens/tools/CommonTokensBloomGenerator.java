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
package org.apache.tika.eval.core.tokens.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections4.bloomfilter.SimpleBloomFilter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.tokens.CommonTokensBloom;

/**
 * Regenerates the shipped per-language common-token Bloom filters from the raw
 * {@code token<TAB>df<TAB>cf} source lists.
 * <p>
 * This is a developer tool, not a unit test: it is skipped unless
 * {@code -DgenerateCommonTokensBloom=true} is passed. To regenerate:
 * <pre>
 *   ./mvnw -pl tika-eval/tika-eval-core test \
 *       -Dtest=CommonTokensBloomGenerator -DgenerateCommonTokensBloom=true
 * </pre>
 * Inputs/outputs default to the module's source tree and can be overridden with
 * {@code -DcommonTokensSrc=...} and {@code -DcommonTokensBloomOut=...}.
 */
public class CommonTokensBloomGenerator {

    private static final String SRC_DEFAULT = "src/test/resources/common_tokens";
    private static final String OUT_DEFAULT = "src/main/resources/common_tokens_bloom";

    @Test
    public void generate() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("generateCommonTokensBloom"),
                "set -DgenerateCommonTokensBloom=true to (re)generate the common-token Bloom filters");

        Path srcDir = Paths.get(System.getProperty("commonTokensSrc", SRC_DEFAULT));
        Path outDir = Paths.get(System.getProperty("commonTokensBloomOut", OUT_DEFAULT));
        Files.createDirectories(outDir);

        long totalIn = 0;
        long totalOut = 0;
        int langs = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(srcDir)) {
            for (Path in : ds) {
                if (!Files.isRegularFile(in)) {
                    continue;
                }
                Path out = outDir.resolve(in.getFileName().toString());
                List<String> tokens = readTokens(in);
                writeFilter(tokens, out);
                langs++;
                totalIn += Files.size(in);
                totalOut += Files.size(out);
            }
        }
        System.out.printf(Locale.ROOT, "Generated %d Bloom filters at fpp=%s: %.1f MB -> %.1f MB%n",
                langs, CommonTokensBloom.DEFAULT_FPP, totalIn / 1048576.0, totalOut / 1048576.0);
    }

    private static List<String> readTokens(Path in) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String token = line.split("\t", 2)[0].trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private static void writeFilter(List<String> tokens, Path out) throws IOException {
        SimpleBloomFilter filter = new SimpleBloomFilter(CommonTokensBloom.shapeFor(tokens.size()));
        for (String token : tokens) {
            filter.merge(CommonTokensBloom.hasher(token));
        }
        try (OutputStream os = Files.newOutputStream(out)) {
            CommonTokensBloom.write(os, filter);
        }
    }
}
