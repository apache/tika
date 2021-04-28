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
package org.apache.tika.pipes.async;

import org.apache.tika.pipes.emitter.EmitData;

public class AsyncResult {

    enum STATUS {
        OK, OOM, TIMEOUT, UNSPECIFIED_CRASH
    }
    public static AsyncResult TIMEOUT = new AsyncResult(STATUS.TIMEOUT);
    public static AsyncResult OOM = new AsyncResult(STATUS.OOM);
    public static AsyncResult UNSPECIFIED_CRASH = new AsyncResult(STATUS.UNSPECIFIED_CRASH);

    private final STATUS status;
    private final EmitData emitData;

    private AsyncResult(STATUS status, EmitData emitData) {
        this.status = status;
        this.emitData = emitData;
    }

    public AsyncResult(STATUS status) {
        this(status, null);
    }

    public AsyncResult(EmitData emitData) {
        this(STATUS.OK, emitData);
    }

    public STATUS getStatus() {
        return status;
    }

    public EmitData getEmitData() {
        return emitData;
    }
}
