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

package org.apache.tika.server.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.tika.server.ServerStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Server status metrics meter binder.
 */
public class ServerStatusMetrics implements MeterBinder {

    /**
     * The server status currently in use.
     */
    private ServerStatus serverStatus;

    /**
     * Initializes server status metrics with the server status object.
     * @param serverStatus the server status.
     */
    public ServerStatusMetrics(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    /**
     * Binds server status metrics to meter registry.
     * @param meterRegistry the meter registry to bind to.
     */
    @Override
    public void bindTo(@NotNull MeterRegistry meterRegistry) {
        Gauge.builder("server.status.lastparsed", serverStatus, ServerStatus::getMillisSinceLastParseStarted)
                .description("Last parsed in milliseconds")
                .register(meterRegistry);
        Gauge.builder("server.status.restarts", serverStatus, ServerStatus::getNumRestarts)
                .description("Last parsed in milliseconds")
                .register(meterRegistry);
        Gauge.builder("server.status.files", serverStatus, ServerStatus::getFilesProcessed)
                .description("Last parsed in milliseconds")
                .register(meterRegistry);
    }

}
