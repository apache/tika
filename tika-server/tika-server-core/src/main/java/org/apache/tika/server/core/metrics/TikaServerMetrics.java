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
package org.apache.tika.server.core.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.eclipse.jetty.util.thread.ThreadPool;

import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.RestartReason;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.resource.AsyncResource;

/**
 * Owns the one {@link PrometheusMeterRegistry} for a tika-server process and every meter
 * recorded against it. Constructed only when metrics are enabled; callers hold null
 * otherwise, so no meter exists when the feature is off.
 * <p>
 * Every tag value is drawn from a bounded set (resource root, status class, enum names):
 * nothing request-derived ever becomes a tag.
 */
public final class TikaServerMetrics implements AutoCloseable {

    /**
     * One bucket set for every duration timer. Twelve explicit boundaries instead of
     * Micrometer's percentile histogram, which would emit roughly 70 per series.
     */
    public static final Duration[] DURATION_SLOS = {
            Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120)
    };

    /** Bucket boundaries for byte-size summaries: 1KB .. 1GB by decades. */
    private static final double[] SIZE_SLOS = {
            1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000
    };

    public static final String REQUESTS = "tika_server_requests";
    public static final String REQUEST_SIZE = "tika_server_request_size_bytes";
    public static final String RESPONSE_SIZE = "tika_server_response_size_bytes";
    public static final String REJECTED = "tika_server_rejected_total";
    public static final String TASKS_ACTIVE = "tika_server_tasks_active";
    public static final String TASKS_STARTED = "tika_server_tasks_started_total";
    public static final String PIPES_WORKERS = "tika_pipes_workers";
    public static final String PIPES_RESTARTS = "tika_pipes_worker_restarts_total";
    public static final String PIPES_QUEUE_DEPTH = "tika_pipes_queue_depth";

    private final PrometheusMeterRegistry registry;
    private final List<AutoCloseable> closeables = new ArrayList<>();

    public TikaServerMetrics(MetricsConfig config) {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        for (Map.Entry<String, String> e : config.getCommonTags().entrySet()) {
            registry.config().commonTags(e.getKey(), e.getValue());
        }
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    /** Prometheus text exposition (format 0.0.4). */
    public String scrape() {
        return registry.scrape();
    }

    public void bindJvm() {
        JvmGcMetrics gc = new JvmGcMetrics();
        gc.bindTo(registry);
        closeables.add(gc);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        new FileDescriptorMetrics().bindTo(registry);
    }

    public void bindServerStatus(ServerStatus serverStatus) {
        Gauge.builder(TASKS_ACTIVE, serverStatus, s -> s.getTasks().size())
                .description("Parse/detect tasks currently running in this server")
                .register(registry);
        FunctionCounter.builder(TASKS_STARTED, serverStatus, ServerStatus::getFilesProcessed)
                .description("Parse/detect tasks started since server start")
                .register(registry);
    }

    public void bindPipes(PipesParser pipesParser) {
        Gauge.builder(PIPES_WORKERS, pipesParser, p -> p.getNumClients() - p.getIdleClientCount())
                .tag("state", "busy")
                .description("Forked pipes workers by state")
                .register(registry);
        Gauge.builder(PIPES_WORKERS, pipesParser, PipesParser::getIdleClientCount)
                .tag("state", "idle")
                .description("Forked pipes workers by state")
                .register(registry);
        for (RestartReason reason : RestartReason.values()) {
            FunctionCounter.builder(PIPES_RESTARTS, pipesParser, p -> p.getRestartCount(reason))
                    .tag("reason", tagValue(reason.name()))
                    .description("Forked pipes worker restarts by reason")
                    .register(registry);
        }
    }

    public void bindAsyncQueue(AsyncResource asyncResource) {
        Gauge.builder(PIPES_QUEUE_DEPTH, asyncResource, AsyncResource::getQueueDepth)
                .description("Tuples waiting in the /async queue")
                .register(registry);
    }

    /** The CXF/Jetty request thread pool: queued jobs are HTTP-level backpressure. */
    public void bindJettyThreadPool(ThreadPool threadPool) {
        new io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics(
                threadPool, List.of()).bindTo(registry);
    }

    void recordRequest(String endpoint, String method, int status, long nanos) {
        Timer.builder(REQUESTS)
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", statusClass(status))
                .description("HTTP requests handled by the parse-side listener")
                .serviceLevelObjectives(DURATION_SLOS)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        String rejected = rejectedReason(status);
        if (rejected != null) {
            Counter.builder(REJECTED)
                    .tag("reason", rejected)
                    .description("Requests refused for capacity reasons")
                    .register(registry)
                    .increment();
        }
    }

    void recordRequestSize(String endpoint, long bytes) {
        sizeSummary(REQUEST_SIZE, endpoint, "Request body bytes (Content-Length)")
                .record(bytes);
    }

    void recordResponseSize(String endpoint, long bytes) {
        sizeSummary(RESPONSE_SIZE, endpoint, "Response entity bytes before compression")
                .record(bytes);
    }

    private DistributionSummary sizeSummary(String name, String endpoint, String description) {
        return DistributionSummary.builder(name)
                .tag("endpoint", endpoint)
                .baseUnit("bytes")
                .description(description)
                .serviceLevelObjectives(SIZE_SLOS)
                .register(registry);
    }

    static String statusClass(int status) {
        if (status < 200 || status >= 600) {
            return "other";
        }
        return (status / 100) + "xx";
    }

    /**
     * By status only, which is what tika-server's mapping already encodes: 429 is
     * capacity (the /async queue full, or no free fork within maxWaitForClientMillis),
     * 503 is a fork that OOM'd, timed out or crashed, 413 is an over-limit body.
     */
    static String rejectedReason(int status) {
        return switch (status) {
            case 429 -> "busy_429";
            case 413 -> "payload_413";
            case 503 -> "crash_503";
            default -> null;
        };
    }

    static String tagValue(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        for (AutoCloseable c : closeables) {
            try {
                c.close();
            } catch (Exception ignore) {
                // best-effort teardown of JVM binders
            }
        }
        registry.close();
    }
}
