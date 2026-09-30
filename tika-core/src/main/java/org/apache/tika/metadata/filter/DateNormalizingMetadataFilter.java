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

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.utils.TikaDates;

/**
 * Rewrites every value of every DATE property to canonical UTC, "yyyy-MM-dd'T'HH:mm:ss'Z'",
 * for end points that require a 'Z' timezone. An explicit offset is honored; zone-less values
 * are read in the default time zone (UTC unless set), and date-only values as midday there.
 * Partial dates are coerced: missing fields take their minimum, so "2019" becomes
 * 2019-01-01 and "2019-06" becomes 2019-06-01, both at midday in the default zone.
 * Unparseable values are left as they are.
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
                String utc = toUtc(values[i]);
                if (utc != null) {
                    values[i] = utc;
                }
            }
            metadata.set(property, values);
        }
    }

    /** @return canonical UTC, or null to leave the value as is (unparseable) */
    private String toUtc(String value) {
        Optional<TikaDates.ParsedDate> parsed = TikaDates.parse(value);
        if (parsed.isEmpty()) {
            LOGGER.warn("Couldn't convert date to UTC: >{}<", abbreviate(value));
            return null;
        }
        // a partial's LocalDateTime is already day 1 at midday
        TikaDates.ParsedDate d = parsed.get();
        try {
            OffsetDateTime odt = d.hasZone() ? d.getLocalDateTime().atOffset(d.getOffset())
                    : d.getLocalDateTime().atZone(defaultTimeZone.toZoneId()).toOffsetDateTime();
            return odt.withOffsetSameInstant(ZoneOffset.UTC).format(UTC_OUT);
        } catch (DateTimeException e) {
            LOGGER.warn("Couldn't convert date to UTC: >{}<", abbreviate(value));
            return null;
        }
    }

    private static String abbreviate(String value) {
        return value.length() > 100 ? value.substring(0, 100) + "..." : value;
    }

    public void setDefaultTimeZone(String timeZoneId) {
        this.defaultTimeZone = TimeZone.getTimeZone(ZoneId.of(timeZoneId));
    }

    public String getDefaultTimeZone() {
        return this.defaultTimeZone.toZoneId().toString();
    }
}
