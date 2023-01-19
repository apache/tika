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
import static java.time.temporal.ChronoField.AMPM_OF_DAY;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static org.apache.tika.utils.DateUtils.MIDDAY;

import java.text.ParseException;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.utils.StringUtils;

/**
 * Dates in emails are a mess.  There are at least two major date related bugs in JDK 8.
 * And, I've found differing behavior, bug or not, between JDK 8 and JDK 11/17.
 * This class does its best to parse date strings.  It does have a US-based date bias.
 * Please open a ticket to fix this as needed.  We can also add overrides via the parser config
 * to manage customization of date formats.
 *
 * This code does not spark joy especially given the diffs in behavior between jdk versions.
 *
 * At some point, we should probably try joda or, heaven forfend, a pile of regexes.
 */
public class MailDateParser {

    //TIKA-1970 Mac Mail's format is GMT+1 so we need to check for hour only
    //Also, there are numerous bugs in jdk 8 with localized offsets
    //so we need to get rid of the GMT/UTC component (e.g. https://bugs.openjdk.org/browse/JDK-8154520)
    private static final Pattern LOCALIZED_OFFSET_PATTERN =
            Pattern.compile("(?:UTC|GMT)\\s*([-+])\\s*(\\d?\\d):?(\\d\\d)?\\Z");

    //this is used to strip junk after a fairly full offset:
    // Wed, 26 Jan 2022 09:14:37 +0100 (CET)
    // Also insert colon to avoid, ahem, behavior that is different in jdk 11 and jdk 17 than jdk8
    // with "Mon, 9 May 2016 3:32:00 +0200"

    //we add the first pattern -\\d\\d-\\d\\d\\d\\d so that we skip over 10-10-2000 via
    //the while loop.
    private static final Pattern OFFSET_PATTERN =
            Pattern.compile("(?:(?:-\\d\\d-\\d{4})|([-+])\\s*(\\d?\\d):?(\\d\\d))");

    private static final Pattern DAYS_OF_WEEK =
            Pattern.compile("(?:\\A| )(MON|MONDAY|TUE|TUES|TUESDAY|WED|WEDNESDAY|THU|THUR|THURS" +
                    "|THURSDAY|FRI|FRIDAY|SAT|SATURDAY|SUN|SUNDAY) ");

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

