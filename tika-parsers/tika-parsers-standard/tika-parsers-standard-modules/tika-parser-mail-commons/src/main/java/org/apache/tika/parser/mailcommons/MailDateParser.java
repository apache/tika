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
package org.apache.tika.parser.mailcommons;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static org.apache.tika.utils.DateUtils.MIDDAY;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.utils.DateUtils;

public class MailDateParser {

    //TIKA-1970 Mac Mail's format
    private static final Pattern GENERAL_TIME_ZONE_NO_MINUTES_PATTERN =
            Pattern.compile("(?:UTC|GMT)([+-])(\\d?\\d)\\Z");

    //find a time ending in am/pm without a space: 10:30am and
    //use this pattern to insert space: 10:30 am
    private static final Pattern AM_PM = Pattern.compile("(?i)(\\d)([ap]m)\\b");

    //Taken nearly directly from mime4j
    private static Map<Long, String> monthOfYear() {
        HashMap<Long, String> result = new HashMap<>();
        result.put(1L, "JAN");
        result.put(2L, "FEB");
        result.put(3L, "MAR");
        result.put(4L, "APR");
        result.put(5L, "MAY");
        result.put(6L, "JUN");
        result.put(7L, "JUL");
        result.put(8L, "AUG");
        result.put(9L, "SEP");
        result.put(10L, "OCT");
        result.put(11L, "NOV");
        result.put(12L, "DEC");
        return result;
    }

    private static Map<Long, String> dayOfWeek() {
        HashMap<Long, String> result = new HashMap<>();
        result.put(1L, "MON");
        result.put(2L, "TUE");
        result.put(3L, "WED");
        result.put(4L, "THU");
        result.put(5L, "FRI");
        result.put(6L, "SAT");
        result.put(7L, "SUN");
        return result;
    }

