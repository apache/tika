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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

public class TestLocalesTest {

    @Test
    public void testCalendarVariantsAreNonGregorian() {
        // plain th-TH too: Calendar.getInstance gives th_TH a Buddhist calendar
        assertTrue(TestLocales.nonGregorian(Locale.forLanguageTag("th-TH")));
        assertTrue(TestLocales.nonGregorian(Locale.forLanguageTag("th-TH-u-nu-thai-x-lvariant-TH")));
        assertTrue(TestLocales.nonGregorian(Locale.forLanguageTag("ja-JP-u-ca-japanese-x-lvariant-JP")));
    }

    @Test
    public void testOrdinaryLocalesAreGregorian() {
        for (String tag : new String[]{"th", "ja-JP", "ar-EG", "tr-TR", "de-DE", "en-US"}) {
            assertFalse(TestLocales.nonGregorian(Locale.forLanguageTag(tag)), tag);
        }
    }
}
