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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * {@link ServerManager} offers two spellings of "recycle this worker" and every in-repo caller
 * uses the reason form, so a regression in the no-arg form is invisible to every other test.
 * These pin the contract from the <em>caller's</em> side: whichever spelling a downstream
 * integration picked up, the worker must actually be marked.
 */
public class ServerManagerMarkContractTest {

    /** Overrides only the no-arg form -- what a pre-RestartReason implementation would have. */
    private static class NoArgOnly implements ServerManager {
        private int marks;

        @Override
        public void markServerForRestart() {
            marks++;
        }

        @Override
        public int getPort() {
            return -1;
        }

        @Override
        public void ensureRunning() {
        }

        @Override
        public Socket connect(int socketTimeoutMillis) {
            return null;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public Path getTempDirectory() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    private static PipesConfig config() {
        return new PipesConfig();
    }

    @Test
    public void testPerClientHonoursBothSpellings() {
        PerClientServerManager sm = new PerClientServerManager(config(), null, 0);
        sm.markServerForRestart();
        assertTrue(sm.needsRestart(), "no-arg markServerForRestart() must recycle the worker");

        PerClientServerManager other = new PerClientServerManager(config(), null, 1);
        other.markServerForRestart(RestartReason.OOM);
        assertTrue(other.needsRestart(), "reason form must recycle the worker");
    }

    @Test
    public void testSharedHonoursBothSpellings() {
        SharedServerManager sm = new SharedServerManager(config(), null, 2);
        sm.markServerForRestart();
        assertTrue(sm.needsRestart(), "no-arg markServerForRestart() must recycle the worker");

        SharedServerManager other = new SharedServerManager(config(), null, 2);
        other.markServerForRestart(RestartReason.OOM);
        assertTrue(other.needsRestart(), "reason form must recycle the worker");
    }

    @Test
    public void testReasonFormReachesANoArgOnlyImplementation() {
        NoArgOnly sm = new NoArgOnly();
        sm.markServerForRestart(RestartReason.OOM);
        assertEquals(1, sm.marks, "reason form must fall back to an older no-arg implementation");
    }

    @Test
    public void testDefaultsDoNotRecurse() {
        // markServerForRestart(reason) defaults to the no-arg form, so the no-arg form must not
        // default back to it: an implementation overriding neither would blow the stack.
        ServerManager sm = new ServerManager() {
            @Override
            public int getPort() {
                return -1;
            }

            @Override
            public void ensureRunning() {
            }

            @Override
            public Socket connect(int socketTimeoutMillis) {
                return null;
            }

            @Override
            public void shutdown() {
            }

            @Override
            public boolean isRunning() {
                return false;
            }

            @Override
            public Path getTempDirectory() {
                return null;
            }

            @Override
            public void close() {
            }
        };
        sm.markServerForRestart();
        sm.markServerForRestart(RestartReason.CRASH);
    }
}
