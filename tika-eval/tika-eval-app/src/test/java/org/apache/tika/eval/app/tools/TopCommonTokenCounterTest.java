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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.utils.ProcessUtils;

public class TopCommonTokenCounterTest extends TikaTest {
    private final static String INPUT_FILE = "lang_file.txt";
    private final static String COMMON_TOKENS_FILE = "common_tokens";

    @TempDir
    private static Path WORKING_DIR;

    @BeforeAll
    public static void setUp() throws Exception {
        String[] docs =
                new String[]{"th quick brown fox", "jumped over th brown lazy", "brown lazy fox",
                        "\u666e\u6797\u65af\u987f\u5927\u5b66",
                        "\u666e\u6797\u65af\u987f\u5927\u5b66"};

        try (BufferedWriter writer = Files
                .newBufferedWriter(WORKING_DIR.resolve(INPUT_FILE), StandardCharsets.UTF_8)) {
            //do this 10 times to bump the numbers above the TopCommonTokenCounter's MIN_DOC_FREQ
            for (int i = 0; i < 10; i++) {
                for (String d : docs) {
                    writer.write(d);
                    writer.newLine();
                }
            }
            writer.flush();
        }
        TopCommonTokenCounter.main(new String[]{ProcessUtils.escapeCommandLine(
                WORKING_DIR.resolve(COMMON_TOKENS_FILE).toAbsolutePath().toString()),
                ProcessUtils.escapeCommandLine(
                        WORKING_DIR.resolve(INPUT_FILE).toAbsolutePath().toString())});
    }


    @Test
    public void testSimple() throws Exception {
        List<String> rows = FileUtils.readLines(WORKING_DIR.resolve(COMMON_TOKENS_FILE).toFile(),
                StandardCharsets.UTF_8);
        List<String> tokens = new ArrayList<>();
        for (String row : rows) {
            if (!row.startsWith("#")) {
                tokens.add(row.split("\t")[0]);
            }
        }
        assertEquals("brown", tokens.get(2));
        assertEquals("lazy", tokens.get(4));
        assertNotContained("th", tokens);//3 char word should be dropped
        assertNotContained("\u987f\u5927\u5b66", tokens);//cjk trigram should not be included
        assertNotContained("\u5b66", tokens);//cjk unigram should not be included
        assertContains("\u5927\u5b66", tokens);//cjk bigrams only
    }
}
