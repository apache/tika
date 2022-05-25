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
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;

@SuppressWarnings("deprecation")
public class SimpleTypeDetectorTest extends TikaTest {

    @Test
    public void testSimpleTypeDetector() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        PrintStream out = System.out;
        System.setOut(new PrintStream(buffer, true, UTF_8.name()));

        SimpleTypeDetector.main(new String[]{"pom.xml"});

        System.setOut(out);

        assertContains("pom.xml: application/xml", buffer.toString(UTF_8.name()).trim());
    }

}
