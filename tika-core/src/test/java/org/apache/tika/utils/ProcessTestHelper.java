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
package org.apache.tika.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finds the processes a test started by a marker string that appears in their command lines.
 */
public final class ProcessTestHelper {

    private static final long POLL_MILLIS = 20;

    private ProcessTestHelper() {
    }

    /** Live processes whose command line contains {@code marker}; a zombie has no command line. */
    public static List<ProcessHandle> processesWith(String marker) {
        List<ProcessHandle> found = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(ph -> {
            if (ph.info().commandLine().orElse("").contains(marker) && ph.isAlive()) {
                found.add(ph);
            }
        });
        return found;
    }

    /**
     * Polls {@link #processesWith(String)} until it returns {@code count} processes or
     * {@code timeoutMillis} passes.
     *
     * @return the processes found by the last poll
     */
    public static List<ProcessHandle> awaitProcessesWith(String marker, int count, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        List<ProcessHandle> found = processesWith(marker);
        while (found.size() != count && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS);
            found = processesWith(marker);
        }
        return found;
    }

    /** One {@code pid:ppid=N:commandLine} entry per process, for assertion messages. */
    public static List<String> describe(List<ProcessHandle> processes) {
        List<String> described = new ArrayList<>();
        for (ProcessHandle ph : processes) {
            described.add(ph.pid() + ":ppid=" + ph.parent().map(ProcessHandle::pid).orElse(-1L)
                    + ":" + ph.info().commandLine().orElse("?"));
        }
        return described;
    }
}
