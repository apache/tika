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

import java.util.EnumMap;
import java.util.concurrent.atomic.LongAdder;

import org.apache.tika.pipes.core.server.PipesServer;

/**
 * Per-manager restart bookkeeping: the reason recorded while a restart is pending, and
 * monotonic counts of restarts actually performed, by reason.
 * <p>
 * Last recorded reason wins: a crash is first seen generically and then refined
 * (OOM/TIMEOUT) from the exit code, so the refinement must overwrite.
 */
final class RestartCounter {

    private final EnumMap<RestartReason, LongAdder> counts = new EnumMap<>(RestartReason.class);
    private volatile RestartReason pending;

    RestartCounter() {
        for (RestartReason r : RestartReason.values()) {
            counts.put(r, new LongAdder());
        }
    }

    void mark(RestartReason reason) {
        pending = reason;
    }

    /**
     * Records that a restart happened. With no reason marked, the worker died on its own:
     * the idle exit code is the idle shutdown, anything else -- including an unexplained
     * 0 -- is a crash.
     */
    void restarted(int exitCode) {
        RestartReason reason = pending;
        pending = null;
        if (reason == null) {
            reason = exitCode == PipesServer.IDLE_EXIT_CODE ? RestartReason.IDLE : RestartReason.CRASH;
        }
        counts.get(reason).increment();
    }

    long count(RestartReason reason) {
        return counts.get(reason).sum();
    }
}
