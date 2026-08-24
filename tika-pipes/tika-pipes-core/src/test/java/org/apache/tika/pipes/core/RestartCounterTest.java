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

import org.junit.jupiter.api.Test;

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
        // generic crash first, then refined from the exit code
        RestartCounter c = new RestartCounter();
        c.mark(RestartReason.CRASH);
        c.mark(RestartReason.OOM);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.OOM));
        assertEquals(0, c.count(RestartReason.CRASH));
    }

    @Test
    public void testUnmarkedAttributedByExitCode() {
        RestartCounter c = new RestartCounter();
        c.restarted(PipesServer.IDLE_EXIT_CODE);
        c.restarted(0); // unexplained clean exit: the parent did not ask for it
        c.restarted(1);
        c.restarted(-1);
        assertEquals(1, c.count(RestartReason.IDLE));
        assertEquals(3, c.count(RestartReason.CRASH));
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
}
