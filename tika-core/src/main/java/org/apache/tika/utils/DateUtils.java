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

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
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
     * So we can return Date objects for these, this is the
     * list (in preference order) of the various ISO-8601
     * variants that we try when processing a date based
     * property.
     */
    private final List<DateFormat> iso8601InputFormats = loadDateFormats();

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        final SimpleDateFormat sdf = new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

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

    private List<DateFormat> loadDateFormats() {
        List<DateFormat> dateFormats = new ArrayList<>();
        // yyyy-mm-ddThh...
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", UTC));   // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", null));    // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss", null));     // Without timezone
        // yyyy-mm-dd hh...
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'", UTC));   // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", null));    // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss", null));     // Without timezone
        // Date without time, set to Midday UTC
        dateFormats.add(createDateFormat("yyyy-MM-dd", MIDDAY));       // Normal date format
        dateFormats.add(createDateFormat("yyyy:MM:dd",
                MIDDAY));              // Image (IPTC/EXIF) format

        return dateFormats;
    }

    /**
     * Tries to parse the date string; returns null if no parse was possible.
     * <p>
     * This is not thread safe!  Wrap in synchronized or create new {@link DateUtils}
     * for each class.
     *
     * @param dateString
     * @return
     */
    public Date tryToParse(String dateString) {
        // Java doesn't like timezones in the form ss+hh:mm
        // It only likes the hhmm form, without the colon
        int n = dateString.length();
        if (dateString.charAt(n - 3) == ':' &&
                (dateString.charAt(n - 6) == '+' || dateString.charAt(n - 6) == '-')) {
            dateString = dateString.substring(0, n - 3) + dateString.substring(n - 2);
        }

        for (DateFormat df : iso8601InputFormats) {
            try {
                return df.parse(dateString);
            } catch (java.text.ParseException e) {
                //swallow
            }
        }
        return null;
    }
}
