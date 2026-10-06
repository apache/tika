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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/** Runs in a +14h zone. */
@Isolated
@ResourceLock(Resources.TIME_ZONE)
public class ZipEntryTimesTest extends TikaTest {

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
    public void testEntryTimesAreFileSystemTimes(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("times.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            ZipArchiveEntry dos = new ZipArchiveEntry("dos.txt");
            dos.setTime(LocalDateTime.of(2009, 8, 11, 9, 9, 44)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            zos.putArchiveEntry(dos);
            zos.write("dos".getBytes(StandardCharsets.US_ASCII));
            zos.closeArchiveEntry();

            ZipArchiveEntry ext = new ZipArchiveEntry("ext.txt");
            X5455_ExtendedTimestamp ts = new X5455_ExtendedTimestamp();
            ts.setModifyJavaTime(Date.from(Instant.parse("2012-02-20T16:44:22Z")));
            ext.addExtraField(ts);
            zos.putArchiveEntry(ext);
            zos.write("ext".getBytes(StandardCharsets.US_ASCII));
            zos.closeArchiveEntry();
        }
        List<Metadata> list = getRecursiveMetadata(zip, new ParseContext(), false);
        Metadata dosMd = entry(list, "dos.txt");
        Metadata extMd = entry(list, "ext.txt");
        assertEquals("2009-08-11T09:09:44", dosMd.get(FileSystem.MODIFIED));
        assertEquals("2012-02-20T16:44:22Z", extMd.get(FileSystem.MODIFIED));
        for (Metadata m : List.of(dosMd, extMd)) {
            assertNull(m.get(TikaCoreProperties.CREATED));
            assertNull(m.get(TikaCoreProperties.MODIFIED));
        }
    }

    private static Metadata entry(List<Metadata> list, String name) {
        return list.stream().filter(m -> name.equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY)))
                .findFirst().orElseThrow();
    }
}
