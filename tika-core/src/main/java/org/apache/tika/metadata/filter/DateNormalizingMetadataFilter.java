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
package org.apache.tika.metadata.filter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Some dates in some file formats do not have a timezone.
 * Tika correctly stores these without a timezone, e.g. 'yyyy-MM-dd'T'HH:mm:ss'
 * This can be a problem if end points expect a 'Z' timezone.
 * This filter makes the assumption that dates without timezones are UTC
 * and always modifies the date to: "yyyy-MM-dd'T'HH:mm:ss'Z'"
 *
 * Users can specify an alternate defaultTimeZone with
 * {@link DateNormalizingMetadataFilter#setDefaultTimeZone(String)} to apply
 * if the file format does not specify a timezone.
 *
 */
@TikaComponent
public class DateNormalizingMetadataFilter extends MetadataFilterBase {

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public String defaultTimeZone = "UTC";
    }

    private static TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final DateTimeFormatter LOCAL_OR_OFFSET = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter UTC_OUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    private static final Logger LOGGER = LoggerFactory.getLogger(DateNormalizingMetadataFilter.class);

    private TimeZone defaultTimeZone = UTC;

    public DateNormalizingMetadataFilter() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public DateNormalizingMetadataFilter(Config config) {
        this.defaultTimeZone = TimeZone.getTimeZone(ZoneId.of(config.defaultTimeZone));
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public DateNormalizingMetadataFilter(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    protected void filter(Metadata metadata) {
        for (String n : metadata.names()) {
            Property property = Property.get(n);
            if (property == null || !property.getValueType().equals(Property.ValueType.DATE)) {
                continue;
            }
            String[] values = metadata.getValues(property);
            for (int i = 0; i < values.length; i++) {
                if (values[i].endsWith("Z")) {
                    continue;
                }
                try {
                    values[i] = toUtc(values[i]);
                } catch (DateTimeParseException e) {
                    LOGGER.warn("Couldn't convert date to default time zone: >" + values[i] + "<");
                }
            }
            metadata.set(property, values);
        }
    }

    private String toUtc(String dateString) {
        TemporalAccessor t = LOCAL_OR_OFFSET.parse(dateString);
        OffsetDateTime odt = t.isSupported(ChronoField.OFFSET_SECONDS) ? OffsetDateTime.from(t)
                : LocalDateTime.from(t).atZone(defaultTimeZone.toZoneId()).toOffsetDateTime();
        return odt.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
                .format(UTC_OUT);
    }

    public void setDefaultTimeZone(String timeZoneId) {
        this.defaultTimeZone = TimeZone.getTimeZone(ZoneId.of(timeZoneId));
    }

    public String getDefaultTimeZone() {
        return this.defaultTimeZone.toZoneId().toString();
    }
}
