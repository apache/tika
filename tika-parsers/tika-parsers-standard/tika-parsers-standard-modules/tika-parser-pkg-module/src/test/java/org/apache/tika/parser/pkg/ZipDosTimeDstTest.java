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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;

/**
 * A DOS time inside the host's DST spring-forward gap: commons-compress resolves it in the JVM
 * zone and moves 02:30 to 03:30; the zip path reads the raw header field instead.
 */
@Isolated
@ResourceLock(Resources.TIME_ZONE)
// commons-compress ZipUtil.dosToJavaDate uses Calendar.getInstance() (TIKA-4920)
@DisabledIf("org.apache.tika.test.TestLocales#nonGregorianDefault")
public class ZipDosTimeDstTest extends TikaTest {

    private static TimeZone saved;

    @BeforeAll
    public static void berlin() {
        saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    }

    @AfterAll
    public static void restore() {
        TimeZone.setDefault(saved);
    }

    @Test
    public void testDosTimeInDstGap() throws Exception {
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(zipAt(2021, 3, 28, 2, 30, 10)),
                new ZipParser(), false);
        assertEquals("2021-03-28T02:30:10", list.get(1).get(FileSystem.MODIFIED));
    }

    @Test
    public void testDosTimeOutsideGap() throws Exception {
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(zipAt(2021, 7, 1, 9, 15, 0)),
                new ZipParser(), false);
        assertEquals("2021-07-01T09:15:00", list.get(1).get(FileSystem.MODIFIED));
    }

    // java.util.zip writes the DOS fields; patch both headers with the exact wall clock
    private static byte[] zipAt(int year, int month, int day, int hour, int min, int sec) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("a.txt"));
            zos.write("hello".getBytes(StandardCharsets.US_ASCII));
            zos.closeEntry();
        }
        byte[] b = bos.toByteArray();
        int time = (hour << 11) | (min << 5) | (sec / 2);
        int date = ((year - 1980) << 9) | (month << 5) | day;
        patch(b, 10, time, date);
        for (int i = 0; i + 4 < b.length; i++) {
            if (b[i] == 0x50 && b[i + 1] == 0x4b && b[i + 2] == 1 && b[i + 3] == 2) {
                patch(b, i + 12, time, date);
            }
        }
        return b;
    }

    private static void patch(byte[] b, int off, int time, int date) {
        b[off] = (byte) time;
        b[off + 1] = (byte) (time >> 8);
        b[off + 2] = (byte) date;
        b[off + 3] = (byte) (date >> 8);
    }
}