    private static final DateTimeFormatter TIME_ZONE_FORMATTER
            = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .optionalStart()
            .appendLiteral(' ') //optional space before any of the time zone offset/ids
            .optionalEnd()
            .optionalStart()
            .appendZoneId()
            .optionalEnd()
            .optionalStart()
            .appendPattern("X")//localized zone offset, e.g. Z; -08; -0830; -08:30; -083015; -08:30:15
            .optionalEnd()
            .optionalStart()
            .appendPattern("z")//zone name, e.g. PST
            .optionalEnd().toFormatter(Locale.US);


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
            .append(TIME_ZONE_FORMATTER)
            .optionalEnd()
            .toFormatter(Locale.US)
            //.withZone(ZoneId.of("GMT")) see TIKA-3735
            .withResolverStyle(ResolverStyle.LENIENT)
            .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR,
                    HOUR_OF_DAY, MINUTE_OF_HOUR,
                    SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);


    //this differs only from RFC_5322_LENIENT in requiring am/pm
    public static final DateTimeFormatter RFC_5322_AMPM_LENIENT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
            .appendLiteral(' ')
            .appendPattern("MMM")
            .appendLiteral(' ')
            .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_AMPM, 1, 2, SignStyle.NEVER)
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
            .appendLiteral(' ') //optional space before am/pm
            .optionalEnd()
            .appendText(ChronoField.AMPM_OF_DAY)
            .optionalStart()
            .optionalStart()
            .append(TIME_ZONE_FORMATTER)
            .optionalEnd()
            .toFormatter(Locale.US)
            //.withZone(ZoneId.of("GMT")) see TIKA-3735
            .withResolverStyle(ResolverStyle.LENIENT)
            .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_AMPM, AMPM_OF_DAY,
                    MINUTE_OF_HOUR,
                    SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);


    public static final DateTimeFormatter MMM_D_YYYY_HH_MM_AM_PM = // "July 9 2012 10:10:10 am UTC"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendPattern("MMM")
                    .appendLiteral(' ')
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                    .appendLiteral(' ')
                    .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
                    .appendLiteral(' ')
                    .appendValue(ChronoField.HOUR_OF_AMPM, 1, 2, SignStyle.NEVER)
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
                    .appendLiteral(' ') //optional space before am/pm
                    .optionalEnd()
                    .appendText(ChronoField.AMPM_OF_DAY)
                    .optionalStart()
                    .append(TIME_ZONE_FORMATTER)
                    .optionalEnd()
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_AMPM, AMPM_OF_DAY,
                            MINUTE_OF_HOUR,
                            SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter MMM_D_YYYY_HH_MM = // "July 9 2012 10:10:10 UTC"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendPattern("MMM")
                    .appendLiteral(' ')
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
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
                    .append(TIME_ZONE_FORMATTER)
                    .optionalEnd()
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_DAY,
                            MINUTE_OF_HOUR,
                            SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter MM_SLASH_DD_SLASH_YY_HH_MM = //
            // US-based month/day ordering !!!! e.g. 7/9/2012 10:10:10"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NEVER)
                    .appendLiteral('/')
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                    .appendLiteral('/')
                    .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
                    .appendLiteral(' ')
                    .appendValue(HOUR_OF_DAY, 1, 2, SignStyle.NEVER)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 1, 2, SignStyle.NEVER)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral('.')
                    .appendValue(MILLI_OF_SECOND, 3, 5, SignStyle.NEVER)
                    .optionalEnd()
                    .optionalStart()
                    .append(TIME_ZONE_FORMATTER)
                    .optionalEnd()
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_DAY,
                            MINUTE_OF_HOUR,
                            SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);
    public static final DateTimeFormatter MM_SLASH_DD_SLASH_YY_HH_MM_AM_PM =
            // US-based month/day ordering !!!! e.g. 7/9/2012 10:10:10 AM UTC"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NEVER)
                    .appendLiteral('/')
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                    .appendLiteral('/')
                    .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
                    .appendLiteral(' ')
                    .appendValue(HOUR_OF_AMPM, 1, 2, SignStyle.NEVER)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 1, 2, SignStyle.NEVER)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral('.')
                    .appendValue(MILLI_OF_SECOND, 3, 5, SignStyle.NEVER)
                    .optionalEnd()
                    .optionalStart()
                    .appendLiteral(' ')
                    .optionalEnd()
                    .appendText(AMPM_OF_DAY)
                    .optionalStart()
                    .append(TIME_ZONE_FORMATTER)
                    .optionalEnd()
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_AMPM,
                            AMPM_OF_DAY,
                            MINUTE_OF_HOUR,
                            SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter YYYY_MM_DD_HH_MM = // "2012-10-10 10:10:10 UTC"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendValue(YEAR, 4)
                    .appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(DAY_OF_MONTH, 2, 2, SignStyle.NEVER)
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
                    .append(TIME_ZONE_FORMATTER)
                    .optionalEnd()
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR, HOUR_OF_DAY,
                            MINUTE_OF_HOUR,
                            SECOND_OF_MINUTE, MILLI_OF_SECOND, OFFSET_SECONDS);

    public static final DateTimeFormatter YYYY_MM_DD = // "2012-10-10"
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendValue(YEAR, 4)
                    .appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR, 2, 2, SignStyle.NEVER)
                    .appendLiteral('-')
                    .appendValue(DAY_OF_MONTH, 2, 2, SignStyle.NEVER)
                    .toFormatter(Locale.US)
                    //.withZone(ZoneId.of("GMT")) see TIKA-3735
                    .withResolverStyle(ResolverStyle.LENIENT)
                    .withResolverFields(DAY_OF_MONTH, MONTH_OF_YEAR, YEAR);

    public static final DateTimeFormatter MM_SLASH_DD_SLASH_YYYY =
            new DateTimeFormatterBuilder()
                    .appendPattern("M/d/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter(Locale.US).withZone(MIDDAY.toZoneId());

    public static final DateTimeFormatter DD_SLASH_MM_SLASH_YYYY =
            new DateTimeFormatterBuilder()
                    .appendPattern("d/M/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter(Locale.US).withZone(MIDDAY.toZoneId());
    public static final DateTimeFormatter MMM_DD_YY =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendPattern("MMM")
                    .appendLiteral(' ')
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                    .appendLiteral(' ')
                    .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter(Locale.US);

    public static final DateTimeFormatter DD_MMM_YY =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                    .appendLiteral(' ')
                    .appendPattern("MMM")
                    .appendLiteral(' ')
                    .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
                    .toFormatter(Locale.US);

    public static final DateTimeFormatter YY_SLASH_MM_SLASH_DD =
            new DateTimeFormatterBuilder()
                    .appendValueReduced(ChronoField.YEAR, 2, 4, INITIAL_YEAR)
                    .appendPattern("/M/d")
                    .toFormatter(Locale.US).withZone(MIDDAY.toZoneId());


    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DD_MMM_YY,
            MMM_DD_YY,
            YYYY_MM_DD,
            MM_SLASH_DD_SLASH_YYYY,//try American first?
            DD_SLASH_MM_SLASH_YYYY,//if that fails, try rest of world?
            YY_SLASH_MM_SLASH_DD
    };



    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
            RFC_5322_LENIENT,
            RFC_5322_AMPM_LENIENT,
            MMM_D_YYYY_HH_MM,
            MMM_D_YYYY_HH_MM_AM_PM,
            YYYY_MM_DD_HH_MM,
            MM_SLASH_DD_SLASH_YY_HH_MM,
            MM_SLASH_DD_SLASH_YY_HH_MM_AM_PM

    };
    public static Date parseRFC5322(String string) throws ParseException {
        //this fails on: MON, 9 MAY 2016 3:32:00 GMT+0200 ... it stops short and doesn't include
        // the +0200?!
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
        String normalized = normalize(text);
        for (DateTimeFormatter dateTimeFormatter : DATE_TIME_FORMATTERS) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(normalized, dateTimeFormatter);
                return Date.from(Instant.from(zonedDateTime));
            } catch (SecurityException e) {
                throw e;
            } catch (DateTimeParseException e) {

                //There's a bug in java 8 that if we include .withZone in the DateTimeFormatter,
                //that will override the offset/timezone id even if it included
                // in the original string.  This is fixed in later versions of Java.
                // Once we move to Java 11, we can get rid of this. Can't make this up...
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(normalized, dateTimeFormatter);
                    return Date.from(Instant.from(localDateTime.atOffset(UTC)));
                } catch (SecurityException e2) {
                    throw e2;
                } catch (Exception e2) {
                    //swallow
                }
            } catch (Exception e) {
                //can get StringIndexOutOfBoundsException because of a bug in java 8
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

    protected static String normalize(String text) {

        text = text.toUpperCase(Locale.US);

        //strip out commas
        text = text.replaceAll(",", "");

        //1) strip off extra stuff after +0800, e.g. "Mon, 9 May 2016 7:32:00 UTC+0600 (BST)",
        //2) insert a colon btwn hrs and minutes to avoid a difference in behavior
        // between jdk 8 and jdk 11+17
        Matcher matcher = OFFSET_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                text = text.substring(0, matcher.start());
                text += matcher.group(1) + StringUtils.leftPad(matcher.group(2), 2, '0') + ":" +
                        matcher.group(3);
                break;
            }
        }

        matcher = LOCALIZED_OFFSET_PATTERN.matcher(text);
        if (matcher.find()) {
            text = buildLocalizedOffset(matcher, text);
        }

        matcher = AM_PM.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceFirst("$1 $2");
        }
        //The rfc_lenient parser had a problem parsing dates
        //with days of week missing and a timezone: 9 May 2016 01:32:00 UTC
        //The day of week is not used in the resolvers, so we may as well throw
        //out that info
        matcher = DAYS_OF_WEEK.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceAll(" ");
        }
        //16 May 2016 at 09:30:32  GMT+1
        text = text.replaceAll("(?i) at ", " ");
        //just cause
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static String buildLocalizedOffset(Matcher matcher, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, matcher.start()));
        sb.append(matcher.group(1));// +/-
        sb.append(StringUtils.leftPad(matcher.group(2), 2, '0'));//HH
        sb.append(":");
        if (matcher.group(3) != null) {
            sb.append(matcher.group(3));
        } else {
            sb.append("00");
        }
        sb.append(text.substring(matcher.end()));
        return sb.toString();
    }
}
