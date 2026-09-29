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
package org.apache.tika;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Fails a locale CI job whose locale never reached the forked test JVM: -Duser.language on
 * the mvn command line becomes a system property only after the default Locale is fixed.
 */
public class DefaultLocaleCanaryTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "TIKA_EXPECTED_LOCALE", matches = ".+")
    public void testDefaultLocaleMatchesExpected() {
        assertEquals(System.getenv("TIKA_EXPECTED_LOCALE"), Locale.getDefault().toLanguageTag());
    }
}
