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
package org.apache.tika.eval.app.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dev tool that reports pairs of languages whose common-token lists overlap by more than 1%.
 * <p>
 * The shipped runtime resources are Bloom filters, which cannot be enumerated, so this reads
 * the raw {@code token<TAB>df<TAB>cf} source lists directly. Point it at a directory of those
 * files (e.g. {@code tika-eval-core/src/test/resources/common_tokens}).
 */
public class CommonTokenOverlapCounter {

    public static void main(String[] args) throws Exception {
        Path commonTokensDir = Paths.get(args[0]);
        new CommonTokenOverlapCounter().execute(commonTokensDir);
    }

    private void execute(Path commonTokensDir) throws IOException {
        Map<String, Set<String>> tokensByLang = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(commonTokensDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    tokensByLang.put(p.getFileName().toString(), loadTokens(p));
                }
            }
        }
        List<String> langs = new ArrayList<>(tokensByLang.keySet());
        for (int i = 0; i < langs.size() - 1; i++) {
            for (int j = i + 1; j < langs.size(); j++) {
                compare(langs.get(i), langs.get(j), tokensByLang);
            }
        }
    }

    private static Set<String> loadTokens(Path p) throws IOException {
        Set<String> tokens = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
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

    private void compare(String langA, String langB, Map<String, Set<String>> tokensByLang) {
        Set<String> setA = tokensByLang.get(langA);
        Set<String> setB = tokensByLang.get(langB);
        int overlap = 0;
        for (String a : setA) {
            if (setB.contains(a)) {
                overlap += 2;
            }
        }
        int denom = setA.size() + setB.size();
        double percent = (double) overlap / (double) denom;
        if (percent > 0.01) {
            System.out.printf(Locale.US, "%s %s %.2f%n", langA, langB, percent);
        }
    }

}
