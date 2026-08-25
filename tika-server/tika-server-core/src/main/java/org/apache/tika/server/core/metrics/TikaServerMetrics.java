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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.eclipse.jetty.util.thread.ThreadPool;

import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.PipesWorkerPool;
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
    static final Duration[] DURATION_SLOS = {
            Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120)
    };

    /** Bucket boundaries for byte-size summaries: 1KB .. 1GB by decades. */
    private static final double[] SIZE_SLOS = {
            1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000
    };

    /**
     * Jetty passes any RFC-token method through to the filter, so the raw verb would let a
     * client mint meters without limit. Anything outside this set is tagged {@code other}.
     */
    private static final Set<String> KNOWN_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE", "CONNECT");

    static final String REQUESTS = "tika_server_requests";
    static final String REQUEST_SIZE = "tika_server_request_size_bytes";
    static final String RESPONSE_SIZE = "tika_server_response_size_bytes";
    static final String REJECTED = "tika_server_rejected_total";
    static final String TASKS_ACTIVE = "tika_server_tasks_active";
    static final String TASKS_STARTED = "tika_server_tasks_started_total";
    static final String PIPES_WORKERS = "tika_pipes_workers";
    static final String PIPES_RESTARTS = "tika_pipes_worker_restarts_total";
    static final String PIPES_QUEUE_DEPTH = "tika_pipes_queue_depth";

    /** Worker pools a tika-server can run at once; each has its own forks. */
    public static final String POOL_SYNC = "sync";
    public static final String POOL_ASYNC = "async";

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

    /**
     * Resources of this JVM only -- parses run in forked workers, so no binder here sees
     * them. Deliberately no GC or CPU binder: both describe a process that does not parse,
     * and a near-idle parent invites the false read that there is headroom. Container CPU
     * (which does cover the workers) belongs to cAdvisor/node-exporter.
     */
    public void bindJvm() {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        new FileDescriptorMetrics().bindTo(registry);
    }

    public void bindServerStatus(ServerStatus serverStatus) {
        Gauge.builder(TASKS_ACTIVE, serverStatus, ServerStatus::getNumTasks)
                .description("Parse/detect tasks currently running in this server")
                .register(registry);
        FunctionCounter.builder(TASKS_STARTED, serverStatus, ServerStatus::getFilesProcessed)
                .description("Parse/detect tasks started since server start")
                .register(registry);
    }

    /**
     * Restart counters for one worker pool. {@code pool} distinguishes the sync endpoints'
     * workers from {@code /async}'s: they are separate forks, and a server can run both.
     */
    public void bindPipes(String pool, PipesWorkerPool workerPool) {
        for (RestartReason reason : RestartReason.values()) {
            FunctionCounter.builder(PIPES_RESTARTS, workerPool, p -> p.getRestartCount(reason))
                    .tag("pool", pool)
                    .tag("reason", tagValue(reason.name()))
                    .description("Forked pipes worker restarts by reason")
                    .register(registry);
        }
    }

    /** Busy/idle needs a borrowable client queue, which only the sync pool has. */
    public void bindSyncWorkerStates(PipesParser pipesParser) {
        Gauge.builder(PIPES_WORKERS, pipesParser, p -> p.getNumClients() - p.getIdleClientCount())
                .tag("pool", POOL_SYNC)
                .tag("state", "busy")
                .description("Pipes worker slots by state")
                .register(registry);
        Gauge.builder(PIPES_WORKERS, pipesParser, PipesParser::getIdleClientCount)
                .tag("pool", POOL_SYNC)
                .tag("state", "idle")
                .description("Pipes worker slots by state")
                .register(registry);
    }

    public void bindAsyncQueue(AsyncResource asyncResource) {
        Gauge.builder(PIPES_QUEUE_DEPTH, asyncResource, AsyncResource::getQueueDepth)
                .description("Tuples waiting in the /async queue")
                .register(registry);
    }

    /** The CXF/Jetty request thread pool: queued jobs are HTTP-level backpressure. */
    public void bindJettyThreadPool(ThreadPool threadPool) {
        JettyServerThreadPoolMetrics poolMetrics =
                new JettyServerThreadPoolMetrics(threadPool, List.of());
        poolMetrics.bindTo(registry);
        closeables.add(poolMetrics);
    }

    void recordRequest(String endpoint, String method, int status, long nanos) {
        Timer.builder(REQUESTS)
                .tag("endpoint", endpoint)
                .tag("method", methodTag(method))
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

    static String methodTag(String method) {
        return method != null && KNOWN_METHODS.contains(method) ? method : "other";
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
