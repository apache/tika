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
package org.apache.tika.parser.xmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class XmpDatesTest {

    /** Timezone-bearing dates have a deterministic UTC instant. */
    @Test
    public void testOffsetDatesNormalizeToUtc() {
        assertEquals("2013-10-21T08:29:53Z", XmpDates.normalize("2013-10-21T10:29:53+02:00"));
        assertEquals("2014-05-01T22:27:00Z", XmpDates.normalize("2014-05-01T15:27-07:00"));
        assertEquals("2006-03-15T15:20:00Z", XmpDates.normalize("2006-03-15T15:20Z"));
    }

    /** Minute-precision-with-offset (no seconds): jempbox silently corrupts this; PDFBox is correct. */
    @Test
    public void testMinutePrecisionWithOffset() {
        assertEquals("2016-06-22T09:59:00Z", XmpDates.normalize("2016-06-22T17:59+08:00"));
        assertEquals("2010-03-12T06:09:00Z", XmpDates.normalize("2010-03-12T11:09+05:00"));
    }

    /** Fractional seconds parse (jempbox drops them) and truncate to seconds. */
    @Test
    public void testFractionalSeconds() {
        assertNotNull(XmpDates.normalize("2017-12-18T17:28:56.425+00:00"));
        assertEquals("2018-11-04T09:29:04Z", XmpDates.normalize("2018-11-04T10:29:04.3218973+01:00"));
    }

    /** PDF D: form and date-only forms are recognized. */
    @Test
    public void testOtherRecognizedForms() {
        assertNotNull(XmpDates.normalize("D:20030101120000+05'30'"));
        assertNotNull(XmpDates.normalize("2015-06-12"));
        assertNotNull(XmpDates.normalize("2015:06:12"));   // EXIF date-only, via DateUtils fallback
    }

    /** Non-dates degrade to null so the caller can pass the raw value through. */
    @Test
    public void testGarbageReturnsNull() {
        assertNull(XmpDates.normalize("CPY Document Creation Date"));
        assertNull(XmpDates.normalize(""));
        assertNull(XmpDates.normalize(null));
    }
}
