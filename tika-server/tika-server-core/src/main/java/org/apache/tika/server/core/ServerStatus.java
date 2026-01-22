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
package org.apache.tika.server.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only server status for tracking active tasks and statistics.
 * <p>
 * This class provides observability into server activity. With pipes-based parsing,
 * timeouts and process crashes are handled by the pipes infrastructure, so this
 * class no longer triggers server shutdowns.
 */
public class ServerStatus {

    private final AtomicLong counter = new AtomicLong(0);
    private final Map<Long, TaskStatus> tasks = new HashMap<>();
    private volatile long lastStarted = Instant.now().toEpochMilli();

    /**
     * Records the start of a task and returns a task ID for tracking.
     *
     * @param task the type of task
     * @param fileName the file being processed (for diagnostics)
     * @return the task ID
     */
    public synchronized long start(TASK task, String fileName) {
        long taskId = counter.incrementAndGet();
        Instant now = Instant.now();
        lastStarted = now.toEpochMilli();
        tasks.put(taskId, new TaskStatus(task, now, fileName));
        return taskId;
    }

    /**
     * Removes the task from the collection of currently running tasks.
     *
     * @param taskId the task ID returned from start()
     * @throws IllegalArgumentException if there is no task by that taskId
     */
    public synchronized void complete(long taskId) throws IllegalArgumentException {
        TaskStatus status = tasks.remove(taskId);
        if (status == null) {
            throw new IllegalArgumentException("TaskId is not in map: " + taskId);
        }
    }

    /**
     * Returns a snapshot of currently running tasks.
     */
    public synchronized Map<Long, TaskStatus> getTasks() {
        return new HashMap<>(tasks);
    }

    /**
     * Returns the total number of tasks started since server startup.
     */
    public long getFilesProcessed() {
        return counter.get();
    }

    /**
     * Returns milliseconds since the last task was started.
     */
    public long getMillisSinceLastParseStarted() {
        return Instant.now().toEpochMilli() - lastStarted;
    }

    public enum TASK {
        PARSE, DETECT, TRANSLATE
    }
}
