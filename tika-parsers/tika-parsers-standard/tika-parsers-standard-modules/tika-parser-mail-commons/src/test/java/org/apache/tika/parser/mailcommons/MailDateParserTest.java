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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class MailDateParserTest {

    @Test
    public void testDateTimesWithTimeZones() throws Exception {
        String expected = "2016-05-09T01:32:00Z";

        //try with timezones
        for (String dateString : new String[] {
                // with timezone info:
                "Thu, 9 May 16 01:32:00 GMT",
                "Thu, 9 May 2016 01:32:00 UTC",
                "Thu, 9 May 2016 01:32:00Z",
                "Thu, 9 May 2016 01:32:00 GMT",
                "Thu, 9 May 2016 01:32:00 UTC",
                //try with leading space
                "      Thu, 9 May 2016 3:32:00 +0200",
                "Thu, 9 May 2016 3:32:00 +02:00",
                // format correctly handled by mime4j if no leading whitespace
                "      Wed, 8 May 2016 20:32:00 EST",}) {
            testDate(dateString, expected, true);
        }
    }

    @Test
    public void testDateTimesWithNoTimeZone() throws Exception {
        String expected = "2016-05-09T01:32:00Z";

        for (String dateString : new String[]{
                /*  "Thu, 9 May 2016 01:32:00",
                  "Thursday, May 9 2016 1:32 AM", "May 9 2016 1:32am", "May 9 2016 1:32 am",
                  "2016-05-09 01:32:00"*/}) {
            testDate(dateString, expected, true);
        }
    }

    @Test
    public void testDates() throws Exception {
        //now try days without times
        String expected = "2016-05-15T12:00:00Z";
        for (String dateString : new String[]{"May 15, 2016", "Sun, 15 May 2016", "15 May 2016",}) {
            testDate(dateString, expected, false);

        }
    }

    @Test
    public void testTrickyDates() throws Exception {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", new DateFormatSymbols(Locale.US));
        //make sure there are no mis-parses of e.g. 90 = year 90 A.D, not 1990
        Date date1980 = df.parse("1980-01-01");
        Date date2010 = df.parse("2010-01-01");
        for (String dateString : new String[]{
                "11/14/08",
                "1/14/08",
                "1/2/08",
                "12/1/2008",
                "12/02/1996",
                "96/1/02",
                "96/12/02",
                "96/12/2",
                "1996/12/02",
                "Mon, 29 Jan 96 14:02 GMT",
                "7/20/95 1:12PM",
                "08/14/2000  12:48 AM",
                "8/4/2000  1:48 AM",
                "06/24/2008, Tuesday, 11 AM",
                }) {
            Date parsedDate = MailDateParser.parseDateLenient(dateString);
            System.out.println(parsedDate);
            assertNotNull(parsedDate);
            if (parsedDate != null) {
                assertTrue(parsedDate.getTime() > date1980.getTime(),
                        "date must be after 1980:" + dateString + " >> + " +
                                parsedDate);
                assertTrue(parsedDate.getTime() < date2010.getTime(),
                        "date must be before 2020: " + dateString + " >> + " +
                                parsedDate);
            }
        }
        //TODO: mime4j misparses these to pre 1980 dates
        //"Wed, 27 Dec 95 11:20:40 EST",
        //"26 Aug 00 11:14:52 EDT"
        //
        //We are still misparsing: 8/1/03 to a pre 1980 date

    }

    private void testDate(String dateString, String expected, boolean useUTC) throws Exception {
        Date parsedDate = MailDateParser.parseDateLenient(dateString);
        assertNotNull(parsedDate, "couldn't parse " + dateString);
        DateFormat df =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", new DateFormatSymbols(Locale.US));
        if (useUTC) {
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        String parsedDateString = df.format(parsedDate);
        assertEquals(expected, parsedDateString, "failed to match: " + dateString);
    }

}