    private static final int INITIAL_YEAR = 1970;
    public static final DateTimeFormatter RFC_5322 = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .optionalStart()
            .appendText(DAY_OF_WEEK, dayOfWeek())
            .appendLiteral(", ")
            .optionalEnd()
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendText(MONTH_OF_YEAR, monthOfYear())
            .appendLiteral(' ')
            .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .optionalStart()
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND, 3)
            .optionalEnd()
            .optionalStart()
            .appendLiteral(' ')
            .appendOffset("+HHMM", "GMT")
            .optionalEnd()
            .toFormatter(Locale.US)
            //.withZone(ZoneId.of("GMT")) see TIKA-3735
            .withResolverStyle(ResolverStyle.LENIENT)
            .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_DAY, MINUTE_OF_HOUR,
                    SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter RFC_5322_LENIENT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .optionalStart()
            .appendPattern("EEEEE")
            .appendLiteral(' ')
            .optionalEnd()
            .optionalStart()
            .appendPattern("E")
            .appendLiteral(' ')
            .optionalEnd()
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
            .appendLiteral(' ')
            .appendPattern("MMM")
            .appendLiteral(' ')
            .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 1, 2, SignStyle.NEVER)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 1, 2, SignStyle.NEVER)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .optionalStart()
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND, 3, 5, SignStyle.NEVER)
            .optionalEnd()
            .optionalStart()
            .optionalStart()
            .appendLiteral(' ')
            .optionalEnd()
            .appendOffset("+HHMM", "GMT")
            .optionalEnd()
            .optionalStart()
            .optionalStart()
            .appendLiteral(' ')
            .optionalEnd()
            .optionalStart()
            .appendZoneId()
            .optionalEnd()
            .optionalStart()
            .optionalStart()
            .appendLiteral(' ')
            .optionalEnd()
            .optionalStart()
            .appendZoneRegionId()
            .optionalEnd()
            .toFormatter(Locale.US)
            //.withZone(ZoneId.of("GMT")) see TIKA-3735
            .withResolverStyle(ResolverStyle.LENIENT)
            .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_DAY, MINUTE_OF_HOUR,
                    SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter MM_SLASH_DD_SLASH_YYYY =
            new DateTimeFormatterBuilder()
                    .appendPattern("M/d/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter().withZone(MIDDAY.toZoneId());

    public static final DateTimeFormatter DD_SLASH_MM_SLASH_YYYY =
            new DateTimeFormatterBuilder()
                    .appendPattern("d/M/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter().withZone(MIDDAY.toZoneId());
    public static final DateTimeFormatter MMM_D_YY =
            DateTimeFormatter.ofPattern("MMM d yy", Locale.US)
                    .withZone(MIDDAY.toZoneId());

    public static final DateTimeFormatter EEE_D_MMM_YY =
            DateTimeFormatter.ofPattern("EEE d MMM yy", Locale.US)
                    .withZone(MIDDAY.toZoneId());

    public static final DateTimeFormatter D_MMM_YY =
            DateTimeFormatter.ofPattern("d MMM yy", Locale.US)
                    .withZone(MIDDAY.toZoneId());

    public static final DateTimeFormatter YY_SLASH_MM_SLASH_DD =
            new DateTimeFormatterBuilder()
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .appendPattern("/M/d")
                    .toFormatter().withZone(MIDDAY.toZoneId());


    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            EEE_D_MMM_YY,
            D_MMM_YY,
            MMM_D_YY,
            MM_SLASH_DD_SLASH_YYYY,//try American first?
            DD_SLASH_MM_SLASH_YYYY,//if that fails, try rest of world?
            YY_SLASH_MM_SLASH_DD
    };



    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
        /*DateTimeFormatter.ofPattern("MMM dd yy hh:mm a", Locale.US).withZone(UTC),
            DateTimeFormatter.ofPattern("EEE d MMM yy HH:mm:ss z", Locale.US).withZone(UTC),
            DateTimeFormatter.ofPattern("EEE d MMM yy HH:mm:ss", Locale.US),
            // Sunday, May 15 2016 1:32 PM
            DateTimeFormatter.ofPattern("EEEEE MMM d yy hh:mm a", Locale.US),
            //16 May 2016 at 09:30:32  GMT+1 (Mac Mail TIKA-1970)
            DateTimeFormatter.ofPattern("d MMM yy 'at' HH:mm:ss z", Locale.US).withZone(UTC),
            DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss", Locale.US),*/
            RFC_5322_LENIENT,

            //this assumes US ordering M/d/ -- we need to add non-US too
            //7/20/95 1:12PM OR 7/20/95 1:12:14PM OR 06/24/2008 Tuesday 11 AM
            /*new DateTimeFormatterBuilder()
                    .appendPattern("M/d/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .optionalStart()
                    .appendLiteral(' ')
                    .appendText(DAY_OF_WEEK, dayOfWeekLenient())
                    .optionalEnd()
                    .appendLiteral(' ')
                    .appendValue(ChronoField.HOUR_OF_AMPM, 1, 2, SignStyle.NEVER)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 1, 2, SignStyle.NEVER)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalEnd()
                    .appendLiteral(' ')
                    .appendText(ChronoField.AMPM_OF_DAY)
                    .toFormatter().withZone(MIDDAY.toZoneId())*/

    };
    public static Date parseDate(String string) throws ParseException {
        if (string != null) {
            string = string.trim();
            string = string.toUpperCase(Locale.US);
        }
        return Date.from(Instant.from(RFC_5322.parse(string, new ParsePosition(0))));
    }

    public static Date parseDateLenient(String text) {
        if (text == null) {
            return null;
        }
        text = text.replaceAll("\\s+", " ").trim();
        text = text.toUpperCase(Locale.US);
        try {
            return parseDate(text);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //ignore
        }

        String normalized = normalize(text);
        for (DateTimeFormatter dateTimeFormatter : DATE_TIME_FORMATTERS) {
            try {
                TemporalQuery<TemporalUnit> query = TemporalQueries.precision();

                TemporalAccessor temporalAccessor = dateTimeFormatter.parse(normalized);
                if (hasInstantSeconds(temporalAccessor)) {
                    System.out.println("precision: " + temporalAccessor.query(query));
                    System.out.println(temporalAccessor.get(SECOND_OF_MINUTE));

                    System.out.println(temporalAccessor.getClass() + " : " + temporalAccessor);
                }
                return Date.from(Instant.from(dateTimeFormatter.parse(normalized)));
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                System.err.println(dateTimeFormatter);
                e.printStackTrace();
                //ignore
            }
        }
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                TemporalAccessor temporalAccessor = dateFormatter.parse(normalized);
                ZonedDateTime localDate = LocalDate.from(temporalAccessor)
                        .atStartOfDay()
                        .atZone(MIDDAY.toZoneId());
                return Date.from(Instant.from(localDate));
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                //e.printStackTrace();
                //ignore
            }
        }
        return null;
    }

    private static boolean hasInstantSeconds(TemporalAccessor temporalAccessor) {
        try {
            temporalAccessor.getLong(INSTANT_SECONDS);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static String normalize(String text) {
        //strip out commas
        text = text.replaceAll(",", "");

        Matcher matcher = GENERAL_TIME_ZONE_NO_MINUTES_PATTERN.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceFirst("GMT$1$2:00");
        }

        matcher = AM_PM.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceFirst("$1 $2");
        }
        return text;
    }
}
