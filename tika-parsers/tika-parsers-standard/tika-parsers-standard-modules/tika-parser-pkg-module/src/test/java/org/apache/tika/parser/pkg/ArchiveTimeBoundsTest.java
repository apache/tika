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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Date;

import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;

/**
 * Archive header times are binary fields; a junk value (e.g. a garbage FILETIME) must not
 * reach fs:* as an impossible year that Metadata.getDate() cannot read back.
 */
public class ArchiveTimeBoundsTest {

    private static final Date YEAR_30828 = Date.from(Instant.parse("+30828-09-14T02:48:05Z"));
    private static final Date YEAR_0999 = Date.from(Instant.parse("0999-01-01T00:00:00Z"));
    private static final Date VALID = Date.from(Instant.parse("2020-01-02T03:04:05Z"));

    @Test
    public void testInstantsOutOfBoundsDropped() {
        Metadata m = new Metadata();
        AbstractArchiveParser.setInstant(m, FileSystem.CREATED, YEAR_30828);
        AbstractArchiveParser.setInstant(m, FileSystem.ACCESSED, YEAR_0999);
        AbstractArchiveParser.setInstant(m, FileSystem.MODIFIED, VALID);
        assertNull(m.get(FileSystem.CREATED));
        assertNull(m.get(FileSystem.ACCESSED));
        assertEquals("2020-01-02T03:04:05Z", m.get(FileSystem.MODIFIED));
    }

    @Test
    public void testLocalTimeOutOfBoundsDropped() {
        Metadata m = new Metadata();
        AbstractArchiveParser.setLocalTime(m, FileSystem.CREATED, YEAR_30828);
        assertNull(m.get(FileSystem.CREATED));
    }

    // X5455 is 32-bit Unix time and cannot hold a junk year; the NTFS FILETIME field can
    @Test
    public void testZipNtfsOutOfBoundsDropped() {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setModifyJavaTime(VALID);
        ntfs.setAccessJavaTime(Date.from(Instant.parse("2500-01-01T00:00:00Z")));
        ntfs.setCreateJavaTime(YEAR_0999);
        entry.addExtraField(ntfs);
        Metadata m = new Metadata();
        ZipParser.setEntryTimes(entry, m);
        assertEquals("2020-01-02T03:04:05Z", m.get(FileSystem.MODIFIED));
        assertNull(m.get(FileSystem.ACCESSED));
        assertNull(m.get(FileSystem.CREATED));
    }
}
