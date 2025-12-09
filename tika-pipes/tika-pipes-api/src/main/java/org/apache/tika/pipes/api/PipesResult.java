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
     * Categories help distinguish between:
     * <ul>
     *   <li>Process crashes (OOM, timeout, system-level failures)</li>
     *   <li>Application errors (process ran but encountered errors)</li>
     *   <li>Successful processing (possibly with warnings)</li>
     * </ul>
     */
    public enum CATEGORY {
        /** Forked process crashed due to OOM, timeout, or other system failure */
        PROCESS_CRASH,

        /** Process completed but encountered application-level errors */
        APPLICATION_ERROR,

        /** Processing completed successfully (possibly with warnings) */
        SUCCESS
    }

    public enum RESULT_STATUS {
        // Process crashes
        OOM(CATEGORY.PROCESS_CRASH),
        TIMEOUT(CATEGORY.PROCESS_CRASH),
        UNSPECIFIED_CRASH(CATEGORY.PROCESS_CRASH),

        // Initialization failures
        FAILED_TO_INITIALIZE(CATEGORY.APPLICATION_ERROR),

        // Process crashes (system-level failures)
        CLIENT_UNAVAILABLE_WITHIN_MS(CATEGORY.APPLICATION_ERROR),

        // Fetch failures
        FETCHER_INITIALIZATION_EXCEPTION(CATEGORY.APPLICATION_ERROR),
        FETCH_EXCEPTION(CATEGORY.APPLICATION_ERROR),

        // Success with edge case
        EMPTY_OUTPUT(CATEGORY.SUCCESS),


        // Parse success states
        PARSE_SUCCESS(CATEGORY.SUCCESS),
        PARSE_SUCCESS_WITH_EXCEPTION(CATEGORY.SUCCESS),
        PARSE_EXCEPTION_NO_EMIT(CATEGORY.SUCCESS),


        // Emit success states
        EMIT_SUCCESS(CATEGORY.SUCCESS),
        EMIT_SUCCESS_PARSE_EXCEPTION(CATEGORY.SUCCESS),
        EMIT_SUCCESS_PASSBACK(CATEGORY.SUCCESS),

        // Emit failure
        EMIT_EXCEPTION(CATEGORY.APPLICATION_ERROR),

        // Emitter failures
        EMITTER_INITIALIZATION_EXCEPTION(CATEGORY.APPLICATION_ERROR),
        EMITTER_NOT_FOUND(CATEGORY.APPLICATION_ERROR),

        // Other errors
        INTERRUPTED_EXCEPTION(CATEGORY.APPLICATION_ERROR),
        FETCHER_NOT_FOUND(CATEGORY.APPLICATION_ERROR);


        private final CATEGORY category;

        RESULT_STATUS(CATEGORY category) {
            this.category = category;
        }

        /**
         * Gets the high-level category for this result status.
         *
         * @return the category (PROCESS_CRASH, APPLICATION_ERROR, or SUCCESS)
         */
        public CATEGORY getCategory() {
            return category;
        }

        /**
         * Checks if this status represents a process crash (OOM, timeout, etc.).
         *
         * @return true if the forked process crashed
         */
        public boolean isProcessCrash() {
            return category == CATEGORY.PROCESS_CRASH;
        }

        /**
         * Checks if this status represents an application error.
         *
         * @return true if the process ran but encountered an error
         */
        public boolean isApplicationError() {
            return category == CATEGORY.APPLICATION_ERROR;
        }

        /**
         * Checks if this status represents successful processing. Successful
         * processing includes handling standard runtime exceptions during the
         * parse.
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
     * @return the category (PROCESS_CRASH, APPLICATION_ERROR, or SUCCESS)
     */
    public CATEGORY getCategory() {
        return status.getCategory();
    }

    /**
     * Checks if this result represents a process crash (OOM, timeout, etc.).
     * <p>
     * Process crashes are system-level failures where the forked process
     * terminated abnormally, as opposed to application errors where the
     * process completed but encountered errors during execution.
     *
     * @return true if the forked process crashed
     */
    public boolean isProcessCrash() {
        return status.isProcessCrash();
    }

    /**
     * Checks if this result represents an application error.
     * <p>
     * Application errors occur when the process runs but encounters
     * errors during fetch, parse, or emit operations. These are
     * caught runtime exceptions, not process crashes.
     *
     * @return true if the process ran but encountered an error
     */
    public boolean isApplicationError() {
        return status.isApplicationError();
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
