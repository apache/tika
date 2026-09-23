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
package org.apache.tika.utils;

import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date related utility methods and constants
 */
public class DateUtils {

    /**
     * The UTC time zone. Not sure if {@link TimeZone#getTimeZone(String)}
     * understands "UTC" in all environments, but it'll fall back to GMT
     * in such cases, which is in practice equivalent to UTC.
     */
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Custom time zone used to interpret date values without a time
     * component in a way that most likely falls within the same day
     * regardless of in which time zone it is later interpreted. For
     * example, the "2012-02-17" date would map to "2012-02-17T12:00:00Z"
     * (instead of the default "2012-02-17T00:00:00Z"), which would still
     * map to "2012-02-17" if interpreted in say Pacific time (while the
     * default mapping would result in "2012-02-16" for UTC-8).
     */
    public static final TimeZone MIDDAY = TimeZone.getTimeZone("GMT-12:00");
    /**
     * Returns a ISO 8601 representation of the given date in UTC,
     * truncated to the seconds unit. This method is thread safe and non-blocking.
     *
     * @param date given date
     * @return ISO 8601 date string in UTC, truncated to the seconds unit
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     */
    public static String formatDate(Date date) {
        Calendar calendar = GregorianCalendar.getInstance(UTC, Locale.US);
        calendar.setTime(date);
        return doFormatDate(calendar);
    }

    /**
     * Returns a ISO 8601 representation of the given date in UTC,
     * truncated to the seconds unit. This method is thread safe and non-blocking.
     *
     * @param date given Calendar
     * @return ISO 8601 date string in UTC, truncated to the seconds unit
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     */
    public static String formatDate(Calendar date) {
        return doFormatDate(date);
    }
    /**
     * Returns a ISO 8601 representation of the given date in UTC,
     * truncated to the seconds unit. This method is thread safe and non-blocking.
     *
     * @param date given date
     * @return ISO 8601 date string in UTC, truncated to the seconds unit
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     */

    public static String formatDateUnknownTimezone(Date date) {
        // Create the Calendar object in the system timezone
        Calendar calendar = GregorianCalendar.getInstance(TimeZone.getDefault(), Locale.US);
        calendar.setTime(date);
        // Have it formatted
        String formatted = formatDate(calendar);
        // Strip the timezone details before returning
        return formatted.substring(0, formatted.length() - 1);
    }


    /**
     * Returns ISO-8601 formatted time converted to UTC, truncated to the seconds place
     * @param calendar
     * @return
     */
    private static String doFormatDate(Calendar calendar) {
        return calendar.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * Tries to parse the date string; returns null if it isn't a full-precision date.
     * Zone-less values resolve as UTC. Thread safe: delegates to {@link TikaDates}.
     *
     * @param dateString
     * @return
     */
    public Date tryToParse(String dateString) {
        return TikaDates.parse(dateString).filter(TikaDates.ParsedDate::isFullPrecision)
                .map(d -> Date.from(d.toInstant())).orElse(null);
    }
}
