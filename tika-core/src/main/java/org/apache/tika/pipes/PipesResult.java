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
package org.apache.tika.pipes;

import org.apache.tika.pipes.emitter.EmitData;

public class PipesResult {

    private boolean intermediate = false;

    public enum STATUS {
        CLIENT_UNAVAILABLE_WITHIN_MS,
        FETCHER_INITIALIZATION_EXCEPTION,
        FETCH_EXCEPTION,
        EMPTY_OUTPUT,
        PARSE_EXCEPTION_NO_EMIT, //within the pipes server
        PARSE_EXCEPTION_EMIT, //within the pipes server
        PARSE_SUCCESS, //when passed back to the async processor for emit
        PARSE_SUCCESS_WITH_EXCEPTION,//when passed back to the async processor for emit
        OOM, TIMEOUT, UNSPECIFIED_CRASH,
        NO_EMITTER_FOUND,
        EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_EXCEPTION,
        INTERRUPTED_EXCEPTION, NO_FETCHER_FOUND,
        INTERMEDIATE_RESULT;
    }

    public static final PipesResult CLIENT_UNAVAILABLE_WITHIN_MS =
            new PipesResult(STATUS.CLIENT_UNAVAILABLE_WITHIN_MS);
    public static final PipesResult TIMEOUT = new PipesResult(STATUS.TIMEOUT);
    public static final PipesResult OOM = new PipesResult(STATUS.OOM);
    public static final PipesResult UNSPECIFIED_CRASH = new PipesResult(STATUS.UNSPECIFIED_CRASH);
    public static final PipesResult EMIT_SUCCESS = new PipesResult(STATUS.EMIT_SUCCESS);
    public static final PipesResult INTERRUPTED_EXCEPTION = new PipesResult(STATUS.INTERRUPTED_EXCEPTION);
    public static final PipesResult EMPTY_OUTPUT =
            new PipesResult(STATUS.EMPTY_OUTPUT);
    private final STATUS status;
    private final EmitData emitData;
    private final String message;

    private PipesResult(STATUS status, EmitData emitData, String message, boolean intermediate) {
        this.status = status;
        this.emitData = emitData;
        this.message = message;
        this.intermediate = intermediate;
    }

    public PipesResult(STATUS status) {
        this(status, null, null, false);
    }

    public PipesResult(STATUS status, String message) {
        this(status, null, message, false);
    }

    /**
     * This assumes parse success with no parse exception
     *
     * @param emitData
     */
    public PipesResult(EmitData emitData) {
        this(STATUS.PARSE_SUCCESS, emitData, null, false);
    }

    public PipesResult(STATUS status, EmitData emitData, boolean intermediate) {
        this(status, emitData, null, intermediate);
    }

    /**
     * This assumes that the message is a stack trace (container
     * parse exception).
     *
     * @param emitData
     * @param message
     */
    public PipesResult(EmitData emitData, String message) {
        this(STATUS.PARSE_SUCCESS_WITH_EXCEPTION, emitData, message, false);
    }

    public STATUS getStatus() {
        return status;
    }

    public EmitData getEmitData() {
        return emitData;
    }

    public String getMessage() {
        return message;
    }

    public boolean isIntermediate() {
        return intermediate;
    }

    @Override
    public String toString() {
        return "PipesResult{" + "intermediate=" + intermediate + ", status=" + status +
                ", emitData=" + emitData + ", message='" + message + '\'' + '}';
    }
}
