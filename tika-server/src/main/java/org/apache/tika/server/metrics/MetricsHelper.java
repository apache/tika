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

import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.Log4j2Metrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.metrics.MetricsFeature;
import org.apache.cxf.metrics.MetricsProvider;
import org.apache.cxf.metrics.micrometer.MicrometerMetricsProperties;
import org.apache.cxf.metrics.micrometer.MicrometerMetricsProvider;
import org.apache.cxf.metrics.micrometer.provider.DefaultExceptionClassProvider;
import org.apache.cxf.metrics.micrometer.provider.DefaultTimedAnnotationProvider;
import org.apache.cxf.metrics.micrometer.provider.StandardTags;
import org.apache.cxf.metrics.micrometer.provider.StandardTagsProvider;
import org.apache.cxf.metrics.micrometer.provider.TagsCustomizer;
import org.apache.cxf.metrics.micrometer.provider.TagsProvider;
import org.apache.cxf.metrics.micrometer.provider.jaxrs.JaxrsOperationTagsCustomizer;
import org.apache.cxf.metrics.micrometer.provider.jaxrs.JaxrsTags;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.apache.tika.server.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Helps setup and configure metrics.
 */
public class MetricsHelper {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MetricsHelper.class);

    /**
     * Map of Meter registry class to object/instance in use.
     */
    private static final Map<Class<? extends MeterRegistry>, MeterRegistry> REGISTRY_MAP
            = Collections.synchronizedMap(new HashMap<>());

    /**
     * The composite registry in use.
     */
    private static final CompositeMeterRegistry REGISTRY = Metrics.globalRegistry;

    /**
     * Gets the sub registry from root registry.
     * @param tClass the class to get registry of.
     * @param <T> the registry type.
     * @return the registry instance.
     */
    public static <T extends MeterRegistry> T getRegistry(Class<T> tClass) {
        return tClass.cast(REGISTRY_MAP.get(tClass));
    }

    /**
     * Initialized metrics.
     * @param sf the server factory bean.
     */
    public static void initMetrics(JAXRSServerFactoryBean sf) {
        final JaxrsTags jaxrsTags = new JaxrsTags();
        final TagsCustomizer operationsCustomizer = new JaxrsOperationTagsCustomizer(jaxrsTags);

        final TagsProvider tagsProvider = new StandardTagsProvider(new DefaultExceptionClassProvider(),
                new StandardTags());

        final MicrometerMetricsProperties properties = new MicrometerMetricsProperties();
        properties.setServerRequestsMetricName("http.server.requests");
        properties.setServerRequestsMetricName("http.client.requests");
        properties.setAutoTimeRequests(true);

        final MetricsProvider metricsProvider = new MicrometerMetricsProvider(REGISTRY, tagsProvider,
                Collections.singletonList(operationsCustomizer),
                new DefaultTimedAnnotationProvider(), properties);

        setUpRegistries();

        sf.setFeatures(Collections.singletonList(new MetricsFeature(metricsProvider)));
    }

    /**
     * Sets up registries.
     */
    private static void setUpRegistries() {
        REGISTRY.config().commonTags("application", "tika-server");

        PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        REGISTRY_MAP.put(PrometheusMeterRegistry.class, prometheusMeterRegistry);
        REGISTRY.add(prometheusMeterRegistry);

        JmxMeterRegistry jmxMeterRegistry = new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);
        REGISTRY_MAP.put(JmxMeterRegistry.class, jmxMeterRegistry);
        REGISTRY.add(jmxMeterRegistry);
    }

    /**
     * Register meters before start.
     * @param serverStatus the server status object.
     * @param enableStatus whether to enable server status metrics.
     */
    public static void registerPreStart(ServerStatus serverStatus,
                                        boolean enableStatus) {

        if (enableStatus) {
            setUpServerStatusMetrics(serverStatus);
        }
        setUpJvmMetrics();
        setUpSystemMetrics();
        setUpLoggingMetrics();
        setUpExtraMetrics();
    }

    /**
     * Sets up server status metrics.
     * @param serverStatus the server status.
     */
    private static void setUpServerStatusMetrics(ServerStatus serverStatus) {
        new ServerStatusMetrics(serverStatus).bindTo(REGISTRY);
    }

    /**
     * Sets up jvm metrics.
     */
    private static void setUpJvmMetrics() {
        new ClassLoaderMetrics().bindTo(REGISTRY);
        new JvmMemoryMetrics().bindTo(REGISTRY);
        new JvmGcMetrics().bindTo(REGISTRY);
        new JvmThreadMetrics().bindTo(REGISTRY);
    }

    /**
     * Sets up system level metrics.
     */
    private static void setUpSystemMetrics() {
        new ProcessorMetrics().bindTo(REGISTRY);
        new FileDescriptorMetrics().bindTo(REGISTRY);
        new UptimeMetrics().bindTo(REGISTRY);
    }

    /**
     * Sets up logging metrics.
     */
    private static void setUpLoggingMetrics() {
        new Log4j2Metrics().bindTo(REGISTRY);
    }

    /**
     * Sets up jvm extras metrics.
     */
    private static void setUpExtraMetrics() {
        new ProcessThreadMetrics().bindTo(REGISTRY);
        new ProcessMemoryMetrics().bindTo(REGISTRY);
    }

    /**
     * Registers meters post start.
     * @param sf the server factory bean.
     * @param server the cxf server.
     */
    public static void registerPostStart(JAXRSServerFactoryBean sf, Server server) {
        setUpJettyThreadPoolMetrics(sf, server);
    }

    /**
     * Sets up jetty thread pool metrics.
     * @param sf the server factory bean.
     * @param server the cxf server.
     */
    private static void setUpJettyThreadPoolMetrics(JAXRSServerFactoryBean sf, Server server) {
        JettyHTTPServerEngineFactory engineFactory = sf.getBus()
                .getExtension(JettyHTTPServerEngineFactory.class);
        JettyHTTPServerEngine engine = engineFactory.retrieveJettyHTTPServerEngine(
                URI.create(server.getDestination().getAddress().getAddress().getValue())
                .getPort()
        );
        org.eclipse.jetty.server.Server jettyServer = engine.getServer();

        new JettyServerThreadPoolMetrics(jettyServer.getThreadPool(), Collections.emptyList())
                .bindTo(REGISTRY);
    }

}
