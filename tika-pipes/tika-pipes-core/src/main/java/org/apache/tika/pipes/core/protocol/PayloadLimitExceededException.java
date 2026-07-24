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
package org.apache.tika.pipes.core.protocol;

import java.io.IOException;

/**
 * Thrown when an incoming IPC payload's declared length exceeds the configured limit
 * (see {@link org.apache.tika.pipes.core.PipesConfig#getMaxIpcPayloadBytes()};
 * default {@link PipesMessage#MAX_PAYLOAD_BYTES}). The payload bytes were not consumed,
 * so the stream is desynchronized and the connection must be closed. With a shared server
 * the process keeps running (only this connection ends); with the default per-client forked
 * server the process may still exit on the failed write, and the client reconnects on the
 * next task.
 */
public class PayloadLimitExceededException extends IOException {
    public PayloadLimitExceededException(String message) {
        super(message);
    }
}
