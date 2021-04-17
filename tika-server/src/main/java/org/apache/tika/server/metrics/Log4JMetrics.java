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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.apache.commons.collections4.EnumerationUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Log4J metrics meter binder.
 */
public class Log4JMetrics implements MeterBinder, AutoCloseable {

    /**
     * Te meter name.
     */
    private static final String METER_NAME = "log4j.events";

    /**
     * Additional tags.
     */
    private final Iterable<Tag> tags;

    /**
     * List of appenders that has the metrics filter attached.
     */
    private List<Appender> attachedAppenders = new ArrayList<>();

    /**
     * Initializes metrics with no additional tags.
     */
    public Log4JMetrics() {
        this(Collections.emptyList());
    }

    /**
     * Initializes metrics with additional tags.
     * @param tags the additional tags.
     */
    public Log4JMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    /**
     * Binds the metrics to registry.
     * @param meterRegistry the meter registry to bind to.
     */
    @Override
    public void bindTo(@NotNull MeterRegistry meterRegistry) {
        Logger rootLogger = LogManager.getRootLogger();
        attachToAppenders(CastUtils.cast(rootLogger.getAllAppenders(), Appender.class), meterRegistry);

        EnumerationUtils.toList(CastUtils.cast(LogManager.getCurrentLoggers(), Logger.class)).stream()
                .filter(logger -> !logger.getAdditivity())
                .forEach(logger -> {
                    if (logger == rootLogger) {
                        return;
                    }

                    attachToAppenders(CastUtils.cast(logger.getAllAppenders(), Appender.class), meterRegistry);
                });
    }

    /**
     * Attaches metrics filter to enumeration of appender.
     * @param appenderEnumeration the appender enumeration.
     * @param meterRegistry the meter registry to attach to.
     */
    private void attachToAppenders(Enumeration<Appender> appenderEnumeration, MeterRegistry meterRegistry) {
        while (appenderEnumeration.hasMoreElements()) {
            Appender appender = appenderEnumeration.nextElement();

            if (appender instanceof AsyncAppender) {
                AsyncAppender asyncAppender = (AsyncAppender) appender;
                attachToAppenders(CastUtils.cast(asyncAppender.getAllAppenders(), Appender.class), meterRegistry);
            } else {
                if (!(appender.getFilter() instanceof MetricsFilter)) {
                    attachMetricsFilter(appender, meterRegistry);
                }
            }
        }
    }

    /**
     * Clears all metrics filters.
     * @throws Exception if an issue.
     */
    @Override
    public void close() throws Exception {
        for (Appender appender : attachedAppenders) {
            MetricsFilter metricsFilter = (MetricsFilter) appender.getFilter();
            appender.clearFilters();
            appender.addFilter(metricsFilter.getNext());
        }
        attachedAppenders.clear();
    }

    /**
     * Attaches metrics filter to appender.
     * @param appender the appender to attach to.
     * @param meterRegistry the meter registry to bind to.
     */
    private void attachMetricsFilter(Appender appender, MeterRegistry meterRegistry) {
        MetricsFilter metricsFilter = new MetricsFilter(meterRegistry, tags);
        attachedAppenders.add(appender);

        metricsFilter.setNext(appender.getFilter());
        appender.clearFilters();
        appender.addFilter(metricsFilter);
    }

    /**
     * Monitors all logging through log4j and keeps count of
     * all logs for all levels.
     */
    @NonNullApi
    @NonNullFields
    private class MetricsFilter extends Filter {

        /**
         * Map of log level string to counter.
         */
        private final Map<String, Counter> LEVEL_COUNTER_MAP =
                new HashMap<>();

        /**
         * Initializes metrics filter with registry and tags.
         * @param registry the meter registry to bind to.
         * @param tags the additional tags.
         */
        MetricsFilter(MeterRegistry registry, Iterable<Tag> tags) {
            Stream.of("fatal", "error", "warn", "info", "debug", "trace")
                    .forEach(levelStr -> LEVEL_COUNTER_MAP.put(levelStr,
                            Counter.builder(METER_NAME)
                            .tags(tags)
                            .tags("level", levelStr)
                            .description("Number of " + levelStr + " level log events")
                            .baseUnit(BaseUnits.EVENTS)
                            .register(registry)));
        }

        /**
         * Internally calls the filter chain, and increments based on final decision.
         * @param event the logging event.
         * @return the final decision.
         */
        @Override
        public int decide(LoggingEvent event) {
            int decision = NEUTRAL;

                Filter filter = getNext();
                while (filter != null) {
                    decision = filter.decide(event);
                    if (decision != NEUTRAL) break;
                    filter = filter.getNext();
                }

                if (decision != DENY) {
                    incrementCounter(event);
                }

            return decision;
        }

        /**
         * Increments the appropriate counter.
         * @param event the logging event.
         */
        private void incrementCounter(LoggingEvent event) {
            LEVEL_COUNTER_MAP.get(event.getLevel().toString().toLowerCase(Locale.ROOT))
                    .increment();
        }
    }

}
