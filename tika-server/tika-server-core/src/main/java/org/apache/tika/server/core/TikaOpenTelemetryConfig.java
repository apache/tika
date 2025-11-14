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

import java.util.Map;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for OpenTelemetry instrumentation in Tika Server.
 * <p>
 * This class encapsulates all OpenTelemetry-related configuration settings,
 * including enabling/disabling instrumentation, OTLP endpoint configuration,
 * service identification, and sampling policies.
 * </p>
 * <p>
 * Configuration can be loaded from:
 * <ul>
 *   <li>Environment variables (takes precedence)</li>
 *   <li>XML configuration via TikaServerConfig</li>
 * </ul>
 * </p>
 * <p>
 * Example XML configuration:
 * <pre>{@code
 * <openTelemetry>
 *   <enabled>true</enabled>
 *   <exporterType>otlp</exporterType>
 *   <otlpEndpoint>http://localhost:4317</otlpEndpoint>
 *   <serviceName>tika-server</serviceName>
 *   <samplingProbability>1.0</samplingProbability>
 *   <exportTimeoutMillis>30000</exportTimeoutMillis>
 * </openTelemetry>
 * }</pre>
 * </p>
 *
 * @since 4.0.0
 */
public class TikaOpenTelemetryConfig implements Initializable {

    /** Environment variable to disable the OpenTelemetry SDK */
    public static final String OTEL_SDK_DISABLED_ENV = "OTEL_SDK_DISABLED";
    
    /** Environment variable for the OTLP exporter endpoint URL */
    public static final String OTEL_EXPORTER_OTLP_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT";
    
    /** Environment variable for the service name identifier */
    public static final String OTEL_SERVICE_NAME_ENV = "OTEL_SERVICE_NAME";
    
    /** Environment variable for the trace sampling strategy */
    public static final String OTEL_TRACES_SAMPLER_ENV = "OTEL_TRACES_SAMPLER";
    
    /** Environment variable for the trace sampling probability */
    public static final String OTEL_TRACES_SAMPLER_ARG_ENV = "OTEL_TRACES_SAMPLER_ARG";

    private static final String DEFAULT_SERVICE_NAME = "tika-server";
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";
    private static final double DEFAULT_SAMPLING_PROBABILITY = 1.0;
    
    private boolean enabled = false;
    private String exporterType = "otlp";
    private String otlpEndpoint = DEFAULT_OTLP_ENDPOINT;
    private String serviceName = DEFAULT_SERVICE_NAME;
    private double samplingProbability = DEFAULT_SAMPLING_PROBABILITY;
    private int exportTimeoutMillis = 30000;

    /**
     * Creates a new OpenTelemetry configuration instance.
     * Automatically loads configuration from environment variables.
     */
    public TikaOpenTelemetryConfig() {
        loadFromEnvironment();
    }

    /**
     * Loads configuration from standard OpenTelemetry environment variables.
     * This method is called automatically during construction.
     * <p>
     * Recognized environment variables:
     * <ul>
     *   <li>{@code OTEL_SDK_DISABLED}: Set to "true" to disable OpenTelemetry</li>
     *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT}: OTLP endpoint URL (enables OpenTelemetry if set)</li>
     *   <li>{@code OTEL_SERVICE_NAME}: Service name for identifying this Tika instance</li>
     *   <li>{@code OTEL_TRACES_SAMPLER_ARG}: Sampling probability (0.0-1.0)</li>
     * </ul>
     * </p>
     */
    private void loadFromEnvironment() {
        String sdkDisabled = System.getenv(OTEL_SDK_DISABLED_ENV);
        if ("true".equalsIgnoreCase(sdkDisabled)) {
            this.enabled = false;
        }

        String endpoint = System.getenv(OTEL_EXPORTER_OTLP_ENDPOINT_ENV);
        if (!StringUtils.isBlank(endpoint)) {
            this.otlpEndpoint = endpoint;
            if (sdkDisabled == null) {
                this.enabled = true;
            }
        }

        String serviceName = System.getenv(OTEL_SERVICE_NAME_ENV);
        if (!StringUtils.isBlank(serviceName)) {
            this.serviceName = serviceName;
        }

        String samplerArg = System.getenv(OTEL_TRACES_SAMPLER_ARG_ENV);
        if (!StringUtils.isBlank(samplerArg)) {
            try {
                this.samplingProbability = Double.parseDouble(samplerArg);
            } catch (NumberFormatException e) {
                // Keep default
            }
        }
    }

