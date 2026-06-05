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
package org.apache.tika.eval.core.langid;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class LangIdTest {


    @Test
    @Disabled("run manually when updating common tokens or the language model")
    public void testCommonTokensCoverage() throws Exception {
        //make sure that there is a common tokens (source) list for every language and no extras.
        //The shipped runtime resources are Bloom filters (not enumerable), so check the raw
        //token lists under src/test/resources/common_tokens instead.
        Path commonTokensDir = Paths.get(getClass().getResource("/common_tokens").toURI());

        Set<String> supported = LanguageIDWrapper.getSupportedLanguages();
        String[] langs = supported.toArray(new String[0]);
        Arrays.sort(langs);
        for (String lang : langs) {
            Path f = commonTokensDir.resolve(lang);
            if (!Files.isRegularFile(f)) {
                fail(String.format(Locale.US, "missing common tokens for: %s", lang));
            }
            long tokens = countTokens(f);
            if (tokens < 250) {
                fail(String.format(Locale.US, "common tokens too small (%d) for: %s", tokens, lang));
            }
        }
        for (File f : commonTokensDir.toFile().listFiles()) {
            if (!supported.contains(f.getName())) {
                fail("extra common tokens for: " + f.getName());
            }
        }
    }

    private static long countTokens(Path f) throws IOException {
        try (Stream<String> lines = Files.lines(f, StandardCharsets.UTF_8)) {
            return lines.map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .count();
        }
    }
}
