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

import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.server.PipesServer;

/**
 * Reason marked while a restart is pending, plus counts of restarts performed, by reason.
 * Last mark wins; an unmarked restart is attributed by the old process's exit code.
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

    /** Marks {@code reason} unless a more specific one is already pending. */
    void markIfUnmarked(RestartReason reason) {
        if (pending == null) {
            pending = reason;
        }
    }

    /**
     * Records a restart of {@code previous}. Null means there was no process to restart
     * (first start, or the last start failed): nothing is counted and any mark is dropped,
     * since it referred to a restart that never happened.
     */
    void restarted(Process previous) {
        if (previous == null) {
            pending = null;
            return;
        }
        restarted(previous.isAlive() ? -1 : previous.exitValue());
    }

    /** Unmarked restarts are attributed by the exit code the child chose. */
    void restarted(int exitCode) {
        RestartReason reason = pending;
        pending = null;
        if (reason == null) {
            reason = fromExitCode(exitCode);
        }
        counts.get(reason).increment();
    }

    /**
     * The child reports why it died in its exit status, and the parent may reach a restart
     * without ever having read the corresponding frame (the socket can break first, or the
     * child can outlive the parent's one-second wait). Every code the child can deliberately
     * choose is honoured here; only genuinely unexplained deaths fall through to CRASH.
     */
    private static RestartReason fromExitCode(int exitCode) {
        if (exitCode == PipesServer.IDLE_EXIT_CODE) {
            return RestartReason.IDLE;
        }
        if (exitCode == 0) {
            // Only PipesServer's SHUT_DOWN handler exits 0, and the parent is the only sender:
            // a worker we asked to stop is not a crash, however we noticed it was gone.
            return RestartReason.SHUTDOWN;
        }
        if (matches(PipesMessageType.OOM, exitCode)) {
            return RestartReason.OOM;
        }
        if (matches(PipesMessageType.TIMEOUT, exitCode)) {
            return RestartReason.TIMEOUT;
        }
        return RestartReason.CRASH;
    }

    private static boolean matches(PipesMessageType type, int exitCode) {
        return type.getExitCode().orElse(Integer.MIN_VALUE) == exitCode;
    }

    long count(RestartReason reason) {
        return counts.get(reason).sum();
    }
}
