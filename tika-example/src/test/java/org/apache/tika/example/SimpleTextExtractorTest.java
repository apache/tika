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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import org.apache.tika.TikaTest;

// because System is caught
// https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution-synchronization
@Isolated
public class SimpleTextExtractorTest extends TikaTest {
    @Test
    public void testSimpleTextExtractor() throws Exception {
        String message =
                "This is Tika - Hello, World! This is simple UTF-8 text" + " content written in English to test autodetection of" + " the character encoding of the input stream.";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        PrintStream out = System.out;
        System.setOut(new PrintStream(buffer, true, UTF_8.name()));

        File file = new File("target", "test.txt");
        FileUtils.writeStringToFile(file, message, UTF_8);
        SimpleTextExtractor.main(new String[]{file.getPath()});
        file.delete();

        System.setOut(out);

        assertContains(message, buffer
                .toString(UTF_8.name())
                .trim());
    }
}
