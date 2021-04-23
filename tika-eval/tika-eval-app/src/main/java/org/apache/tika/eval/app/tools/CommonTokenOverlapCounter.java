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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.eval.core.tokens.CommonTokenCountManager;

public class CommonTokenOverlapCounter {

    public static void main(String[] args) throws Exception {
        Path commonTokensDir = Paths.get(args[0]);
        CommonTokenOverlapCounter counter = new CommonTokenOverlapCounter();
        counter.execute(commonTokensDir);
    }

    private void execute(Path commonTokensDir) throws IOException {
        List<String> langs = new ArrayList<>();
        for (File f : commonTokensDir.toFile().listFiles()) {
            langs.add(f.getName());
        }
        CommonTokenCountManager mgr = new CommonTokenCountManager(commonTokensDir, "");
        for (int i = 0; i < langs.size() - 1; i++) {
            for (int j = i + 1; j < langs.size(); j++) {
                compare(langs.get(i), langs.get(j), mgr);
            }
        }
    }

    private void compare(String langA, String langB, CommonTokenCountManager mgr) {
        int overlap = 0;
        int denom = 0;
        Set<String> setA = mgr.getTokens(langA);
        Set<String> setB = mgr.getTokens(langB);
        for (String a : setA) {
            if (setB.contains(a)) {
                overlap += 2;
            }
        }
        denom = setA.size() + setB.size();
        double percent = (double) overlap / (double) denom;
        if (percent > 0.01) {
            System.out.println(String.format(Locale.US, "%s %s %.2f", langA, langB, percent));
        }
    }


}
