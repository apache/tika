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
package org.apache.tika.server;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerStatus {

    enum STATUS {
        OPEN(0),
        HIT_MAX(1),
        TIMEOUT(2),
        ERROR(3),
        PARENT_REQUESTED_SHUTDOWN(4);

        private final int shutdownCode;
        STATUS(int shutdownCode) {
            this.shutdownCode = shutdownCode;
        }
        int getShutdownCode() {
            return shutdownCode;
        }
    }
    enum TASK {
        PARSE,
        UNZIP,
        DETECT,
        METADATA
    };

    private final int maxFilesToProcess;
    private AtomicInteger counter = new AtomicInteger(0);
    private Map<Integer, TaskStatus> tasks = new HashMap<>();

    private STATUS status = STATUS.OPEN;
    public ServerStatus(int maxFilesToProcess) {
        this.maxFilesToProcess = maxFilesToProcess;
    }
    public synchronized int start(TASK task, String fileName) throws FileCountExceededException {
        int i = counter.incrementAndGet();
        if (i == Integer.MAX_VALUE ||
                (maxFilesToProcess > 0 && i >= maxFilesToProcess)) {
            setStatus(STATUS.HIT_MAX);
            throw new FileCountExceededException();
        }
        tasks.put(i, new TaskStatus(task, Instant.now(), fileName));
        return i;
    }

    /**
     * Removes the task from the collection of currently running tasks.
     *
     * @param taskId
     * @throws IllegalArgumentException if there is no task by that taskId in the collection
     */
    public synchronized void complete(int taskId) throws IllegalArgumentException {
        TaskStatus status = tasks.remove(taskId);
        if (status == null) {
            throw new IllegalArgumentException("TaskId is not in map:"+taskId);
        }
    }

    public synchronized void setStatus(STATUS status) {
        this.status = status;
    }

    public synchronized STATUS getStatus() {
        return status;
    }

    public synchronized Map<Integer, TaskStatus> getTasks() {
        Map<Integer, TaskStatus> ret = new HashMap<>();
        ret.putAll(tasks);
        return ret;
    }

    public synchronized int getFilesProcessed() {
        return counter.get();
    }
}
