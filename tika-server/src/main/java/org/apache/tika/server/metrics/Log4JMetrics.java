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
import java.util.List;

import static org.apache.log4j.Level.DEBUG_INT;
import static org.apache.log4j.Level.ERROR_INT;
import static org.apache.log4j.Level.FATAL_INT;
import static org.apache.log4j.Level.INFO_INT;
import static org.apache.log4j.Level.TRACE_INT;
import static org.apache.log4j.Level.WARN_INT;

public class Log4JMetrics implements MeterBinder, AutoCloseable {

    private static final String METER_NAME = "log4j.events";

    private final Iterable<Tag> tags;

    private List<Appender> attachedAppenders = new ArrayList<>();

    public Log4JMetrics() {
        this.tags = Collections.emptyList();
    }

    public Log4JMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

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

    @Override
    public void close() throws Exception {
        for (Appender appender : attachedAppenders) {
            MetricsFilter metricsFilter = (MetricsFilter) appender.getFilter();
            appender.clearFilters();
            appender.addFilter(metricsFilter.getNext());
        }
        attachedAppenders.clear();
    }

    private void attachMetricsFilter(Appender appender, MeterRegistry meterRegistry) {
        MetricsFilter metricsFilter = new MetricsFilter(meterRegistry, tags);
        attachedAppenders.add(appender);

        metricsFilter.setNext(appender.getFilter());
        appender.clearFilters();
        appender.addFilter(metricsFilter);
    }

    @NonNullApi
    @NonNullFields
    class MetricsFilter extends Filter {

        private final Counter fatalCounter;
        private final Counter errorCounter;
        private final Counter warnCounter;
        private final Counter infoCounter;
        private final Counter debugCounter;
        private final Counter traceCounter;

        MetricsFilter(MeterRegistry registry, Iterable<Tag> tags) {
            fatalCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "fatal")
                    .description("Number of fatal level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);

            errorCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "error")
                    .description("Number of error level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);

            warnCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "warn")
                    .description("Number of warn level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);

            infoCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "info")
                    .description("Number of info level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);

            debugCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "debug")
                    .description("Number of debug level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);

            traceCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "trace")
                    .description("Number of trace level log events")
                    .baseUnit(BaseUnits.EVENTS)
                    .register(registry);
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

        private void incrementCounter(LoggingEvent event) {
            switch (event.getLevel().toInt()) {
                case FATAL_INT:
                    fatalCounter.increment();
                    break;
                case ERROR_INT:
                    errorCounter.increment();
                    break;
                case WARN_INT:
                    warnCounter.increment();
                    break;
                case INFO_INT:
                    infoCounter.increment();
                    break;
                case DEBUG_INT:
                    debugCounter.increment();
                    break;
                case TRACE_INT:
                    traceCounter.increment();
                    break;
                default:
                    break;
            }
        }
    }

}
