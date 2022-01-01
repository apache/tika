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
package org.apache.tika.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.eval.core.textstats.CommonTokens;
import org.apache.tika.eval.core.textstats.CompositeTextStatsCalculator;
import org.apache.tika.eval.core.textstats.TextStatsCalculator;
import org.apache.tika.eval.core.tokens.CommonTokenResult;


/**
 * These examples create a new {@link CompositeTextStatsCalculator}
 * for each call.  This is extremely inefficient because the lang id
 * model has to be loaded and the common words for each call.
 */
public class TextStatsFromTikaEval {

    /**
     * Use the default language id models and the default common tokens
     * lists in tika-eval to calculate the out-of-vocabulary percentage
     * for a given string.
     *
     * @param txt
     * @return
     */
    public double getOOV(String txt) {
        List<TextStatsCalculator> calculators = new ArrayList<>();
        calculators.add(new CommonTokens());
        CompositeTextStatsCalculator calc = new CompositeTextStatsCalculator(calculators);
        Map<Class, Object> results = calc.calculate(txt);

        /*
            Note that the OOV requires language id, so you can also
            retrieve the detected languages with this:

            List<Language> detectedLanguages = (List<Language>) results.get(LanguageIDWrapper.class);

         */

        CommonTokenResult result = (CommonTokenResult) results.get(CommonTokens.class);
        result.getLangCode(); // returned value ignored, this line van be removed
        return result.getOOV();
    }
}
