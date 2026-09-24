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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@Isolated
@ResourceLock(Resources.TIME_ZONE)
public class MetadataZonelessDateTest {

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

    @Test
    public void testGetDateIgnoresJvmZone() {
        assertEquals(Instant.parse("2010-01-10T13:00:19Z"), getDate("2010-01-10T13:00:19"));
        assertEquals(Instant.parse("2010-01-10T12:00:00Z"), getDate("2010-01-10"));
        assertEquals(Instant.parse("2010-01-10T13:00:19Z"), getDate("2010-01-10T13:00:19Z"));
        assertEquals(Instant.parse("2010-01-10T12:00:19Z"), getDate("2010-01-10T13:00:19+01:00"));
    }

    private static Instant getDate(String stored) {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.CREATED, stored);
        return m.getDate(TikaCoreProperties.CREATED).toInstant();
    }

    @Test
    public void testGarbageIsNull() {
        Metadata m = new Metadata();
        for (String garbage : new String[]{"0-00-00T00:00:00Z", "0000-00-00", "2019", "INVALID"}) {
            m.set(TikaCoreProperties.CREATED, garbage);
            assertNull(m.getDate(TikaCoreProperties.CREATED), garbage);
            assertNull(new org.apache.tika.utils.DateUtils().tryToParse(garbage), garbage);
        }
    }
}
