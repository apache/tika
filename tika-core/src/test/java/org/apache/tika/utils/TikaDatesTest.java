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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cases come from the TIKA-4917 corpus scrapes (XMP packets, mail headers). Runs in a +14h default
 * zone so any dependence on the JVM zone fails.
 */
@Isolated
@ResourceLock(Resources.TIME_ZONE)
public class TikaDatesTest {

    private static TimeZone originalTimeZone;

    @BeforeAll
    static void init() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
    }

    @AfterAll
    static void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // ISO / W3C-DTF (the bulk of XMP)
            "2006-07-15T00:01:46+01:00         | 2006-07-14T23:01:46Z",
            "2004-08-09T14:03:21-05:00         | 2004-08-09T19:03:21Z",
            "2011-01-01T12:41:36Z              | 2011-01-01T12:41:36Z",
            "2007-01-01T00:07+01:00            | 2006-12-31T23:07:00Z",
            "2010-03-08T21:37-05:00            | 2010-03-09T02:37:00Z",
            "2016-08-26T07:06Z                 | 2016-08-26T07:06:00Z",
            "2010-01-10T13:00:19               | 2010-01-10T13:00:19Z",
            "2013-09-08T04:14:06.006           | 2013-09-08T04:14:06Z",
            "2018-08-06T20:53:57.9843081+01:00 | 2018-08-06T19:53:57Z",
            "2011-12-17T21:47:07.88+09:00      | 2011-12-17T12:47:07Z",
            "2007-02-21T09:10:07.0+2:00        | 2007-02-21T07:10:07Z",
            "2018-03-15T10:18:57+1:00          | 2018-03-15T09:18:57Z",
            "2011-12-31T15:37:24.00986+03:30   | 2011-12-31T12:07:24Z",
            "2010-05-09T21:34:38+0200          | 2010-05-09T19:34:38Z",
            "2003-01-01 12:00:00 +0100         | 2003-01-01T11:00:00Z",
            "2010-04-22 15:20 -0400            | 2010-04-22T19:20:00Z",
            "2015-08-26T09:39                  | 2015-08-26T09:39:00Z",
            // date-only -> midday UTC
            "2005-11-03                        | 2005-11-03T12:00:00Z",
            "2017-06-1                         | 2017-06-01T12:00:00Z",
            "1969-7-20                         | 1969-07-20T12:00:00Z",
            // PDF D: and compact
            "D:20041028184621                  | 2004-10-28T18:46:21Z",
            "D:20080528153425+02'00'           | 2008-05-28T13:34:25Z",
            "D:20030101120000+05'30'           | 2003-01-01T06:30:00Z",
            "D:20030101120000+05'30            | 2003-01-01T06:30:00Z",
            "D:20030101120000-08'00'           | 2003-01-01T20:00:00Z",
            "D:20030101120000Z                 | 2003-01-01T12:00:00Z",
            "D:20030101120000Z00'00'           | 2003-01-01T12:00:00Z",
            "D:200301011200                    | 2003-01-01T12:00:00Z",
            "D:20041109                        | 2004-11-09T12:00:00Z",
            "\"  D:20030101  \"                  | 2003-01-01T12:00:00Z",
            "20030101120000                    | 2003-01-01T12:00:00Z",
            // EXIF
            "2014:08:21 13:33:53               | 2014-08-21T13:33:53Z",
            "2015:06:12                        | 2015-06-12T12:00:00Z",
            "2017:12:23 12:51:20Z              | 2017-12-23T12:51:20Z",
            // RFC 5322 (mail)
            "\"Fri, 01 Dec 2006 00:06:00 GMT\"   | 2006-12-01T00:06:00Z",
            "\"Fri, 03 Dec 1999 15:17:26 -0500 (EST)\" | 1999-12-03T20:17:26Z",
            "01 Aug 2001 11:14:22 -0400        | 2001-08-01T15:14:22Z",
            "1 Apr 1999 11:40:10 -0500         | 1999-04-01T16:40:10Z",
            "\"Fri, 16 Apr 93 08:25:04 -0400\"   | 1993-04-16T12:25:04Z",
            "\"Fri, 4 Sep 98 00:24:11 -0600\"    | 1998-09-04T06:24:11Z",
            "\"Fri, 01 Apr 1994 16:57 -0800 (PST)\" | 1994-04-02T00:57:00Z",
            "\"Fri, 01 Dec 2000 08:39:07\"       | 2000-12-01T08:39:07Z",
            "\"Fri, 18 Jun 1993 9:42:24 -0400 (EDT)\" | 1993-06-18T13:42:24Z",
            "10 Jul 1996 13:23 EDT             | 1996-07-10T17:23:00Z",
            "\"Fri, 1 Feb 91 18:33:59 PST\"      | 1991-02-02T02:33:59Z",
            "\"Thu,  6 May 2004 10:12:09 +0200 (CEST)\" | 2004-05-06T08:12:09Z",
            "\"Fri, 12 Apr 2002 18:56:49 -0400 (Eastern Daylight Time)\" | 2002-04-12T22:56:49Z",
            "\"Fri, 19 Oct 2007 10:23:52 -0600 (GMT-06:00)\" | 2007-10-19T16:23:52Z",
            "\"Fri, 16 Jan 2004 10:45:33 0000\"  | 2004-01-16T10:45:33Z",
            "15 Nov 94 09:44:30 -5             | 1994-11-15T14:44:30Z",
            "\"Thursday, 18 December 1997 11:31pm\" | 1997-12-18T23:31:00Z",
            "29 Jan 2014                       | 2014-01-29T12:00:00Z",
            "9 November 2001                   | 2001-11-09T12:00:00Z",
            "\"3 March, 2017\"                   | 2017-03-03T12:00:00Z",
            // US month-name forms
            "\"Friday, April 02, 1999 1:20:07 PM\" | 1999-04-02T13:20:07Z",
            "\"Fri, Nov 15, 1996 6:58 AM\"       | 1996-11-15T06:58:00Z",
            "\"Friday, December 12, 2008, 11:28 AM\" | 2008-12-12T11:28:00Z",
            "\"Fri, May 11, 2012 at 6:06 PM\"    | 2012-05-11T18:06:00Z",
            "\"Monday, August 12, 1996 5:13PM\"  | 1996-08-12T17:13:00Z",
            "\"Fri, Mar 9, 2012 20:23\"          | 2012-03-09T20:23:00Z",
            "\"Monday, October 29, 2001 10:13\"  | 2001-10-29T10:13:00Z",
            "\"Friday, June 2, 2000   3:45 PM PDT\" | 2000-06-02T22:45:00Z",
            "\"Friday, February 19 2010 13:31:33 CET\" | 2010-02-19T12:31:33Z",
            "\"August 12, 1999\"                 | 1999-08-12T12:00:00Z",
            "\"April 5, 1989\"                   | 1989-04-05T12:00:00Z",
            "Aug 13 2008                       | 2008-08-13T12:00:00Z",
            "Fri Jan 18  2008                  | 2008-01-18T12:00:00Z",
            "Wed May 23 2018 17:52:58 GMT+0200 | 2018-05-23T15:52:58Z",
            // ctime
            "Fri Apr 10 04:00:33 PDT 1992      | 1992-04-10T11:00:33Z",
            "Fri Apr  2 04:00:22 PST 1999      | 1999-04-02T12:00:22Z",
            "Fri Aug 01 10:21:01 2014 -0700    | 2014-08-01T17:21:01Z",
            "Fri Apr 20 13:38:50 2007          | 2007-04-20T13:38:50Z",
            "THU JAN 01 22:51:33 1970          | 1970-01-01T22:51:33Z",
            // slash: month-first, day-first when the first field > 12
            "5/12/2008                         | 2008-05-12T12:00:00Z",
            "12/31/2008 13:14:15               | 2008-12-31T13:14:15Z",
            "23/12/2009 16:52:18               | 2009-12-23T16:52:18Z",
            "13/09/2016                        | 2016-09-13T12:00:00Z",
            "2/22/2016 4:00:00 PM              | 2016-02-22T16:00:00Z",
            "\"09/09/2018, 20:47\"               | 2018-09-09T20:47:00Z",
            "08/14/18                          | 2018-08-14T12:00:00Z",
            "5/4/05                            | 2005-05-04T12:00:00Z",
            "17/4/13 14:10                     | 2013-04-17T14:10:00Z",
            "2016/01/15 14:27:04               | 2016-01-15T14:27:04Z",
            "2009/8/26                         | 2009-08-26T12:00:00Z",
            // producer quirks recovered (XMP / mail scrape losses)
            "2017-11-20T13:5836                | 2017-11-20T13:58:36Z",
            "2018-09-13T18.20.16Z              | 2018-09-13T18:20:16Z",
            "2006-06-19T12:37:3Z               | 2006-06-19T12:37:03Z",
            "2012-08-02T19:22:29:93            | 2012-08-02T19:22:29Z",
            "2008-01-14T19:43:09Z00:00         | 2008-01-14T19:43:09Z",
            "2016-01-14T11:27:45-6':00         | 2016-01-14T17:27:45Z",
            "2017-12-31 07:09:59 PM            | 2017-12-31T19:09:59Z",
            "D:2018-08-02T16:06:22+02'00'      | 2018-08-02T14:06:22Z",
            // PDF quirks PDFBox accepts in docinfo (full-probe losses)
            "D:20160308221458Z'                | 2016-03-08T22:14:58Z",
            "D:20210712135012+02'00''          | 2021-07-12T11:50:12Z",
            "D:20190221101345-6'00'            | 2019-02-21T16:13:45Z",
            "8/11/2008 14:8:23                 | 2008-08-11T14:08:23Z",
            "\"Wednesday, 16/02/2011\"         | 2011-02-16T12:00:00Z",
            "2006-10-18 16:01:00 -0700 (Wed, 18 Oct 2006) | 2006-10-18T23:01:00Z",
            "\"Mon, 29 Oct 2007 20:20:52 -0500From: someone\" | 2007-10-30T01:20:52Z",
            "\"Thu, 27 Aug 1998 08:39:32 +0200 <br>\" | 1998-08-27T06:39:32Z",
            "\"Sat, 19 Jun 93 16:34:27 JST\"   | 1993-06-19T07:34:27Z",
            "\"Wed, 16 Aug 2000 4:19:1 GMT\"   | 2000-08-16T04:19:01Z",
            // ambiguous zone name (BST: British or Bangladesh) is unknown -> zone-less, not rejected
            "15 Oct 99 13:47:26 BST            | 1999-10-15T13:47:26Z",
            "2010-07-28T11:02:12.000CEST       | 2010-07-28T09:02:12Z",
            "7-JUL-1994 02:36:51.00            | 1994-07-07T02:36:51Z",
            "\"Tuesday, 04-Jul-95 06:04:28 GMT\" | 1995-07-04T06:04:28Z",
            "\"April 18th, 2000\"              | 2000-04-18T12:00:00Z",
            "\"Nov. 21, 1998\"                 | 1998-11-21T12:00:00Z",
    })
    public void testNormalize(String raw, String expected) {
        assertEquals(expected, TikaDates.normalize(raw));
    }

    /** Rejected, not guessed: every one of these was silently misparsed by an existing parser. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "0-01-01T00:00:00Z",          // year 0 -> -0001-12-30 (DateConverter)
            "0-00-00T00:00:00Z",          // -> -0001-11-28 (DateUtils)
            "0000-01-01T00:00:00Z",
            "0-00-00",
            "9999-12-31",                 // sentinel, above MAX_YEAR
            "9999/12/31",
            "10000-01-01T00:59:59+01:00",
            "406-04-16T07:15:00Z",
            "2/23/201",                   // 3-digit year -> 0201 (DateConverter)
            "00:15:54",                   // time only -> year 1 (DateUtils)
            "2:16:58",
            "2014-07-18T05:44:470Z",      // overflow -> 05:51:50 (DateConverter)
            "2015:06:12 10:11:",
            "2016-13-01",
            "2016-02-30",
            "23/13/2009",                 // both fields > 12
            "18/02/98 25:00",
            "11658090",
            "137912",
            "1057298",
            "0",
            "+02:00",
            "()",
            "CPY Document Creation Date",
            "\"Mon, 7 Jan 2008 08:47:01 0100 (CET)\"",  // unsigned non-zero offset: ambiguous
            "10 Jun 1998 09:07:34 U",
            "December 15, 2005 Report No. LOXA05-004",
            "2015-09-28T04:47:26-16:00",  // offsets outside -12..+14
            "2017-09-12T02:10:54-21:00",
            "2015-09-28T17:25:50+17:00",
            "2017-05-11T19:05:84",        // second 84 -> rolled to 19:06:24 (DateConverter)
            "D:20080702212348+20'00'",    // offset out of range
            "D:2012010510394209'00'",     // 16 digits, unsigned offset: ambiguous
            "D:191010111144159",
            "D:20170112095532--4'00'",
    })
    public void testRejected(String raw) {
        assertNull(TikaDates.normalize(raw), raw);
    }

    @Test
    public void testKeepPartial() {
        assertEquals("2018", TikaDates.toMetadataStringKeepPartial("2018"));
        assertEquals("2018-05", TikaDates.toMetadataStringKeepPartial("2018-05"));
        assertEquals("2018-05-01", TikaDates.toMetadataStringKeepPartial("2018-05-01"));
        for (String junk : new String[]{"", "   ", "0", "18", "0312", "2018 2018", "[www.example.com]"}) {
            assertNull(TikaDates.toMetadataStringKeepPartial(junk), junk);
        }
    }

    @Test
    public void testInYearBounds() {
        assertTrue(TikaDates.inYearBounds(java.time.Instant.parse("2012-02-20T16:44:22Z")));
        assertFalse(TikaDates.inYearBounds(java.time.Instant.parse("0004-12-31T23:00:00Z")));
        assertFalse(TikaDates.inYearBounds(java.time.Instant.parse("+31135-02-07T00:18:29Z")));
        assertFalse(TikaDates.inYearBounds(null));
    }

    @Test
    public void testEmptyAndNull() {
        assertNull(TikaDates.normalize(null));
        assertNull(TikaDates.normalize(""));
        assertNull(TikaDates.normalize("   "));
        assertFalse(TikaDates.parse(null).isPresent());
    }

    /** Partial dates parse (so callers can see them) but never normalize: no fabricated precision. */
    @Test
    public void testPartialDates() {
        assertEquals(TikaDates.Precision.YEAR, TikaDates.parse("2019").get().getPrecision());
        assertEquals(TikaDates.Precision.MONTH, TikaDates.parse("2019-06").get().getPrecision());
        assertEquals(TikaDates.Precision.YEAR, TikaDates.parse("D:2003").get().getPrecision());
        assertFalse(TikaDates.parse("2019").get().isFullPrecision());
        assertNull(TikaDates.normalize("2019"));
        assertNull(TikaDates.normalize("2019-06"));
        assertNull(TikaDates.normalize("D:2003"));
    }

    @Test
    public void testPrecisionAndZone() {
        TikaDates.ParsedDate d = TikaDates.parse("2010-01-10T13:00:19").get();
        assertEquals(TikaDates.Precision.SECOND, d.getPrecision());
        assertFalse(d.hasZone());
        assertTrue(TikaDates.parse("2010-01-10T13:00:19Z").get().hasZone());
        assertEquals(TikaDates.Precision.MINUTE, TikaDates.parse("2016-08-26T07:06Z").get().getPrecision());
        assertEquals(TikaDates.Precision.DAY, TikaDates.parse("2005-11-03").get().getPrecision());
        assertTrue(TikaDates.parse("2005-11-03").get().isFullPrecision());
    }

    /** Everything normalize() writes must read back to itself (Metadata.getDate() round trip). */
    @Test
    public void testRoundTrip() {
        for (String s : new String[]{"2006-07-15T00:01:46+01:00", "D:20080528153425+02'00'",
                "Fri, 01 Dec 2006 00:06:00 GMT", "2005-11-03", "2014:08:21 13:33:53"}) {
            String n = TikaDates.normalize(s);
            assertEquals(n, TikaDates.normalize(n), s);
        }
    }

    /** Storage form: a zone only when the source had one; date-only stays a date. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "2006-07-15T00:01:46+01:00         | 2006-07-14T23:01:46Z",
            "2016-08-26T07:06Z                 | 2016-08-26T07:06:00Z",
            "2010-01-10T13:00:19               | 2010-01-10T13:00:19",
            "2013-09-08T04:14:06.006           | 2013-09-08T04:14:06",
            "2015-08-26T09:39                  | 2015-08-26T09:39:00",
            "D:20041028184621                  | 2004-10-28T18:46:21",
            "D:20080528153425+02'00'           | 2008-05-28T13:34:25Z",
            "2014:08:21 13:33:53               | 2014-08-21T13:33:53",
            "\"Fri, 01 Dec 2000 08:39:07\"   | 2000-12-01T08:39:07",
            "\"Fri, 01 Dec 2006 00:06:00 GMT\" | 2006-12-01T00:06:00Z",
            "2005-11-03                        | 2005-11-03",
            "2015:06:12                        | 2015-06-12",
            "D:20041109                        | 2004-11-09",
            "\"August 12, 1999\"             | 1999-08-12",
    })
    public void testToMetadataString(String raw, String expected) {
        assertEquals(expected, TikaDates.toMetadataString(raw));
    }

    /** What we store must re-parse to the same local time, zone-or-not and instant; idempotent. */
    @Test
    public void testMetadataStringRoundTrip() {
        for (String s : new String[]{"2006-07-15T00:01:46+01:00", "2010-01-10T13:00:19", "2015-08-26T09:39",
                "D:20041028184621", "2014:08:21 13:33:53", "2005-11-03", "Fri, 01 Dec 2006 00:06:00 GMT"}) {
            TikaDates.ParsedDate a = TikaDates.parse(s).get();
            String stored = a.toMetadataString();
            TikaDates.ParsedDate b = TikaDates.parse(stored).get();
            assertEquals(a.toInstant(), b.toInstant(), s);
            assertEquals(a.hasZone(), b.hasZone(), s);
            assertEquals(a.getPrecision() == TikaDates.Precision.DAY, b.getPrecision() == TikaDates.Precision.DAY, s);
            if (!a.hasZone()) {
                assertEquals(a.getLocalDateTime(), b.getLocalDateTime(), s);
            }
            assertEquals(stored, TikaDates.toMetadataString(stored), s);
        }
    }

    @Test
    public void testMetadataStringPartialAndGarbage() {
        assertNull(TikaDates.toMetadataString("2019"));
        assertNull(TikaDates.toMetadataString("2019-06"));
        assertNull(TikaDates.toMetadataString("0-01-01T00:00:00Z"));
        assertNull(TikaDates.toMetadataString(null));
    }
}
