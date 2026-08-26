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

import java.util.Calendar;
import java.util.Date;

import org.apache.pdfbox.util.DateConverter;

import org.apache.tika.utils.DateUtils;

/** XMP date string -&gt; canonical ISO-8601 UTC (seconds), or null if unrecognizable. */
public final class XmpDates {

    private XmpDates() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Known gap: a partial date (YYYY, YYYY-MM) inflates to a full timestamp -- preserving it
        // breaks Metadata.getDate() (can't re-parse year/month-only). Deferred to the
        // value-representation ticket. PDFBox handles PDF D: dates, producer formats, robust TZ.
        try {
            Calendar c = DateConverter.toCalendar(s);
            if (c != null) {
                return DateUtils.formatDate(c);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            // fall through
        }
        // DateUtils fallback catches EXIF yyyy:MM:dd; not thread-safe, so new instance.
        Date d = new DateUtils().tryToParse(s);
        if (d != null) {
            return DateUtils.formatDate(d);
        }
        return null;
    }
}
