package org.apache.tika.server.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.tika.server.ServerStatus;
import org.jetbrains.annotations.NotNull;

public class ServerStatusMetrics implements MeterBinder {

    private ServerStatus serverStatus;

    public ServerStatusMetrics(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

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
