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
package org.apache.tika.pipes.api;

import java.io.Serializable;

import org.apache.tika.pipes.api.emitter.EmitData;

public record PipesResult(RESULT_STATUS status, EmitData emitData, String message) implements Serializable {

    /**
     * High-level categorization of result statuses.
     * <p>
     * Categories help distinguish between different types of failures and successes,
     * allowing infrastructure to decide how to handle each case:
     * <ul>
     *   <li>{@link #FATAL} - System cannot continue, must be fixed and restarted</li>
     *   <li>{@link #INITIALIZATION_FAILURE} - Component failed to initialize, might be transient</li>
     *   <li>{@link #TASK_EXCEPTION} - This task failed, but other tasks may succeed</li>
     *   <li>{@link #PROCESS_CRASH} - Forked process crashed, auto-restart and continue</li>
     *   <li>{@link #SUCCESS} - Processing completed successfully (possibly with warnings)</li>
     * </ul>
     */
    public enum CATEGORY {
        /** Fatal system error - cannot continue, must be fixed and restarted */
        FATAL,

        /** Component initialization failed - processing should stop, might be transient */
        INITIALIZATION_FAILURE,

        /** Task-level exception - this task failed, log and continue with next task */
        TASK_EXCEPTION,

        /** Forked process crashed due to OOM, timeout, or other system failure - auto-restart */
        PROCESS_CRASH,

        /** Processing completed successfully (possibly with warnings) */
        SUCCESS
    }

    public enum RESULT_STATUS {
        // Fatal errors - system cannot continue
        FAILED_TO_INITIALIZE(CATEGORY.FATAL),

        // Initialization failures - component failed to start, might be transient
        FETCHER_INITIALIZATION_EXCEPTION(CATEGORY.INITIALIZATION_FAILURE),
        EMITTER_INITIALIZATION_EXCEPTION(CATEGORY.INITIALIZATION_FAILURE),
        CLIENT_UNAVAILABLE_WITHIN_MS(CATEGORY.INITIALIZATION_FAILURE),

        // Task exceptions - this task failed, continue with next
        FETCH_EXCEPTION(CATEGORY.TASK_EXCEPTION),
        EMIT_EXCEPTION(CATEGORY.TASK_EXCEPTION),
        FETCHER_NOT_FOUND(CATEGORY.TASK_EXCEPTION),
        EMITTER_NOT_FOUND(CATEGORY.TASK_EXCEPTION),

        // Process crashes - forked process died, auto-restart
        OOM(CATEGORY.PROCESS_CRASH),
        TIMEOUT(CATEGORY.PROCESS_CRASH),
        UNSPECIFIED_CRASH(CATEGORY.PROCESS_CRASH),

        // Success states
        EMPTY_OUTPUT(CATEGORY.SUCCESS),
        PARSE_SUCCESS(CATEGORY.SUCCESS),
        PARSE_SUCCESS_WITH_EXCEPTION(CATEGORY.SUCCESS),
        PARSE_EXCEPTION_NO_EMIT(CATEGORY.SUCCESS),
        EMIT_SUCCESS(CATEGORY.SUCCESS),
        EMIT_SUCCESS_PARSE_EXCEPTION(CATEGORY.SUCCESS),
        EMIT_SUCCESS_PASSBACK(CATEGORY.SUCCESS);


        private final CATEGORY category;

        RESULT_STATUS(CATEGORY category) {
            this.category = category;
        }

        /**
         * Gets the high-level category for this result status.
         *
         * @return the category (FATAL, INITIALIZATION_FAILURE, TASK_EXCEPTION, PROCESS_CRASH, or SUCCESS)
         */
        public CATEGORY getCategory() {
            return category;
        }

        /**
         * Checks if this status represents a fatal error.
         * <p>
         * Fatal errors mean the system cannot continue and must be fixed and restarted.
         *
         * @return true if this is a fatal error
         */
        public boolean isFatal() {
            return category == CATEGORY.FATAL;
        }

