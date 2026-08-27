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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.server.PipesServer;

public class RestartCounterTest {

    @Test
    public void testMarkedReasonWins() {
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.MAX_FILES);
        c.restarted(PipesServer.IDLE_EXIT_CODE);
        assertEquals(1, c.count(RestartReason.MAX_FILES));
        assertEquals(0, c.count(RestartReason.IDLE));
    }

    @Test
    public void testLastMarkWins() {
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.CRASH);
        c.mark(RestartReason.OOM);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.OOM));
        assertEquals(0, c.count(RestartReason.CRASH));
    }

    @Test
    public void testMarkIfUnmarkedKeepsSpecificReason() {
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.OOM);
        c.markIfUnmarked(RestartReason.CRASH);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.OOM));
        c.markIfUnmarked(RestartReason.CRASH);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.CRASH));
    }

    @ParameterizedTest
    @MethodSource("exitCodes")
    public void testUnmarkedAttributedByExitCode(int exitCode, RestartReason expected) {
        RestartCounter c = new RestartCounter();
        c.restarted(exitCode);
        for (RestartReason r : RestartReason.values()) {
            assertEquals(r == expected ? 1 : 0, c.count(r), r.name());
        }
    }

    /**
     * Enumerated from the codes the child can deliberately choose ({@link PipesMessageType} and
     * {@link PipesServer}), not from RestartCounter's own branches: a table derived from the
     * implementation cannot catch a case the implementation forgot.
     */
    static Stream<Arguments> exitCodes() {
        return Stream.of(
                Arguments.of(PipesServer.IDLE_EXIT_CODE, RestartReason.IDLE),
                Arguments.of(PipesMessageType.OOM.getExitCode().getAsInt(), RestartReason.OOM),
                Arguments.of(PipesMessageType.TIMEOUT.getExitCode().getAsInt(), RestartReason.TIMEOUT),
                Arguments.of(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().getAsInt(), RestartReason.CRASH),
                Arguments.of(0, RestartReason.SHUTDOWN),
                Arguments.of(1, RestartReason.CRASH),
                Arguments.of(-1, RestartReason.CRASH));
    }

    @Test
    public void testFatalReasonOutranksScheduledRecycle() {
        // The file-limit boundary document may be the one that killed the worker; a scheduled
        // recycle must not overwrite the fatal reason recorded for the same pending restart.
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.OOM);
        c.markIfUnmarked(RestartReason.MAX_FILES);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.OOM));
        assertEquals(0, c.count(RestartReason.MAX_FILES));
    }

    @Test
    public void testPendingClearsAfterRestart() {
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.TIMEOUT);
        c.restarted(-1);
        c.restarted(PipesServer.IDLE_EXIT_CODE);
        assertEquals(1, c.count(RestartReason.TIMEOUT));
        assertEquals(1, c.count(RestartReason.IDLE));
    }

    /** A mark left over from a start that never produced a process must not bleed into the next restart. */
    @Test
    public void testFirstStartNotCountedAndClearsMark() {
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.CRASH);
        c.restarted((Process) null);
        for (RestartReason r : RestartReason.values()) {
            assertEquals(0, c.count(r), r.name());
        }
        c.restarted(PipesServer.IDLE_EXIT_CODE);
        assertEquals(1, c.count(RestartReason.IDLE));
        assertEquals(0, c.count(RestartReason.CRASH));
    }
}
