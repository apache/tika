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
package org.apache.tika.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Also the canary: the listener reaches every module through tika-parent, and this test fails
 * the build if it stops doing so.
 */
public class RandomLocaleListenerTest {

    @Test
    public void testListenerAppliedToThisJvm() {
        String tag = System.getProperty(RandomLocaleListener.PROPERTY);
        assertNotNull(tag, "listener did not run");
        assertEquals(tag, Locale.getDefault().toLanguageTag());
    }

    @Test
    public void testChoose() {
        assertEquals(Locale.forLanguageTag("tr-TR"), RandomLocaleListener.choose("tr-TR"));
        assertSame(Locale.getDefault(), RandomLocaleListener.choose("system"));
        assertThrows(IllegalArgumentException.class, () -> RandomLocaleListener.choose("?"));
        List<Locale> candidates = RandomLocaleListener.candidates();
        assertTrue(candidates.size() > 500, "only " + candidates.size() + " candidates");
        assertTrue(candidates.contains(Locale.forLanguageTag("tr-TR")));
        assertTrue(candidates.contains(Locale.forLanguageTag("th-TH-u-nu-thai-x-lvariant-TH")));
        // the tag printed on failure must reproduce the pick, variants and extensions included
        for (Locale candidate : candidates) {
            assertFalse(candidate.getLanguage().isEmpty());
            assertEquals(candidate, RandomLocaleListener.choose(candidate.toLanguageTag()),
                    candidate.toString());
        }
        for (int i = 0; i < 20; i++) {
            assertTrue(candidates.contains(RandomLocaleListener.choose(null)));
        }
    }
}
