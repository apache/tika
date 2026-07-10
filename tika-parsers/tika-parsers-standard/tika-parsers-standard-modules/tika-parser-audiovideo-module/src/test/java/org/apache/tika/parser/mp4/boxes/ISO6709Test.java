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
package org.apache.tika.parser.mp4.boxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ISO6709Test {

    private static final double DELTA = 0.00001;

    @Test
    public void testNullAndEmpty() {
        assertNull(ISO6709.parse(null));
        assertNull(ISO6709.parse(""));
        assertNull(ISO6709.parse("not a location"));
    }

    @Test
    public void testDecimalDegrees() {
        //the value carried by the TIKA-2861 test fixtures; must stay unchanged
        ISO6709.Location loc = ISO6709.parse("+12.3456-098.7654+010.500/");
        assertEquals(12.3456, loc.latitude, DELTA);
        assertEquals(-98.7654, loc.longitude, DELTA);
        assertEquals(10.5, loc.altitude, DELTA);
    }

    @Test
    public void testDecimalNoAltitude() {
        ISO6709.Location loc = ISO6709.parse("+12.34-098.76/");
        assertEquals(12.34, loc.latitude, DELTA);
        assertEquals(-98.76, loc.longitude, DELTA);
        assertNull(loc.altitude);
    }

    @Test
    public void testDecimalWithAltitudeAndCrsSuffix() {
        //Everest-like: large altitude followed by a CRS designator before the terminator
        ISO6709.Location loc = ISO6709.parse("+27.5916+086.5640+8850.000CRSWGS_84/");
        assertEquals(27.5916, loc.latitude, DELTA);
        assertEquals(86.5640, loc.longitude, DELTA);
        assertEquals(8850.0, loc.altitude, DELTA);
    }

    @Test
    public void testIntegerDegreesNoDecimalPoint() {
        ISO6709.Location loc = ISO6709.parse("+12-098/");
        assertEquals(12.0, loc.latitude, DELTA);
        assertEquals(-98.0, loc.longitude, DELTA);
    }

    @Test
    public void testNegativeSubDegreeWithLeadingZeros() {
        ISO6709.Location loc = ISO6709.parse("-00.5000-000.5000/");
        assertEquals(-0.5, loc.latitude, DELTA);
        assertEquals(-0.5, loc.longitude, DELTA);
    }

    @Test
    public void testCompactDegreesMinutes() {
        //+DDMM.MMMM / -DDDMM.MMMM -> degrees + minutes/60
        ISO6709.Location loc = ISO6709.parse("+1234.5600-09830.0000/");
        assertEquals(12 + 34.56 / 60.0, loc.latitude, DELTA);
        assertEquals(-(98 + 30.0 / 60.0), loc.longitude, DELTA);
        assertNull(loc.altitude);
    }

    @Test
    public void testCompactDegreesMinutesSeconds() {
        //+DDMMSS.S / -DDDMMSS.S -> degrees + minutes/60 + seconds/3600
        ISO6709.Location loc = ISO6709.parse("+123456.0-0983456.0/");
        assertEquals(12 + 34.0 / 60.0 + 56.0 / 3600.0, loc.latitude, DELTA);
        assertEquals(-(98 + 34.0 / 60.0 + 56.0 / 3600.0), loc.longitude, DELTA);
    }
}
