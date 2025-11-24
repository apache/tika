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

import org.apache.tika.pipes.api.emitter.EmitData;

public record PipesResult(STATUS status, EmitData emitData, String message, boolean intermediate) {
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
        EMIT_SUCCESS_PASSBACK,//emit happened and some data is returned
        INTERRUPTED_EXCEPTION, NO_FETCHER_FOUND,
        INTERMEDIATE_RESULT;
    }

    public PipesResult(STATUS status) {
        this(status, null, null, false);
    }

    public PipesResult(STATUS status, EmitData emitData, boolean intermediate) {
        this(status, emitData, null, intermediate);
    }

    public PipesResult(STATUS status, String message) {
        this(status, null, message, false);
    }

}
