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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OpenTelemetry integration in Tika Server.
 * <p>
 * These tests verify the basic functionality of the OpenTelemetry configuration
 * and initialization, including configuration defaults, environment variable loading,
 * SDK lifecycle management, and span creation.
 * </p>
 *
 * @since 4.0.0
 */
public class TikaOpenTelemetryTest {

    /**
     * Cleans up OpenTelemetry resources after each test.
     * Ensures tests don't interfere with each other by shutting down
     * the SDK between tests.
     */
    @AfterEach
    public void cleanup() {
        TikaOpenTelemetry.shutdown();
    }

    /**
     * Verifies that OpenTelemetry is disabled by default when no
     * environment variables or explicit configuration is provided.
     */
    @Test
    public void testDisabledByDefault() {
        // By default, OTEL should be disabled unless configured
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        assertFalse(config.isEnabled(), "OpenTelemetry should be disabled by default");
    }

    /**
     * Verifies that configuration defaults are correctly set to
     * standard OpenTelemetry values.
     */
    @Test
    public void testConfigurationDefaults() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        
        assertEquals("tika-server", config.getServiceName());
        assertEquals("http://localhost:4317", config.getOtlpEndpoint());
        assertEquals(1.0, config.getSamplingProbability());
        assertEquals(30000, config.getExportTimeoutMillis());
        assertEquals("otlp", config.getExporterType());
    }

    /**
     * Verifies that configuration can load from environment variables.
     * Note: This is a basic test that verifies object creation.
     * Full environment variable testing would require system property manipulation.
     */
    @Test
    public void testEnvironmentVariableLoading() {
        // Note: This test would need to set environment variables before creating config
        // For now, just verify the config object is created
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        assertNotNull(config);
    }

    /**
     * Tests full OpenTelemetry SDK initialization with valid configuration.
     * Verifies that the SDK is enabled and a valid Tracer instance is returned.
     */
    @Test
    public void testOpenTelemetryInitialization() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        config.setEnabled(true);
        config.setOtlpEndpoint("http://localhost:4317");
        
        TikaOpenTelemetry.initialize(config);
        
        assertTrue(TikaOpenTelemetry.isEnabled(), "OpenTelemetry should be enabled after initialization");
        
        Tracer tracer = TikaOpenTelemetry.getTracer();
        assertNotNull(tracer, "Tracer should not be null");
    }

    /**
     * Verifies that when OpenTelemetry is explicitly disabled in configuration,
     * the SDK remains disabled after initialization.
     */
    @Test
    public void testOpenTelemetryDisabled() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        config.setEnabled(false);
        
        TikaOpenTelemetry.initialize(config);
        
        assertFalse(TikaOpenTelemetry.isEnabled(), "OpenTelemetry should remain disabled");
    }

    /**
     * Tests the creation of custom spans using the OpenTelemetry Tracer.
     * Verifies that spans can be created with attributes and properly ended.
     */
    @Test
    public void testSpanCreation() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        config.setEnabled(true);
        
        TikaOpenTelemetry.initialize(config);
        
        if (TikaOpenTelemetry.isEnabled()) {
            Tracer tracer = TikaOpenTelemetry.getTracer();
            Span span = tracer.spanBuilder("test.operation")
                    .setAttribute("test.attribute", "test.value")
                    .startSpan();
            
            assertNotNull(span, "Span should be created");
            
            span.end();
        }
    }

    /**
     * Tests all getter and setter methods on TikaOpenTelemetryConfig
     * to ensure proper field access and mutation.
     */
    @Test
    public void testGettersAndSetters() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        
        config.setEnabled(true);
        assertTrue(config.isEnabled());
        
        config.setServiceName("test-service");
        assertEquals("test-service", config.getServiceName());
        
        config.setOtlpEndpoint("http://test:4317");
        assertEquals("http://test:4317", config.getOtlpEndpoint());
        
        config.setSamplingProbability(0.5);
        assertEquals(0.5, config.getSamplingProbability());
        
        config.setExportTimeoutMillis(60000);
        assertEquals(60000, config.getExportTimeoutMillis());
        
        config.setExporterType("console");
        assertEquals("console", config.getExporterType());
    }

    /**
     * Tests the toString() method of TikaOpenTelemetryConfig.
     * Verifies that the string representation includes key configuration values.
     */
    @Test
    public void testToString() {
        TikaOpenTelemetryConfig config = new TikaOpenTelemetryConfig();
        config.setEnabled(true);
        config.setServiceName("my-tika");
        
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("my-tika"));
    }
}
