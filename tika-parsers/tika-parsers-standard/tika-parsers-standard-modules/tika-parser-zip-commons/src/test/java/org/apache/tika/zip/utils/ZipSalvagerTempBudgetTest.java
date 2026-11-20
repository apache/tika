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
package org.apache.tika.zip.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class ZipSalvagerTempBudgetTest {

    private static final int MB = 1024 * 1024;

    // 3 MB that does not compress, with the central directory cut off: only salvaging opens it
    private static byte[] truncated;

    @BeforeAll
    static void makeTruncatedZip() throws Exception {
        byte[] noise = new byte[MB];
        new Random(5075).nextBytes(noise);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            for (int i = 0; i < 3; i++) {
                zip.putNextEntry(new ZipEntry("noise" + i + ".bin"));
                zip.write(noise);
                zip.closeEntry();
            }
        }
        byte[] whole = bos.toByteArray();
        truncated = Arrays.copyOf(whole, whole.length - 200);
    }

    @Test
    public void testSalvagesWithinBudget() throws Exception {
        TempBytesBudget budget = new TempBytesBudget(10 * MB);
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            tis.setTempBudget(budget);
            assertNotNull(ZipSalvager.tryToOpenZipFile(tis, new Metadata()));
            assertTrue(budget.getReservedBytes() >= 3 * MB, "salvaged zip not charged");
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testSalvageStopsAtBudget() throws Exception {
        TempBytesBudget budget = new TempBytesBudget(MB);
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            tis.setTempBudget(budget);
            assertNull(ZipSalvager.tryToOpenZipFile(tis, new Metadata()));
            // the abandoned copy is deleted and no longer counted
            assertEquals(0, budget.getReservedBytes());
            assertTrue(budget.isLimitReached());
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testSalvageCopyReportsTheLimit(@TempDir Path dir) throws Exception {
        TempBytesBudget budget = new TempBytesBudget(MB);
        Path salvaged = dir.resolve("salvaged.zip");
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            tis.setTempBudget(budget);
            tis.enableRewind();
            assertThrows(TempLimitReachedException.class,
                    () -> ZipSalvager.salvageCopy(tis, salvaged, false));
            assertTrue(Files.size(salvaged) <= MB);
        }
        assertEquals(0, budget.getReservedBytes());
    }
}
