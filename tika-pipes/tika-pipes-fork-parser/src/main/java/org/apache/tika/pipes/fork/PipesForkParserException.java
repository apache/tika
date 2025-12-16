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
package org.apache.tika.pipes.fork;

import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.PipesResult;

/**
 * Exception thrown when {@link PipesForkParser} encounters an application error.
 * <p>
 * This exception is thrown for application-level errors that indicate
 * infrastructure or configuration problems:
 * <ul>
 *   <li>Initialization failures (parser, fetcher, or emitter initialization)</li>
 *   <li>Configuration errors (fetcher or emitter not found)</li>
 *   <li>Client unavailable (no forked process available within timeout)</li>
 * </ul>
 * <p>
 * The following are NOT thrown as exceptions:
 * <ul>
 *   <li>Process crashes (OOM, timeout) - returned in result, next parse
 *       will automatically restart the forked process</li>
 *   <li>Per-document failures (fetch exception, parse exception) - returned
 *       in result so caller can handle gracefully</li>
 * </ul>
 *
 * @see PipesForkResult#isProcessCrash()
 * @see PipesForkResult#isApplicationError()
 */
public class PipesForkParserException extends TikaException {

    private final PipesResult.RESULT_STATUS status;

    /**
     * Creates a new exception with the given status and message.
     *
     * @param status the result status that caused this exception
     * @param message the error message
     */
    public PipesForkParserException(
            PipesResult.RESULT_STATUS status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Creates a new exception with the given status, message, and cause.
     *
     * @param status the result status that caused this exception
     * @param message the error message
     * @param cause the underlying cause
     */
    public PipesForkParserException(
            PipesResult.RESULT_STATUS status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Get the result status that caused this exception.
     *
     * @return the result status
     */
    public PipesResult.RESULT_STATUS getStatus() {
        return status;
    }

    /**
     * Check if this exception was caused by an initialization failure.
     *
     * @return true if initialization failed
     */
    public boolean isInitializationFailure() {
        return status == PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE
                || status == PipesResult.RESULT_STATUS.FETCHER_INITIALIZATION_EXCEPTION
                || status == PipesResult.RESULT_STATUS.EMITTER_INITIALIZATION_EXCEPTION;
    }

    /**
     * Check if this exception was caused by a configuration error.
     *
     * @return true if there was a configuration error
     */
    public boolean isConfigurationError() {
        return status == PipesResult.RESULT_STATUS.FETCHER_NOT_FOUND
                || status == PipesResult.RESULT_STATUS.EMITTER_NOT_FOUND;
    }
}
