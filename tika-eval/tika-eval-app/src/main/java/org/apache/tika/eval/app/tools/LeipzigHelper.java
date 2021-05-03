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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeipzigHelper {

    static Map<String, List<Path>> getFiles(Path leipzigDir) throws IOException {
        Matcher tableMatcher = Pattern.compile("([a-z]+)_table(\\.txt)?(\\.gz)?$").matcher("");
        Matcher leipzigMatcher =
                Pattern.compile("([a-z]{3,3}(-(simp|trad|rom|zaw))?)[-_].*$").matcher("");

        Map<String, List<Path>> m = new TreeMap<>();
        for (File f : leipzigDir.toFile().listFiles()) {
            System.err.println(f);
            String lang = null;
            if (tableMatcher.reset(f.getName()).find()) {
                lang = tableMatcher.group(1);
            } else if (leipzigMatcher.reset(f.getName()).find()) {
                lang = leipzigMatcher.group(1);
            }
            if (lang == null) {
                System.err.println("couldn't find a lang: " + f);
                continue;
            }
            List<Path> files = m.get(lang);
            if (files == null) {
                files = new ArrayList<>();
            }
            files.add(f.toPath());
            m.put(lang, files);
        }
        return m;
    }
}
