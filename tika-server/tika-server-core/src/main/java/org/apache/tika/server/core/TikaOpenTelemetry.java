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
package org.apache.tika.server.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton class for managing OpenTelemetry instrumentation in Tika Server.
 * <p>
 * This class provides centralized management of the OpenTelemetry SDK lifecycle,
 * including initialization, configuration, and graceful shutdown. It exposes
 * {@link Tracer} and {@link Meter} instances for creating custom spans and metrics
 * throughout the Tika Server codebase.
 * </p>
 * <p>
 * The class automatically detects when the OpenTelemetry Java agent is present
 * and defers to the agent's configuration in that case, enabling both manual
 * and auto-instrumentation to coexist harmoniously.
 * </p>
 * <p>
 * Usage example:
 * <pre>{@code
 * // Initialize during server startup
 * TikaOpenTelemetry.initialize(config);
 *
 * // Create custom spans
 * Tracer tracer = TikaOpenTelemetry.getTracer();
 * Span span = tracer.spanBuilder("custom.operation").startSpan();
 * try {
 *     // Your code here
 *     span.setStatus(StatusCode.OK);
 * } finally {
 *     span.end();
 * }
 *
 * // Shutdown during server teardown
 * TikaOpenTelemetry.shutdown();
 * }</pre>
 * </p>
 *
 * @since 4.0.0
 */
public class TikaOpenTelemetry {

    private static final Logger LOG = LoggerFactory.getLogger(TikaOpenTelemetry.class);
    
    /** Instrumentation library name for Tika Server */
    private static final String INSTRUMENTATION_NAME = "org.apache.tika.server";
    
    /** Instrumentation library version */
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private static volatile OpenTelemetry openTelemetry = null;
    private static volatile Tracer tracer = null;
    private static volatile Meter meter = null;
    private static volatile boolean initialized = false;
    private static volatile boolean enabled = false;
    private static volatile SdkTracerProvider tracerProvider = null;
    private static volatile SdkMeterProvider meterProvider = null;

    /**
     * Initializes OpenTelemetry with the provided configuration.
     * <p>
     * This method is thread-safe and will only initialize once. Subsequent calls
     * will log a warning and return immediately.
     * </p>
     * <p>
     * The initialization process:
     * <ol>
     *   <li>Checks if OpenTelemetry Java agent is already active (auto-instrumentation)</li>
     *   <li>If agent present, uses the agent's global instance</li>
     *   <li>Otherwise, creates and registers a new OpenTelemetry SDK instance</li>
     *   <li>Configures OTLP exporters for traces and metrics</li>
     *   <li>Sets up resource attributes (service name, version)</li>
     *   <li>Configures sampling based on provided probability</li>
     * </ol>
     * </p>
     * <p>
     * If configuration has {@code enabled=false}, OpenTelemetry remains disabled
     * and noop implementations are returned by {@link #getTracer()} and {@link #getMeter()}.
     * </p>
     *
     * @param config the OpenTelemetry configuration settings
     */
    public static synchronized void initialize(TikaOpenTelemetryConfig config) {
        if (initialized) {
            LOG.warn("OpenTelemetry already initialized, skipping");
            return;
        }

        if (!config.isEnabled()) {
            LOG.info("OpenTelemetry is disabled");
            initialized = true;
            enabled = false;
            return;
        }

        try {
            LOG.info("Initializing OpenTelemetry: {}", config);

            Resource resource = Resource.getDefault().merge(Resource.create(
                    Attributes.builder()
                            .put(AttributeKey.stringKey("service.name"), config.getServiceName())
                            .put(AttributeKey.stringKey("service.version"), INSTRUMENTATION_VERSION)
                            .build()));

            // Configure tracer provider
            OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.getOtlpEndpoint())
                    .setTimeout(Duration.ofMillis(config.getExportTimeoutMillis()))
                    .build();

            Sampler sampler = Sampler.traceIdRatioBased(config.getSamplingProbability());

            tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                    .setResource(resource)
                    .setSampler(sampler)
                    .build();

            // Configure meter provider
            OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.getOtlpEndpoint())
                    .setTimeout(Duration.ofMillis(config.getExportTimeoutMillis()))
                    .build();

            meterProvider = SdkMeterProvider.builder()
                    .registerMetricReader(
                            PeriodicMetricReader.builder(metricExporter)
                                    .setInterval(Duration.ofSeconds(60))
                                    .build())
                    .setResource(resource)
                    .build();

            // Build and register OpenTelemetry SDK globally
            // This may fail if the Java agent has already set the global instance
            try {
                openTelemetry = OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .setMeterProvider(meterProvider)
                        .buildAndRegisterGlobal();

                tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
                meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);

