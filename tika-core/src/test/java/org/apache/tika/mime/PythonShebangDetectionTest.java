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
package org.apache.tika.mime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tika.Tika;

class PythonShebangDetectionTest {

    private final Tika tika = new Tika();

    static Stream<Arguments> scripts() {
        return Stream.of(
                Arguments.of("#!/usr/bin/env python", "print('hello')", "text/x-python"),
                Arguments.of("#!/usr/bin/env python3", "print('hello')", "text/x-python"),
                Arguments.of("#! /usr/bin/env python3", "print('hello')", "text/x-python"),
                Arguments.of("#!/usr/bin/env python3.11", "print('hello')", "text/x-python"),
                Arguments.of("#!/usr/bin/python3", "print('hello')", "text/x-python"),
                Arguments.of("#!/bin/python3", "print('hello')", "text/x-python"),
                Arguments.of("#!/bin/sh", "echo '/bin/env python3'", "application/x-sh"),
                Arguments.of("#!/usr/bin/env bash", "echo hello", "application/x-sh")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scripts")
    void testContentOnly(String shebang, String body, String expected) {
        byte[] content = (shebang + "\n" + body + "\n").getBytes(UTF_8);
        String actual = tika.detect(content);
        assertEquals(expected, actual, "Content-only detection: " + shebang);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scripts")
    void testWithPythonFilenameHint(String shebang, String body, String expected) {
        byte[] content = (shebang + "\n" + body + "\n").getBytes(UTF_8);
        String actual = tika.detect(content, "script.py");
        assertEquals(expected, actual, "Detection with script.py hint: " + shebang);
    }

    @Test
    void testPythonFilenameOnly() {
        String actual = tika.detect("script.py");
        assertEquals("text/x-python", actual);
    }

    @Test
    void testPythonShebangMustBeAtStart() {
        byte[] content = "\n#!/usr/bin/env python3\nprint('hello')\n".getBytes(UTF_8);
        assertEquals("text/plain", tika.detect(content));
    }
}
