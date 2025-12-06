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
package org.apache.tika.pipes.core;

import org.apache.tika.pipes.api.PipesResult;

public class PipesResults {

    public static final PipesResult CLIENT_UNAVAILABLE_WITHIN_MS =
            new PipesResult(PipesResult.RESULT_STATUS.CLIENT_UNAVAILABLE_WITHIN_MS);
    public static final PipesResult TIMEOUT = new PipesResult(PipesResult.RESULT_STATUS.TIMEOUT);
    public static final PipesResult OOM = new PipesResult(PipesResult.RESULT_STATUS.OOM);
    public static final PipesResult UNSPECIFIED_CRASH = new PipesResult(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH);
    public static final PipesResult EMIT_SUCCESS = new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS);
    public static final PipesResult INTERRUPTED_EXCEPTION = new PipesResult(PipesResult.RESULT_STATUS.INTERRUPTED_EXCEPTION);
    public static final PipesResult EMPTY_OUTPUT = new PipesResult(PipesResult.RESULT_STATUS.EMPTY_OUTPUT);

}
