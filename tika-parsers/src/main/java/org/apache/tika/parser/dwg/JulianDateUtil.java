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

package org.apache.tika.parser.dwg;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

class JulianDateUtil {
    private static final double NANOS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000000000.0;
    public static final Instant REDUCED_JD =
            ZonedDateTime.of(1858, 11, 16, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
    public static final Instant JULIAN_DATE =
            REDUCED_JD.minus(2400000, ChronoUnit.DAYS);

    private final Instant epoch;

    private JulianDateUtil(Instant epoch) {
        super();
        this.epoch = epoch;
    }

    private Instant toInstant(double day) {
        long l = (long) day;
        return epoch
                .plus(l, ChronoUnit.DAYS)
                .plusNanos(Math.round((day - l) * NANOS_PER_DAY));
    }

    public static Instant toInstant(int julianDay, int millisecondsIntoDay) {
        return new JulianDateUtil(JulianDateUtil.JULIAN_DATE).toInstant(Double.parseDouble(julianDay + "." + millisecondsIntoDay));
    }
}