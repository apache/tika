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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.async.AsyncLoggerConfig;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Log4J metrics meter binder.
 * Largely inspired by: https://github.com/micrometer-metrics/micrometer/blob/main/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/logging/Log4j2Metrics.java
 */
public class Log4j2Metrics implements MeterBinder, AutoCloseable {

    /**
     * Te meter name.
     */
    private static final String METER_NAME = "log4j2.events";

    /**
     * Additional tags.
     */
    private final Iterable<Tag> tags;

    /**
     * List of metrics filters
     */
    private final List<MetricsFilter> metricsFilters = new ArrayList<>();

    private final LoggerContext loggerContext;

    /**
     * Initializes metrics with no additional tags.
     */
    public Log4j2Metrics() {
        this(Collections.emptyList());
    }

    /**
     * Initializes metrics with additional tags.
     * @param tags the additional tags.
     */
    public Log4j2Metrics(Iterable<Tag> tags) {
        this(tags, (LoggerContext) LogManager.getContext(false));
    }

    public Log4j2Metrics(Iterable<Tag> tags, LoggerContext loggerContext) {
        this.tags = tags;
        this.loggerContext = loggerContext;
    }
    /**
     * Binds the metrics to registry.
     * @param meterRegistry the meter registry to bind to.
     */
    @Override
    public void bindTo(@NotNull MeterRegistry meterRegistry) {
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();
        rootLoggerConfig.addFilter(createMetricsFilterAndStart(meterRegistry, rootLoggerConfig));

        loggerContext.getConfiguration().getLoggers().values().stream()
                .filter(loggerConfig -> !loggerConfig.isAdditive())
                .forEach(loggerConfig -> {
                    if (loggerConfig == rootLoggerConfig) {
                        return;
                    }
                    Filter logFilter = loggerConfig.getFilter();

                    if ((logFilter instanceof CompositeFilter && Arrays.stream(((CompositeFilter) logFilter).getFiltersArray())
                            .anyMatch(innerFilter -> innerFilter instanceof MetricsFilter))) {
                        return;
                    }

                    if (logFilter instanceof MetricsFilter) {
                        return;
                    }
                    loggerConfig.addFilter(createMetricsFilterAndStart(meterRegistry, loggerConfig));
                });

        loggerContext.updateLoggers(configuration);
    }


    private MetricsFilter createMetricsFilterAndStart(MeterRegistry registry, LoggerConfig loggerConfig) {
        MetricsFilter metricsFilter = new MetricsFilter(registry, tags, loggerConfig);
        metricsFilter.start();
        metricsFilters.add(metricsFilter);
        return metricsFilter;
    }


    /**
     * Clears all metrics filters.
     * @throws Exception if an issue.
     */
    @Override
    public void close() throws Exception {
        if (!metricsFilters.isEmpty()) {
            Configuration configuration = loggerContext.getConfiguration();
            LoggerConfig rootLoggerConfig = configuration.getRootLogger();
            metricsFilters.forEach(rootLoggerConfig::removeFilter);

            loggerContext.getConfiguration().getLoggers().values().stream()
                    .filter(loggerConfig -> !loggerConfig.isAdditive())
                    .forEach(loggerConfig -> {
                        if (loggerConfig != rootLoggerConfig) {
                            metricsFilters.forEach(loggerConfig::removeFilter);
                        }
                    });

            loggerContext.updateLoggers(configuration);
            metricsFilters.forEach(MetricsFilter::stop);
        }
    }


    /**
     * Monitors all logging through log4j2 and keeps count of
     * all logs for all levels.
     */
    @NonNullApi
    @NonNullFields
    private class MetricsFilter extends AbstractFilter {

        /**
         * Map of log level string to counter.
         */
        private final Map<String, Counter> LEVEL_COUNTER_MAP =
                new HashMap<>();

        private final boolean isAsyncLogger;

        //this is a list of non-MetricsFilters associated with the loggerConfig
        //these are used to determine whether or not to filter this through
        //MetricsFilter
        private final List<Filter> filters;

        /**
         * Initializes metrics filter with registry and tags.
         * @param registry the meter registry to bind to.
         * @param tags the additional tags.
         */
        MetricsFilter(MeterRegistry registry, Iterable<Tag> tags, LoggerConfig loggerConfig) {
            this.isAsyncLogger = (loggerConfig instanceof AsyncLoggerConfig);
            this.filters = getNonMetricsFilterFilters(loggerConfig);
            Stream.of("fatal", "error", "warn", "info", "debug", "trace")
                    .forEach(levelStr -> LEVEL_COUNTER_MAP.put(levelStr,
                            Counter.builder(METER_NAME)
                            .tags(tags)
                            .tags("level", levelStr)
                            .description("Number of " + levelStr + " level log events")
                            .baseUnit(BaseUnits.EVENTS)
                            .register(registry)));
        }

        private List<Filter> getNonMetricsFilterFilters(LoggerConfig loggerConfig) {
            List<Filter> filters = new ArrayList<>();
            Filter filter = loggerConfig.getFilter();
            addFilters(filter, filters);
            return filters;
        }

        private void addFilters(Filter filter, List<Filter> filters) {
            if (filter == null) {
                return;
            }
            if (filter instanceof CompositeFilter) {
                for (Filter f : ((CompositeFilter)filter).getFiltersArray()) {
                    addFilters(f, filters);
                }
                return;
            }
            if (filter instanceof MetricsFilter) {
                return;
            }
            filters.add(filter);
        }

        /**
         * Internally calls the filter chain, and increments based on final decision.
         * @param event the logging event.
         * @return the final decision.
         */
        @Override
        public Result filter(LogEvent event) {
            Result result = Result.NEUTRAL;
            for (Filter filter : filters) {
                result = filter.filter(event);
                if (result != Result.NEUTRAL) break;
            }
            if (result != Result.DENY) {
                if (!isAsyncLogger || isAsyncLoggerAndEndOfBatch(event)) {
                    incrementCounter(event);
                }
            }
            return result;
        }

        private boolean isAsyncLoggerAndEndOfBatch(LogEvent event) {
            return isAsyncLogger && event.isEndOfBatch();
        }

        /**
         * Increments the appropriate counter.
         * @param event the logging event.
         */
        private void incrementCounter(LogEvent event) {
            LEVEL_COUNTER_MAP.get(event.getLevel().toString().toLowerCase(Locale.ROOT))
                    .increment();
        }
    }

}
