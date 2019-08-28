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

package org.apache.tika.eval.langid;

import static org.junit.Assert.fail;

import java.util.Locale;
import java.util.Set;

import org.apache.tika.eval.tokens.CommonTokenCountManager;
import org.junit.BeforeClass;
import org.junit.Test;

public class LangIdTest {

    @BeforeClass
    public static void init() throws Exception {
        LanguageIDWrapper.loadBuiltInModels();
    }

    @Test
    public void testCommonTokensCoverage() throws Exception {
        //make sure that there is a common tokens file for every
        //language
        LanguageIDWrapper wrapper = new LanguageIDWrapper();
        CommonTokenCountManager commonTokens = new CommonTokenCountManager(null, "eng");
        for (String lang : wrapper.getSupportedLanguages()) {
            Set<String> tokens = commonTokens.getTokens(lang);
            if (tokens.size() == 0) {
                fail(String.format(Locale.US,
                        "missing common tokens for: %s", lang));
            } else if (tokens.size() < 1000) {//kur has 1357
                fail(String.format(Locale.US,
                        "common tokens too small (%s) for: %s",
                        tokens.size(), lang));

            }
        }
    }
}
