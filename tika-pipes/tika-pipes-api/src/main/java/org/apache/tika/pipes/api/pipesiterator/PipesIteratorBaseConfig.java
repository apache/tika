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
package org.apache.tika.pipes.api.pipesiterator;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;


public record PipesIteratorBaseConfig(String fetcherId, String emitterId, HandlerConfig handlerConfig,
                                      FetchEmitTuple.ON_PARSE_EXCEPTION onParseException, long maxWaitMs, int queueSize) {

    public static final HandlerConfig DEFAULT_HANDLER_CONFIG = new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, HandlerConfig.PARSE_MODE.RMETA,
            -1, -1, true);
    private static final FetchEmitTuple.ON_PARSE_EXCEPTION DEFAULT_ON_PARSE_EXCEPTION = FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
    private static final long DEFAULT_MAX_WAIT_MS = 600_000;
    private static final int DEFAULT_QUEUE_SIZE = 10000;

    public PipesIteratorBaseConfig(String fetcherId, String emitterId) {
        this(fetcherId, emitterId, DEFAULT_HANDLER_CONFIG, DEFAULT_ON_PARSE_EXCEPTION, DEFAULT_MAX_WAIT_MS, DEFAULT_QUEUE_SIZE);
    }

}
