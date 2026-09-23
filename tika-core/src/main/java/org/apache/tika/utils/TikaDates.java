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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient-in-format, strict-in-value date parsing for the date strings found in documents
 * (XMP, PDF, mail headers, EXIF, office metadata).
 * <p>
 * The input is routed by shape to one family (ISO/W3C-DTF, PDF {@code D:}/compact, EXIF,
 * RFC 5322 and month-name forms, ctime, slash dates). Each family must match the whole string,
 * and every field is validated; nothing is rolled over or prefix-matched. Anything else is
 * rejected rather than guessed.
 * <p>
 * Conventions: no zone means UTC (never the JVM default); a date-only value is midday UTC;
 * two-digit years follow RFC 5322 (00-49 is 20xx, 50-99 is 19xx); slash dates are month-first
 * unless the first field is over 12; years outside {@link #MIN_YEAR}..{@link #MAX_YEAR} are
 * rejected. Partial dates (year or year-month) parse but never {@link #normalize}.
 * <p>
 * Thread-safe.
 */
public final class TikaDates {

    public static final int MIN_YEAR = 1000;
    public static final int MAX_YEAR = 2200;

    public enum Precision { YEAR, MONTH, DAY, MINUTE, SECOND }

    /** A parsed date. The local fields are as written; the offset is null when none was given. */
    public static final class ParsedDate {
        private final LocalDateTime local;
        private final ZoneOffset offset;
        private final Precision precision;

        ParsedDate(LocalDateTime local, ZoneOffset offset, Precision precision) {
            this.local = local;
            this.offset = offset;
            this.precision = precision;
        }

        public LocalDateTime getLocalDateTime() {
            return local;
        }

        /** @return the explicit offset, or null if the source gave none */
        public ZoneOffset getOffset() {
            return offset;
        }

        public boolean hasZone() {
            return offset != null;
        }

        public Precision getPrecision() {
            return precision;
        }

        /** Day precision or finer; only these may be stored on a DATE property. */
        public boolean isFullPrecision() {
            return precision.compareTo(Precision.DAY) >= 0;
        }

        /** The instant, reading a zone-less value as UTC. */
        public Instant toInstant() {
            return local.toInstant(offset == null ? ZoneOffset.UTC : offset);
        }

        /** Canonical {@code yyyy-MM-dd'T'HH:mm:ss'Z'}, truncated to seconds. */
        public String toCanonicalString() {
            return CANONICAL.format(toInstant().truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
        }

        @Override
        public String toString() {
            return local + (offset == null ? "" : offset.toString()) + " (" + precision + ")";
        }
    }

    private static final DateTimeFormatter CANONICAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    // offsets: Z, +hh, +hhmm, +hh:mm, +h:mm, optionally prefixed by GMT/UTC (e.g. "GMT+0200")
    private static final String OFFSET = "(?:(?:GMT|UTC)?([+-])(\\d{1,2})(?::?(\\d{2}))?)";
    private static final String ZONE = "(?:\\s*(?:" + OFFSET + "|(Z|[A-Z]{1,5})))?";
    private static final String TIME =
            "(\\d{1,2}):(\\d{2})(?::(\\d{1,2})(?:[.,]\\d{1,9})?)?(?:\\s*([AP])\\.?M\\.?)?";

    // producer quirks seen in XMP: HH:mmss, HH.mm.ss, single-digit seconds, trailing :cc centiseconds
    private static final Pattern ISO = Pattern.compile("(\\d{4})(?:-(\\d{1,2})(?:-(\\d{1,2})"
            + "(?:(?:T|\\s+)(\\d{1,2})([:.])(\\d{2})(?:(?:\\5|(?<=:\\d\\d))(\\d{1,2})(?::\\d{2})?(?:[.,]\\d{1,9})?)?"
            + "(?:\\s*([AP])\\.?M\\.?)?)?)?)?(?:Z00:?00)?" + ZONE);
    private static final Pattern PDF = Pattern.compile("(D:)?\\s*(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?"
            + "(?:(Z)(?:00'?00'?)?|([+-])(\\d{2})(?:'?(\\d{2})'?)?)?");
    private static final Pattern EXIF = Pattern.compile("(\\d{4}):(\\d{2}):(\\d{2})"
            + "(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,9})?)?)?" + ZONE);
    // d MMM yyyy [time] [zone]
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(\\d{1,2})[ -]([A-Z]{3,9})\\.?,?[ -](\\d{4}|\\d{2})(?:,?\\s+" + TIME + ")?" + ZONE);
    // MMM d[,] yyyy [time] [zone]
    private static final Pattern MONTH_DAY = Pattern.compile(
            "([A-Z]{3,9})\\.?\\s+(\\d{1,2})(?:ST|ND|RD|TH)?,?\\s+(\\d{4})(?:,?\\s+" + TIME + ")?" + ZONE);
    // ctime: MMM d HH:mm:ss [zone-name] yyyy [offset]
    private static final Pattern CTIME = Pattern.compile(
            "([A-Z]{3,9})\\s+(\\d{1,2})\\s+" + TIME + "(?:\\s+([A-Z]{1,5}))?\\s+(\\d{4})" + ZONE);
    // M/d/yy[yy] or d/M/yy[yy] [time] [zone]
    private static final Pattern SLASH = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})/(\\d{4}|\\d{2})(?:,?\\s+" + TIME + ")?" + ZONE);
    private static final Pattern SLASH_YMD = Pattern.compile(
            "(\\d{4})/(\\d{1,2})/(\\d{1,2})(?:\\s+" + TIME + ")?" + ZONE);

    private static final Pattern WEEKDAY = Pattern.compile(
            "^(?:MON|TUE|TUES|WED|THU|THUR|THURS|FRI|SAT|SUN)(?:DAY|NESDAY|SDAY|RSDAY|URDAY)?\\.?,?\\s+");
    private static final Pattern TRAILING_COMMENT = Pattern.compile("\\s*\\([^)]*\\)$");
    // "-0400 EDT": a name after an explicit offset is redundant
    private static final Pattern OFFSET_THEN_NAME = Pattern.compile("([+-]\\d{2}:?\\d{2})\\s+[A-Z]{2,5}$");
    private static final Pattern AFTER_OFFSET = Pattern.compile("\\d:\\d{2}(?::\\d{2})?\\s*([+-]\\d{4})(?![\\d:]).+$");
    // RFC 5322 allows a missing sign only for the zero offset
    private static final Pattern UNSIGNED_ZERO = Pattern.compile("\\s0000$");

    private static final Map<String, Integer> ZONE_NAMES = Map.ofEntries(
            Map.entry("Z", 0), Map.entry("UT", 0), Map.entry("UTC", 0), Map.entry("GMT", 0),
            Map.entry("EST", -5), Map.entry("EDT", -4), Map.entry("CST", -6), Map.entry("CDT", -5),
            Map.entry("MST", -7), Map.entry("MDT", -6), Map.entry("PST", -8), Map.entry("PDT", -7),
            Map.entry("CET", 1), Map.entry("CEST", 2), Map.entry("MET", 1), Map.entry("MEST", 2),
            Map.entry("WET", 0), Map.entry("WEST", 1), Map.entry("EET", 2), Map.entry("EEST", 3),
            Map.entry("MSK", 3), Map.entry("JST", 9), Map.entry("HST", -10), Map.entry("AKST", -9),
            Map.entry("AKDT", -8), Map.entry("NZST", 12), Map.entry("NZDT", 13));

    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG",
            "SEP", "OCT", "NOV", "DEC"};

    private TikaDates() {
    }

    /** @return the parsed date, or empty if the string is not a date this class accepts */
    public static Optional<ParsedDate> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(route(s)).filter(TikaDates::inBounds);
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @return canonical UTC string for a full-precision date, else null */
    public static String normalize(String raw) {
        return parse(raw).filter(ParsedDate::isFullPrecision).map(ParsedDate::toCanonicalString).orElse(null);
    }

    private static boolean inBounds(ParsedDate d) {
        int y = d.toInstant().atOffset(ZoneOffset.UTC).getYear();
        return y >= MIN_YEAR && y <= MAX_YEAR;
    }

    private static ParsedDate route(String s) {
        s = TRAILING_COMMENT.matcher(s).replaceFirst("");
        if (s.isEmpty()) {
            return null;
        }
        char c = s.charAt(0);
        if (c == 'D' && s.startsWith("D:")) {
            return s.length() > 6 && s.charAt(6) == '-' ? iso(s.substring(2)) : pdf(s);
        }
        if (Character.isDigit(c)) {
            if (s.length() > 4 && Character.isDigit(s.charAt(1)) && Character.isDigit(s.charAt(2))
                    && Character.isDigit(s.charAt(3))) {
                char sep = s.charAt(4);
                if (sep == '-') {
                    return iso(s);
                }
                if (sep == ':') {
                    return exif(s);
                }
                if (sep == '/') {
                    return slashYmd(s);
                }
                if (Character.isDigit(sep)) {
                    return pdf(s);
                }
                return null;
            }
            if (s.length() == 4) {
                return iso(s);
            }
            if (s.indexOf('/') > 0 && s.indexOf('/') < 3) {
                return slash(s);
            }
            return dayMonth(stripMailNoise(s));
        }
        String t = WEEKDAY.matcher(s).replaceFirst("");
        if (t.isEmpty() || t.equals(s) && !Character.isLetter(c)) {
            return null;
        }
        if (Character.isDigit(t.charAt(0))) {
            return route(t);
        }
        t = stripMailNoise(t);
        ParsedDate d = monthDay(t);
        return d != null ? d : ctime(t);
    }

    private static String stripMailNoise(String s) {
        Matcher junk = AFTER_OFFSET.matcher(s);
        if (junk.find()) {
            s = s.substring(0, junk.end(1));
        }
        s = TRAILING_COMMENT.matcher(s).replaceFirst("");
        s = s.replace(" AT ", " ");
        s = OFFSET_THEN_NAME.matcher(s).replaceFirst("$1");
        return UNSIGNED_ZERO.matcher(s).replaceFirst(" +0000");
    }

    private static ParsedDate iso(String s) {
        Matcher m = ISO.matcher(s.replace("'", ""));   // PDF-style offsets in ISO strings: -06'00'

        if (!m.matches()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        if (m.group(2) == null) {
            return partial(year, 1, Precision.YEAR);
        }
        if (m.group(3) == null) {
            return partial(year, Integer.parseInt(m.group(2)), Precision.MONTH);
        }
        return build(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                m.group(4), m.group(6), m.group(7), m.group(8), zone(m, 9));
    }

    private static ParsedDate pdf(String s) {
        Matcher m = PDF.matcher(s);
        if (!m.matches()) {
            return null;
        }
        boolean prefixed = m.group(1) != null;
        int digits = 4;
        for (int g = 3; g <= 7 && m.group(g) != null; g++) {
            digits += 2;
        }
        // bare digit strings are dates only at full lengths; shorter runs are counters and ids
        if (!prefixed && digits != 8 && digits != 12 && digits != 14) {
            return null;
        }
        int year = Integer.parseInt(m.group(2));
        if (m.group(3) == null) {
            return partial(year, 1, Precision.YEAR);
        }
        if (m.group(4) == null) {
            return partial(year, Integer.parseInt(m.group(3)), Precision.MONTH);
        }
        if (m.group(5) != null && m.group(6) == null) {
            return null;
        }
        ZoneOffset off = null;
        if (m.group(8) != null) {
            off = ZoneOffset.UTC;
        } else if (m.group(9) != null) {
            off = offset(m.group(9), m.group(10), m.group(11));
        }
        return build(year, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                m.group(5), m.group(6), m.group(7), null, new ZoneOffset[]{off});
    }

    private static ParsedDate exif(String s) {
        Matcher m = EXIF.matcher(s);
        if (!m.matches()) {
            return null;
        }
        return build(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                m.group(4), m.group(5), m.group(6), null, zone(m, 7));
    }

    private static ParsedDate dayMonth(String s) {
        Matcher m = DAY_MONTH.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int month = month(m.group(2));
        if (month < 0) {
            return null;
        }
        return build(year(m.group(3)), month, Integer.parseInt(m.group(1)),
                m.group(4), m.group(5), m.group(6), m.group(7), zone(m, 8));
    }

    private static ParsedDate monthDay(String s) {
        Matcher m = MONTH_DAY.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int month = month(m.group(1));
        if (month < 0) {
            return null;
        }
        return build(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(2)),
                m.group(4), m.group(5), m.group(6), m.group(7), zone(m, 8));
    }

    private static ParsedDate ctime(String s) {
        Matcher m = CTIME.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int month = month(m.group(1));
        if (month < 0) {
            return null;
        }
        ZoneOffset[] z = zone(m, 9);
        if (z == null) {
            return null;
        }
        if (m.group(7) != null) {
            ZoneOffset named = named(m.group(7));
            if (named == null || z[0] != null) {
                return null;
            }
            z = new ZoneOffset[]{named};
        }
        return build(Integer.parseInt(m.group(8)), month, Integer.parseInt(m.group(2)),
                m.group(3), m.group(4), m.group(5), m.group(6), z);
    }

    private static ParsedDate slash(String s) {
        Matcher m = SLASH.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        int month = a > 12 ? b : a;
        int day = a > 12 ? a : b;
        return build(year(m.group(3)), month, day, m.group(4), m.group(5), m.group(6), m.group(7), zone(m, 8));
    }

    private static ParsedDate slashYmd(String s) {
        Matcher m = SLASH_YMD.matcher(s);
        if (!m.matches()) {
            return null;
        }
        return build(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                m.group(4), m.group(5), m.group(6), m.group(7), zone(m, 8));
    }

    /** Reads the ZONE groups starting at {@code g}: sign, hours, minutes, name. Null means reject. */
    private static ZoneOffset[] zone(Matcher m, int g) {
        if (m.group(g) != null) {
            return new ZoneOffset[]{offset(m.group(g), m.group(g + 1), m.group(g + 2))};
        }
        if (m.group(g + 3) != null) {
            return namedOrUnknown(m.group(g + 3));
        }
        return new ZoneOffset[]{null};
    }

    private static ZoneOffset named(String name) {
        Integer h = ZONE_NAMES.get(name);
        return h == null ? null : ZoneOffset.ofHours(h);
    }

    // RFC 5322 4.3: an unrecognized alphabetic zone (BST, IST, ...: often ambiguous) is unknown, not an error
    private static ZoneOffset[] namedOrUnknown(String name) {
        ZoneOffset named = named(name);
        if (named != null) {
            return new ZoneOffset[]{named};
        }
        return name.length() >= 2 ? new ZoneOffset[]{null} : null;
    }

    private static ZoneOffset offset(String sign, String hh, String mm) {
        int h = Integer.parseInt(hh);
        int min = mm == null ? 0 : Integer.parseInt(mm);
        if ("-".equals(sign) ? h > 12 : h > 14) {
            throw new DateTimeException("offset out of range");
        }
        return "-".equals(sign) ? ZoneOffset.ofHoursMinutes(-h, -min) : ZoneOffset.ofHoursMinutes(h, min);
    }

    private static int month(String name) {
        if (name.length() < 3) {
            return -1;
        }
        String p = name.substring(0, 3);
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(p) && fullMonthName(i).startsWith(name)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String fullMonthName(int i) {
        return Month.of(i + 1).name();
    }

    private static int year(String y) {
        int v = Integer.parseInt(y);
        if (y.length() == 2) {
            return v < 50 ? 2000 + v : 1900 + v;
        }
        return v;
    }

    private static ParsedDate partial(int year, int month, Precision p) {
        return new ParsedDate(LocalDateTime.of(year, month, 1, 12, 0), null, p);
    }

    private static ParsedDate build(int year, int month, int day, String hh, String mi, String ss,
                                    String ampm, ZoneOffset[] zone) {
        if (zone == null) {
            return null;
        }
        if (hh == null) {
            return new ParsedDate(LocalDateTime.of(year, month, day, 12, 0), zone[0], Precision.DAY);
        }
        int hour = Integer.parseInt(hh);
        if (ampm != null) {
            if (hour < 1 || hour > 12) {
                return null;
            }
            hour = hour % 12 + ("P".equals(ampm) ? 12 : 0);
        }
        int sec = ss == null ? 0 : Integer.parseInt(ss);
        return new ParsedDate(LocalDateTime.of(year, month, day, hour, Integer.parseInt(mi), sec), zone[0],
                ss == null ? Precision.MINUTE : Precision.SECOND);
    }
}
