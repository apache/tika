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
package org.apache.tika.server.component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring component that tracks server status, active tasks, and task completion.
 * This component is thread-safe and maintains the state of running operations.
 */
@Component
public class ServerStatus {

    private static final Logger LOG = LoggerFactory.getLogger(ServerStatus.class);

    private volatile STATUS currentStatus = STATUS.READY;
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, TaskInfo> activeTasks = new ConcurrentHashMap<>();

    /**
     * Enumeration of possible server statuses
     */
    public enum STATUS {
        READY,
        PROCESSING,
        ERROR,
        SHUTDOWN
    }

    /**
     * Enumeration of task types
     */
    public enum TASK {
        DETECT,
        PARSE,
        METADATA,
        TRANSLATE,
        UNPACK,
        ASYNC,
        EVAL,
        PIPES
    }

    /**
     * Information about an active task
     */
    public static class TaskInfo {
        private final long taskId;
        private final TASK taskType;
        private final String filename;
        private final long timeoutMillis;
        private final LocalDateTime startTime;
        private volatile LocalDateTime endTime;

        public TaskInfo(long taskId, TASK taskType, String filename, long timeoutMillis) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.filename = filename;
            this.timeoutMillis = timeoutMillis;
            this.startTime = LocalDateTime.now(ZoneId.systemDefault());
        }

        // Getters
        public long getTaskId() { return taskId; }
        public TASK getTaskType() { return taskType; }
        public String getFilename() { return filename; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }

        void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }

    /**
     * Start a new task and return its unique ID
     *
     * @param taskType The type of task being started
     * @param filename The filename being processed (can be null)
     * @param timeoutMillis Timeout in milliseconds
     * @return Unique task ID
     */
    public long start(TASK taskType, String filename, long timeoutMillis) {
        long taskId = taskIdCounter.incrementAndGet();
        TaskInfo taskInfo = new TaskInfo(taskId, taskType, filename, timeoutMillis);
        activeTasks.put(taskId, taskInfo);

        // Update server status to processing if not already in error state
        if (currentStatus != STATUS.ERROR && currentStatus != STATUS.SHUTDOWN) {
            currentStatus = STATUS.PROCESSING;
        }

        LOG.debug("Started task {} ({}) for file: {}", taskId, taskType, filename);
        return taskId;
    }

    /**
     * Mark a task as complete
     *
     * @param taskId The task ID to complete
     */
    public void complete(long taskId) {
        TaskInfo taskInfo = activeTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setEndTime(LocalDateTime.now(ZoneId.systemDefault()));
            LOG.debug("Completed task {} ({}) for file: {}", taskId, taskInfo.getTaskType(), taskInfo.getFilename());
        } else {
            LOG.warn("Attempted to complete unknown task ID: {}", taskId);
        }

        // If no more active tasks and not in error/shutdown state, return to ready
        if (activeTasks.isEmpty() && currentStatus == STATUS.PROCESSING) {
            currentStatus = STATUS.READY;
        }
    }

    /**
     * Set the server status
     *
     * @param status The new status
     */
    public void setStatus(STATUS status) {
        STATUS oldStatus = this.currentStatus;
        this.currentStatus = status;
        LOG.info("Server status changed from {} to {}", oldStatus, status);
    }

    /**
     * Get the current server status
     *
     * @return Current status
     */
    public STATUS getStatus() {
        return currentStatus;
    }

    /**
     * Get the number of active tasks
     *
     * @return Number of active tasks
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    /**
     * Get information about a specific task
     *
     * @param taskId The task ID
     * @return TaskInfo or null if not found
     */
    public TaskInfo getTaskInfo(long taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * Get all active task IDs
     *
     * @return Array of active task IDs
     */
    public Long[] getActiveTaskIds() {
        return activeTasks.keySet().toArray(new Long[0]);
    }

    /**
     * Check if the server is ready to accept new tasks
     *
     * @return true if ready, false otherwise
     */
    public boolean isReady() {
        return currentStatus == STATUS.READY || currentStatus == STATUS.PROCESSING;
    }

    /**
     * Forcefully cancel all active tasks (for shutdown scenarios)
     */
    public void cancelAllTasks() {
        LOG.info("Cancelling {} active tasks", activeTasks.size());
        for (TaskInfo taskInfo : activeTasks.values()) {
            taskInfo.setEndTime(LocalDateTime.now(ZoneId.systemDefault()));
            LOG.debug("Cancelled task {} ({}) for file: {}",
                taskInfo.getTaskId(), taskInfo.getTaskType(), taskInfo.getFilename());
        }
        activeTasks.clear();
        setStatus(STATUS.SHUTDOWN);
    }
}
