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
package org.apache.tika.eval.util;

import java.util.List;


import org.apache.tika.eval.langid.Language;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.junit.Assert;
import org.junit.Test;

public class LanguageIdTest {
    @Test(timeout = 10000)
    public void testDefenseAgainstBadRegexInOpenNLP() throws Exception {
        //TIKA-2777
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            sb.append("a");
        }
        LanguageIDWrapper.loadBuiltInModels();
        LanguageIDWrapper wrapper = new LanguageIDWrapper();
        List<Language> languages = wrapper.getProbabilities(sb.toString());
        Assert.assertEquals("mri", languages.get(0).getLanguage());
    }
}
