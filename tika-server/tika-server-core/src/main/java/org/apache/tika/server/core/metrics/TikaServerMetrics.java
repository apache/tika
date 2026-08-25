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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.RestartReason;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.server.core.ServerStatus;

/**
 * Owns the process's {@link PrometheusMeterRegistry} and every meter recorded against it.
 * Every tag value comes from a bounded set; nothing request-derived becomes a tag.
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

    /**
     * Jetty passes any RFC-token method through to the filter, so the raw verb would let a
     * client mint meters without limit. Anything outside this set is tagged {@code other}.
     */
    private static final Set<String> KNOWN_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE", "CONNECT");

    static final String REQUESTS = "tika_server_requests";
    static final String REQUEST_SIZE = "tika_server_request_size_bytes";
    static final String REJECTED = "tika_server_rejected_total";
    static final String TASKS_ACTIVE = "tika_server_tasks_active";
    static final String PIPES_WORKERS = "tika_pipes_workers";
    static final String PIPES_RESTARTS = "tika_pipes_worker_restarts_total";
    static final String PIPES_QUEUE_DEPTH = "tika_pipes_queue_depth";

    static final String TAG_ENDPOINT = "endpoint";
    static final String TAG_METHOD = "method";
    static final String TAG_STATUS = "status";
    static final String TAG_REASON = "reason";
    static final String TAG_POOL = "pool";
    static final String TAG_STATE = "state";

    /** Worker pools a tika-server can run at once; each has its own forks. */
    static final String POOL_SYNC = "sync";
    static final String POOL_ASYNC = "async";

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    MeterRegistry getRegistry() {
        return registry;
    }

    /** Prometheus text exposition (format 0.0.4). */
    public String scrape() {
        return registry.scrape();
    }

    /** No GC/CPU binders: this JVM does not parse, so they would suggest headroom that is not there. */
    public void bindJvm() {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        new FileDescriptorMetrics().bindTo(registry);
    }

    public void bindServerStatus(ServerStatus serverStatus) {
        Gauge.builder(TASKS_ACTIVE, serverStatus, ServerStatus::getNumTasks)
                .description("Sync parse/detect tasks currently running in this server")
                .register(registry);
    }

    /** The sync endpoints' forks: restart counters plus busy/idle slots. */
    public void bindSyncPool(PipesParser pipesParser) {
        bindRestarts(POOL_SYNC, pipesParser::getRestartCount);
        Gauge.builder(PIPES_WORKERS, pipesParser, p -> p.getNumClients() - p.getIdleClientCount())
                .tag(TAG_POOL, POOL_SYNC)
                .tag(TAG_STATE, "busy")
                .description("Pipes worker slots by state")
                .register(registry);
        Gauge.builder(PIPES_WORKERS, pipesParser, PipesParser::getIdleClientCount)
                .tag(TAG_POOL, POOL_SYNC)
                .tag(TAG_STATE, "idle")
                .description("Pipes worker slots by state")
                .register(registry);
    }

    /** {@code /async}'s own forks: restart counters plus queue depth. */
    public void bindAsyncPool(AsyncProcessor asyncProcessor) {
        bindRestarts(POOL_ASYNC, asyncProcessor::getRestartCount);
        Gauge.builder(PIPES_QUEUE_DEPTH, asyncProcessor, AsyncProcessor::getQueueDepth)
                .tag(TAG_POOL, POOL_ASYNC)
                .description("Tuples waiting in the /async queue")
                .register(registry);
    }

    private void bindRestarts(String pool, ToLongFunction<RestartReason> restartCount) {
        for (RestartReason reason : RestartReason.values()) {
            FunctionCounter.builder(PIPES_RESTARTS, reason, restartCount::applyAsLong)
                    .tag(TAG_POOL, pool)
                    .tag(TAG_REASON, reason.name().toLowerCase(Locale.ROOT))
                    .description("Forked pipes worker restarts by reason")
                    .register(registry);
        }
    }

    void recordRequest(String endpoint, String method, int status, long nanos) {
        Timer.builder(REQUESTS)
                .tag(TAG_ENDPOINT, endpoint)
                .tag(TAG_METHOD, methodTag(method))
                .tag(TAG_STATUS, statusClass(status))
                .description("HTTP requests handled by the parse-side listener")
                .serviceLevelObjectives(DURATION_SLOS)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        String rejected = rejectedReason(status);
        if (rejected != null) {
            Counter.builder(REJECTED)
                    .tag(TAG_REASON, rejected)
                    .description("Requests refused for capacity reasons")
                    .register(registry)
                    .increment();
        }
    }

    void recordRequestSize(String endpoint, long bytes) {
        DistributionSummary.builder(REQUEST_SIZE)
                .tag(TAG_ENDPOINT, endpoint)
                .baseUnit("bytes")
                .description("Request body bytes (Content-Length)")
                .serviceLevelObjectives(SIZE_SLOS)
                .register(registry)
                .record(bytes);
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

    /** By status, as tika-server already maps them: 429 capacity, 503 fork OOM/timeout/crash, 413 body limit. */
    static String rejectedReason(int status) {
        return switch (status) {
            case 429 -> "busy_429";
            case 413 -> "payload_413";
            case 503 -> "crash_503";
            default -> null;
        };
    }

    @Override
    public void close() {
        registry.close();
    }
}