                LOG.info("OpenTelemetry initialized successfully");
            } catch (IllegalStateException e) {
                // Java agent has already set the global OpenTelemetry
                LOG.info("OpenTelemetry Java agent detected. Using agent's configuration (service.name from -Dotel.service.name)");
                
                // Use the agent's global instance
                OpenTelemetry agentOtel = GlobalOpenTelemetry.get();
                tracer = agentOtel.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
                meter = agentOtel.getMeter(INSTRUMENTATION_NAME);
                
                // Clean up our providers since we're using the agent's
                if (tracerProvider != null) {
                    tracerProvider.close();
                }
                if (meterProvider != null) {
                    meterProvider.close();
                }
                tracerProvider = null;
                meterProvider = null;
                openTelemetry = agentOtel;
            }

            initialized = true;
            enabled = true;

        } catch (Exception e) {
            LOG.error("Failed to initialize OpenTelemetry", e);
            initialized = true;
            enabled = false;
        }
    }

    /**
     * Returns the OpenTelemetry tracer instance for creating custom spans.
     * <p>
     * The tracer can be used to instrument custom operations and add
     * application-specific spans to traces. If OpenTelemetry is not enabled
     * or not initialized, returns a noop tracer that performs no operations.
     * </p>
     * <p>
     * Example usage:
     * <pre>{@code
     * Tracer tracer = TikaOpenTelemetry.getTracer();
     * Span span = tracer.spanBuilder("custom.operation")
     *     .setAttribute("key", "value")
     *     .startSpan();
     * try (Scope scope = span.makeCurrent()) {
     *     // Your instrumented code
     *     span.setStatus(StatusCode.OK);
     * } finally {
     *     span.end();
     * }
     * }</pre>
     * </p>
     *
     * @return Tracer instance for creating spans, never null
     */
    public static Tracer getTracer() {
        if (!enabled || tracer == null) {
            return OpenTelemetry.noop().getTracer(INSTRUMENTATION_NAME);
        }
        return tracer;
    }

    /**
     * Returns the OpenTelemetry meter instance for recording metrics.
     * <p>
     * The meter can be used to create counters, gauges, and histograms for
     * recording custom metrics. If OpenTelemetry is not enabled or not
     * initialized, returns a noop meter that performs no operations.
     * </p>
     * <p>
     * Example usage:
     * <pre>{@code
     * Meter meter = TikaOpenTelemetry.getMeter();
     * LongCounter counter = meter.counterBuilder("parse.count")
     *     .setDescription("Number of documents parsed")
     *     .build();
     * counter.add(1, Attributes.builder()
     *     .put("content_type", "application/pdf")
     *     .build());
     * }</pre>
     * </p>
     *
     * @return Meter instance for recording metrics, never null
     */
    public static Meter getMeter() {
        if (!enabled || meter == null) {
            return OpenTelemetry.noop().getMeter(INSTRUMENTATION_NAME);
        }
        return meter;
    }

    /**
     * Checks if OpenTelemetry instrumentation is enabled and active.
     * <p>
     * This method can be used to conditionally create spans or metrics only
     * when OpenTelemetry is active, though it's generally not necessary as
     * {@link #getTracer()} and {@link #getMeter()} return noop implementations
     * when disabled.
     * </p>
     *
     * @return true if OpenTelemetry is enabled and initialized, false otherwise
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Gracefully shuts down the OpenTelemetry SDK and flushes any pending telemetry data.
     * <p>
     * This method should be called during application shutdown to ensure all
     * traces and metrics are properly exported before the process terminates.
     * The shutdown process:
     * <ol>
     *   <li>Stops accepting new spans and metrics</li>
     *   <li>Flushes all pending data to the configured exporter</li>
     *   <li>Waits up to 10 seconds for export to complete</li>
     *   <li>Releases all resources</li>
     * </ol>
     * </p>
     * <p>
     * This method is thread-safe and idempotent. If OpenTelemetry is not initialized
     * or already shutdown, this method returns immediately without error.
     * </p>
     * <p>
     * <strong>Note:</strong> If the OpenTelemetry Java agent is in use, this method
     * will not shut down the agent's SDK, only the manually created providers (if any).
     * </p>
     */
    public static synchronized void shutdown() {
        if (!initialized || !enabled) {
            return;
        }

        LOG.info("Shutting down OpenTelemetry");

        try {
            if (tracerProvider != null) {
                tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
            }
            if (meterProvider != null) {
                meterProvider.shutdown().join(10, TimeUnit.SECONDS);
            }
            LOG.info("OpenTelemetry shut down successfully");
        } catch (Exception e) {
            LOG.error("Error shutting down OpenTelemetry", e);
        } finally {
            enabled = false;
        }
    }

    /**
     * Get the OpenTelemetry instance.
     *
     * @return OpenTelemetry instance
     */
    public static OpenTelemetry getOpenTelemetry() {
        if (!enabled || openTelemetry == null) {
            return OpenTelemetry.noop();
        }
        return openTelemetry;
    }
}
