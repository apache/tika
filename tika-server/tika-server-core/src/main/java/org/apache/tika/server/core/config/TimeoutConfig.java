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
package org.apache.tika.server.core.config;

import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.core.ParseContextConfig;

public class TimeoutConfig implements ParseContextConfig {

    public static final String X_TIKA_TIMEOUT_MILLIS = "X-Tika-Timeout-Millis";

    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders, Metadata metadata, ParseContext context) {
        if (httpHeaders.containsKey(X_TIKA_TIMEOUT_MILLIS)) {
            long timeout = Long.parseLong(httpHeaders.getFirst(X_TIKA_TIMEOUT_MILLIS));
            context.set(TikaTaskTimeout.class, new TikaTaskTimeout(timeout));
        }
    }
}
