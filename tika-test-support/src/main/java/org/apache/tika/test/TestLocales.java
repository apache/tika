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

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Conditions for {@code @DisabledIf} / {@code @EnabledIf} on locale-sensitive tests. */
public final class TestLocales {

    private TestLocales() {
    }

    /**
     * True when the default locale's calendar is not Gregorian (th-TH and its legacy variant,
     * ja-JP-u-ca-japanese-x-lvariant-JP). Libraries that build dates from
     * {@code Calendar.getInstance()} then set era-relative years. Use as
     * {@code @DisabledIf("org.apache.tika.test.TestLocales#nonGregorianDefault")} on a test that
     * reads such a date, with a comment naming the library.
     */
    public static boolean nonGregorianDefault() {
        return nonGregorian(Locale.getDefault());
    }

    static boolean nonGregorian(Locale locale) {
        // not instanceof GregorianCalendar: BuddhistCalendar extends it
        return !"gregory".equals(Calendar.getInstance(TimeZone.getTimeZone("UTC"), locale).getCalendarType());
    }
}