        /**
         * Checks if this status represents an initialization failure.
         * <p>
         * Initialization failures occur when a component (fetcher, emitter, client)
         * fails to start. These might be transient (e.g., network issues) or require
         * configuration fixes.
         *
         * @return true if a component failed to initialize
         */
        public boolean isInitializationFailure() {
            return category == CATEGORY.INITIALIZATION_FAILURE;
        }

        /**
         * Checks if this status represents a task-level exception.
         * <p>
         * Task exceptions indicate this specific task failed, but other tasks
         * may succeed. Processing can continue with the next task.
         *
         * @return true if this task failed
         */
        public boolean isTaskException() {
            return category == CATEGORY.TASK_EXCEPTION;
        }

        /**
         * Checks if this status represents a process crash (OOM, timeout, etc.).
         * <p>
         * Process crashes are system-level failures where the forked process
         * terminated abnormally. The process will be auto-restarted and
         * processing can continue.
         *
         * @return true if the forked process crashed
         */
        public boolean isProcessCrash() {
            return category == CATEGORY.PROCESS_CRASH;
        }

        /**
         * Checks if this status represents successful processing.
         * <p>
         * Success includes normal completion as well as cases where
         * processing completed with warnings (e.g., PARSE_SUCCESS_WITH_EXCEPTION).
         *
         * @return true if processing completed successfully (possibly with warnings)
         */
        public boolean isSuccess() {
            return category == CATEGORY.SUCCESS;
        }

        public static RESULT_STATUS lookup(int b) {
            if (b < 1) {
                throw new IllegalArgumentException("bad result value: " + b);
            }
            int ordinal = b - 1;
            if (ordinal >= RESULT_STATUS.values().length) {
                throw new IllegalArgumentException("ordinal > than array length? " + ordinal);
            }
            return RESULT_STATUS.values()[ordinal];
        }

        public byte getByte() {
            return (byte) (ordinal() + 1);
        }
    }

    public PipesResult(RESULT_STATUS status) {
        this(status, null, null);
    }

    public PipesResult(RESULT_STATUS status, EmitData emitData) {
        this(status, emitData, null);
    }

    public PipesResult(RESULT_STATUS status, String message) {
        this(status, null, message);
    }

    /**
     * Gets the high-level category for this result.
     *
     * @return the category (FATAL, INITIALIZATION_FAILURE, TASK_EXCEPTION, PROCESS_CRASH, or SUCCESS)
     */
    public CATEGORY getCategory() {
        return status.getCategory();
    }

    /**
     * Checks if this result represents a fatal error.
     * <p>
     * Fatal errors mean the system cannot continue and must be fixed and restarted.
     *
     * @return true if this is a fatal error
     */
    public boolean isFatal() {
        return status.isFatal();
    }

    /**
     * Checks if this result represents an initialization failure.
     * <p>
     * Initialization failures occur when a component (fetcher, emitter, client)
     * fails to start. These might be transient (e.g., network issues) or require
     * configuration fixes.
     *
     * @return true if a component failed to initialize
     */
    public boolean isInitializationFailure() {
        return status.isInitializationFailure();
    }

    /**
     * Checks if this result represents a task-level exception.
     * <p>
     * Task exceptions indicate this specific task failed, but other tasks
     * may succeed. Processing can continue with the next task.
     *
     * @return true if this task failed
     */
    public boolean isTaskException() {
        return status.isTaskException();
    }

    /**
     * Checks if this result represents a process crash (OOM, timeout, etc.).
     * <p>
     * Process crashes are system-level failures where the forked process
     * terminated abnormally. The process will be auto-restarted and
     * processing can continue.
     *
     * @return true if the forked process crashed
     */
    public boolean isProcessCrash() {
        return status.isProcessCrash();
    }

    /**
     * Checks if this result represents successful processing.
     * <p>
     * Success includes normal completion as well as cases where
     * processing completed with warnings (e.g., PARSE_SUCCESS_WITH_EXCEPTION).
     *
     * @return true if processing completed successfully (possibly with warnings)
     */
    public boolean isSuccess() {
        return status.isSuccess();
    }

}
