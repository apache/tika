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
package org.apache.tika.pipes.core.extractor.frictionless;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class FrictionlessResourceTest {

    /** Frictionless resource names are lowercase [a-z0-9._-]; extensions in path are not. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rmeta.json|rmeta.json",
            "unpacked/1.png|unpacked-1.png",
            "unpacked/4.PDF|unpacked-4.pdf",
            "unpacked/5.tar.gz|unpacked-5.tar.gz",
            "unpacked/6.c++|unpacked-6.c--",
            "unpacked/7.p df|unpacked-7.p-df",
            "unpacked/8.\u00fcn|unpacked-8.-n"
    })
    public void testNameFromPath(String path, String name) {
        assertEquals(name, FrictionlessResource.nameFromPath(path));
    }
}