    /**
     * Returns whether OpenTelemetry instrumentation is enabled.
     *
     * @return true if OpenTelemetry is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether OpenTelemetry instrumentation is enabled.
     *
     * @param enabled true to enable OpenTelemetry, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the type of exporter to use for telemetry data.
     *
     * @return the exporter type (currently only "otlp" is supported)
     */
    public String getExporterType() {
        return exporterType;
    }

    /**
     * Sets the type of exporter to use for telemetry data.
     *
     * @param exporterType the exporter type (currently only "otlp" is supported)
     */
    public void setExporterType(String exporterType) {
        this.exporterType = exporterType;
    }

    /**
     * Returns the OTLP endpoint URL for exporting telemetry data.
     *
     * @return the OTLP endpoint URL (e.g., "http://localhost:4317")
     */
    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    /**
     * Sets the OTLP endpoint URL for exporting telemetry data.
     * <p>
     * Port reference:
     * <ul>
     *   <li>4317: gRPC protocol (default for manual instrumentation)</li>
     *   <li>4318: HTTP protocol (default for Java agent)</li>
     * </ul>
     * </p>
     *
     * @param otlpEndpoint the OTLP endpoint URL
     */
    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    /**
     * Returns the service name used to identify this Tika Server instance.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the service name used to identify this Tika Server instance.
     * This name appears in traces and helps identify different Tika deployments.
     *
     * @param serviceName the service name
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Returns the trace sampling probability.
     *
     * @return the sampling probability (0.0 = sample nothing, 1.0 = sample everything)
     */
    public double getSamplingProbability() {
        return samplingProbability;
    }

    /**
     * Sets the trace sampling probability.
     * This determines what fraction of traces are sampled and exported.
     *
     * @param samplingProbability the sampling probability (must be between 0.0 and 1.0)
     */
    public void setSamplingProbability(double samplingProbability) {
        this.samplingProbability = samplingProbability;
    }

    /**
     * Returns the timeout in milliseconds for exporting telemetry data.
     *
     * @return the export timeout in milliseconds
     */
    public int getExportTimeoutMillis() {
        return exportTimeoutMillis;
    }

    /**
     * Sets the timeout in milliseconds for exporting telemetry data.
     *
     * @param exportTimeoutMillis the export timeout in milliseconds
     */
    public void setExportTimeoutMillis(int exportTimeoutMillis) {
        this.exportTimeoutMillis = exportTimeoutMillis;
    }

    @Override
    public String toString() {
        return "TikaOpenTelemetryConfig{enabled=" + enabled +
                ", exporterType=" + exporterType +
                ", otlpEndpoint=" + otlpEndpoint +
                ", serviceName=" + serviceName +
                ", samplingProbability=" + samplingProbability +
                ", exportTimeoutMillis=" + exportTimeoutMillis + "}";
    }

    /**
     * Initializes the configuration after all fields have been set.
     * This method is called by the Tika configuration framework after
     * all setter methods have been invoked.
     * <p>
     * For this configuration class, no additional initialization is required
     * as all fields are directly set via setters.
     * </p>
     *
     * @param params configuration parameters (not used in this implementation)
     * @throws TikaConfigException if initialization fails
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        // All fields are set via setters by ConfigBase before this is called
        // No additional initialization needed
    }

    /**
     * Validates the configuration settings.
     * This method is called by the Tika configuration framework to ensure
     * all configuration values are valid and consistent.
     *
     * @param problemHandler handler for reporting configuration problems
     * @throws TikaConfigException if the configuration is invalid
     */
    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) 
            throws TikaConfigException {
        // Validate configuration if needed
        if (enabled && StringUtils.isBlank(otlpEndpoint)) {
            throw new TikaConfigException(
                    "OpenTelemetry is enabled but otlpEndpoint is not configured");
        }
        if (samplingProbability < 0.0 || samplingProbability > 1.0) {
            throw new TikaConfigException(
                    "samplingProbability must be between 0.0 and 1.0, got: " + samplingProbability);
        }
    }
}
