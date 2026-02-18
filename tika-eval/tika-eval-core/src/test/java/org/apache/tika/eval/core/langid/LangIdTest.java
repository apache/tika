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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.tokens.CommonTokenCountManager;

public class LangIdTest {


    @Test
    @Disabled("run manually when updating common tokens or the language model")
    public void testCommonTokensCoverage() throws Exception {
        //make sure that there is a common tokens file for every
        //language
        CommonTokenCountManager commonTokens = new CommonTokenCountManager(null, "eng");

        Set<String> supported = LanguageIDWrapper.getSupportedLanguages();
        String[] langs = supported.toArray(new String[0]);
        Arrays.sort(langs);
        for (String lang : langs) {
            Set<String> tokens = commonTokens.getTokens(lang);
            if (tokens.size() == 0) {
                fail(String.format(Locale.US, "missing common tokens for: %s", lang));
            } else if (tokens.size() < 250) {
                fail(String.format(Locale.US, "common tokens too small (%s) for: %s",
                        tokens.size(), lang));
            }
        }
        Path p = Paths.get(getClass().getResource("/common_tokens").toURI());
        for (File f : p.toFile().listFiles()) {
            if (!supported.contains(f.getName())) {
                fail("extra common tokens for: " + f.getName());
            }
        }
    }
}
