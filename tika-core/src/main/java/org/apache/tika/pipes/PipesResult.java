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

    public enum STATUS {
        CLIENT_UNAVAILABLE_WITHIN_MS,
        PARSE_EXCEPTION_NO_EMIT,
        PARSE_EXCEPTION_EMIT, PARSE_SUCCESS,
        OOM, TIMEOUT, UNSPECIFIED_CRASH,
        NO_EMITTER_FOUND,
        EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_EXCEPTION
    }

    public static PipesResult CLIENT_UNAVAILABLE_WITHIN_MS =
            new PipesResult(STATUS.CLIENT_UNAVAILABLE_WITHIN_MS);
    public static PipesResult TIMEOUT = new PipesResult(STATUS.TIMEOUT);
    public static PipesResult OOM = new PipesResult(STATUS.OOM);
    public static PipesResult UNSPECIFIED_CRASH = new PipesResult(STATUS.UNSPECIFIED_CRASH);
    public static PipesResult EMIT_SUCCESS = new PipesResult(STATUS.EMIT_SUCCESS);
    public static PipesResult NO_EMITTER_FOUND = new PipesResult(STATUS.NO_EMITTER_FOUND);
    private final STATUS status;
    private final EmitData emitData;
    private final String message;

    private PipesResult(STATUS status, EmitData emitData, String message) {
        this.status = status;
        this.emitData = emitData;
        this.message = message;
    }

    public PipesResult(STATUS status) {
        this(status, null, null);
    }

    public PipesResult(STATUS status, String message) {
        this(status, null, message);
    }

    public PipesResult(EmitData emitData) {
        this(STATUS.PARSE_SUCCESS, emitData, null);
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
}
