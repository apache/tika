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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerStatus {

    private static final Logger LOG = LoggerFactory.getLogger(ServerStatus.class);
    private final String serverId;
    private final int numRestarts;

    private final boolean isLegacy;
    private AtomicLong counter = new AtomicLong(0);
    private Map<Long, TaskStatus> tasks = new HashMap<>();
    private STATUS status = STATUS.OPERATING;
    private volatile long lastStarted = Instant.now().toEpochMilli();

    public ServerStatus(String serverId, int numRestarts) {
        this(serverId, numRestarts, false);
    }

    public ServerStatus(String serverId, int numRestarts, boolean isLegacy) {
        this.serverId = serverId;
        this.numRestarts = numRestarts;
        this.isLegacy = isLegacy;
    }

    public synchronized long start(TASK task, String fileName, long timeoutMillis) {
        long taskId = counter.incrementAndGet();
        Instant now = Instant.now();
        lastStarted = now.toEpochMilli();
        tasks.put(taskId, new TaskStatus(task, now, fileName, timeoutMillis));
        return taskId;
    }

    /**
     * Removes the task from the collection of currently running tasks.
     *
     * @param taskId
     * @throws IllegalArgumentException if there is no task by that taskId in the collection
     */
    public synchronized void complete(long taskId) throws IllegalArgumentException {
        TaskStatus status = tasks.remove(taskId);
        if (status == null) {
            throw new IllegalArgumentException("TaskId is not in map:" + taskId);
        }
    }

    public synchronized STATUS getStatus() {
        return status;
    }

    public synchronized void setStatus(STATUS status) {
        this.status = status;
    }

    public synchronized Map<Long, TaskStatus> getTasks() {
        return new HashMap<>(tasks);
    }

    public synchronized long getFilesProcessed() {
        return counter.get();
    }

    public long getMillisSinceLastParseStarted() {
        return Instant.now().toEpochMilli() - lastStarted;
    }

    /**
     * @return true if this is legacy, otherwise whether or not status == OPERATING.
     */
    public synchronized boolean isOperating() {
        if (isLegacy) {
            return true;
        }
        return status == STATUS.OPERATING;
    }

    public String getServerId() {
        return serverId;
    }

    public int getNumRestarts() {
        return numRestarts;
    }

    enum DIRECTIVES {
        PING((byte) 0), PING_ACTIVE_SERVER_TASKS((byte) 1), SHUTDOWN((byte) 2);

        private final byte b;

        DIRECTIVES(byte b) {
            this.b = b;
        }

        byte getByte() {
            return b;
        }
    }

    public enum STATUS {
        INITIALIZING(0), OPERATING(1), HIT_MAX_FILES(2), TIMEOUT(3), ERROR(4),
        PARENT_REQUESTED_SHUTDOWN(5), PARENT_EXCEPTION(6), OFF(7);

        private final int shutdownCode;

        STATUS(int shutdownCode) {
            this.shutdownCode = shutdownCode;
        }

        static STATUS lookup(int i) {
            STATUS[] values = STATUS.values();
            if (i < 0 || i >= values.length) {
                throw new ArrayIndexOutOfBoundsException(
                        i + " is not acceptable for an array of length " + values.length);
            }
            return STATUS.values()[i];
        }

        int getShutdownCode() {
            return shutdownCode;
        }

        int getInt() {
            return shutdownCode;
        }

    }

    public enum TASK {
        PARSE, DETECT, TRANSLATE
    }
}
