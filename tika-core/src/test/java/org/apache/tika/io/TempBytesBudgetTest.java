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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.io.function.IOSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tika.config.TempLimits;
import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class TempBytesBudgetTest {

    private static final int MB = 1024 * 1024;

    // one per source that spools: stream cache, byte array, re-openable
    static Stream<Arguments> sources() {
        Function<byte[], TikaInputStream> stream =
                b -> TikaInputStream.get(new ByteArrayInputStream(b));
        Function<byte[], TikaInputStream> array = TikaInputStream::get;
        Function<byte[], TikaInputStream> reopenable = b -> TikaInputStream.get(
                (IOSupplier<InputStream>) () -> new ByteArrayInputStream(b),
                new TemporaryResources(), new Metadata());
        return Stream.of(Arguments.of("stream", stream), Arguments.of("array", array),
                Arguments.of("reopenable", reopenable));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    public void testSpoolPastBudgetFails(String name, Function<byte[], TikaInputStream> open)
            throws Exception {
        TempBytesBudget budget = new TempBytesBudget(MB);
        try (TikaInputStream tis = open.apply(new byte[3 * MB])) {
            tis.setTempBudget(budget);
            assertThrows(TempLimitReachedException.class, tis::getPath);
            assertTrue(budget.getReservedBytes() <= MB);
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    public void testClosingGivesTheBytesBack(String name, Function<byte[], TikaInputStream> open)
            throws Exception {
        TempBytesBudget budget = new TempBytesBudget(3 * MB);
        // more in total than the budget, never more than it at once
        for (int i = 0; i < 4; i++) {
            Path path;
            try (TikaInputStream tis = open.apply(new byte[2 * MB])) {
                tis.setTempBudget(budget);
                path = tis.getPath();
                assertEquals(2 * MB, Files.size(path));
                assertEquals(2 * MB, budget.getReservedBytes());
            }
            assertFalse(Files.exists(path));
            assertEquals(0, budget.getReservedBytes());
        }
    }

    @Test
    public void testStreamsShareOneBudget() throws Exception {
        TempBytesBudget budget = new TempBytesBudget(3 * MB);
        try (TikaInputStream first = TikaInputStream.get(new ByteArrayInputStream(new byte[2 * MB]));
                TikaInputStream second =
                        TikaInputStream.get(new ByteArrayInputStream(new byte[2 * MB]))) {
            first.setTempBudget(budget);
            second.setTempBudget(budget);
            first.getPath();
            assertThrows(TempLimitReachedException.class, second::getPath);
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testApplyUsesTheContextsBudget() throws IOException {
        ParseContext context = new ParseContext();
        context.set(TempBytesBudget.class, new TempLimits(MB).newBudget());
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[3 * MB]))) {
            TempLimits.apply(tis, context);
            assertThrows(TempLimitReachedException.class, tis::getPath);
        }
    }

    @Test
    public void testNoBudgetNoLimit() throws IOException {
        // limits alone do nothing: only a budget in the context is enforced
        ParseContext context = new ParseContext();
        context.set(TempLimits.class, new TempLimits(MB));
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[3 * MB]))) {
            TempLimits.apply(tis, context);
            assertEquals(3 * MB, Files.size(tis.getPath()));
        }
        assertNull(new TempLimits(-1).newBudget());
    }

    @Test
    public void testChargeFilesSomethingElseWrote(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("a"), new byte[MB]);
        Files.write(Files.createDirectory(dir.resolve("sub")).resolve("b"), new byte[MB]);
        TempBytesBudget budget = new TempBytesBudget(2 * MB + MB / 2);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTempBudget(budget);
            tmp.chargeTempFile(dir);
            assertEquals(2 * MB, budget.getReservedBytes());
            // measured again, not added again
            tmp.chargeTempFile(dir);
            assertEquals(2 * MB, budget.getReservedBytes());
            assertThrows(TempLimitReachedException.class,
                    () -> tmp.chargeTempFile(dir.resolve("sub")));
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testFailedSpoolIsFinal() throws Exception {
        // all in memory until getPath(), so the first write to the spill file is the one refused
        TempBytesBudget budget = new TempBytesBudget(MB / 2);
        try (TikaInputStream tis =
                TikaInputStream.get(new ByteArrayInputStream(new byte[MB - 200_000]))) {
            tis.setTempBudget(budget);
            assertThrows(TempLimitReachedException.class, tis::getPath);
            // not a file holding only what was written after the failure
            assertThrows(TempLimitReachedException.class, tis::getPath);
            assertFalse(tis.hasFile());
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testReachingTheLimitIsFinal() throws Exception {
        ParseContext context = new ParseContext();
        TempBytesBudget budget = new TempBytesBudget(MB);
        context.set(TempBytesBudget.class, budget);
        try (TikaInputStream big = TikaInputStream.get(new byte[3 * MB])) {
            TempLimits.apply(big, context);
            assertThrows(TempLimitReachedException.class, big::getPath);
        }
        assertTrue(budget.isLimitReached());
        assertEquals(0, budget.getReservedBytes());
        // there is room again, but the task has failed
        try (TikaInputStream small = TikaInputStream.get(new byte[1024])) {
            assertThrows(TempLimitReachedException.class, () -> TempLimits.apply(small, context));
            small.setTempBudget(budget);
            assertThrows(TempLimitReachedException.class, small::getPath);
        }
    }

    @Test
    public void testChargeFollowsTheFile() throws IOException {
        TempBytesBudget budget = new TempBytesBudget(3 * MB);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTempBudget(budget);
            Path file = tmp.createTempFile();
            for (int i = 0; i < 3; i++) {
                // reopening truncates: 2 MB each time, never 4
                try (OutputStream os = tmp.newOutputStream(file)) {
                    os.write(new byte[2 * MB]);
                }
                assertEquals(2 * MB, budget.getReservedBytes());
            }
            tmp.deleteTempFile(file);
            assertFalse(Files.exists(file));
            assertEquals(0, budget.getReservedBytes());
        }
        assertFalse(budget.isLimitReached());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    public void testBudgetSetLateCountsWhatWasSpooled(String name,
                                                      Function<byte[], TikaInputStream> open)
            throws Exception {
        TempBytesBudget roomy = new TempBytesBudget(3 * MB);
        try (TikaInputStream tis = open.apply(new byte[2 * MB])) {
            tis.getPath();
            tis.setTempBudget(roomy);
            assertEquals(2 * MB, roomy.getReservedBytes());
        }
        assertEquals(0, roomy.getReservedBytes());

        ParseContext context = new ParseContext();
        TempBytesBudget tight = new TempBytesBudget(MB);
        context.set(TempBytesBudget.class, tight);
        try (TikaInputStream tis = open.apply(new byte[2 * MB])) {
            tis.getPath();
            assertThrows(TempLimitReachedException.class, () -> TempLimits.apply(tis, context));
        }
        assertEquals(0, tight.getReservedBytes());
    }

    @Test
    public void testFirstBudgetWins() throws IOException {
        TempBytesBudget first = new TempBytesBudget(3 * MB);
        TempBytesBudget second = new TempBytesBudget(3 * MB);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTempBudget(first);
            tmp.setTempBudget(second);
            tmp.setTempBudget(null);
            try (OutputStream os = tmp.newOutputStream(tmp.createTempFile())) {
                os.write(new byte[MB]);
            }
            assertEquals(MB, first.getReservedBytes());
            assertEquals(0, second.getReservedBytes());
        }
        assertEquals(0, first.getReservedBytes());
    }

    @Test
    public void testChargeDoesNotFollowLinks(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("a"), new byte[MB]);
        try {
            Files.createSymbolicLink(dir.resolve("b"), file);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "no symbolic links on this file system");
        }
        TempBytesBudget budget = new TempBytesBudget(3 * MB);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTempBudget(budget);
            tmp.chargeTempFile(dir);
            assertEquals(MB, budget.getReservedBytes());
        }
    }

    @Test
    public void testOnlyMinusOneMeansNoLimit() {
        assertThrows(IllegalArgumentException.class, () -> new TempLimits(0));
        assertThrows(IllegalArgumentException.class, () -> new TempLimits(-2));
        assertNull(new TempLimits(TempLimits.UNLIMITED).newBudget());
    }

    @Test
    public void testTightenedTo() {
        TempLimits ceiling = new TempLimits(10);
        assertEquals(10, new TempLimits(-1).tightenedTo(ceiling).getMaxTempBytes());
        assertEquals(10, new TempLimits(11).tightenedTo(ceiling).getMaxTempBytes());
        assertEquals(5, new TempLimits(5).tightenedTo(ceiling).getMaxTempBytes());
        assertEquals(11, new TempLimits(11).tightenedTo(new TempLimits(-1)).getMaxTempBytes());
    }
}
