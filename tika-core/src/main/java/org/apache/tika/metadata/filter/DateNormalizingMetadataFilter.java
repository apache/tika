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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some dates in some file formats do not have a timezone. Tika correctly stores these without a
 * timezone, e.g. 'yyyy-MM-dd'T'HH:mm:ss' This can be a problem if end points expect a 'Z' timezone.
 * This filter makes the assumption that dates without timezones are UTC and always modifies the
 * date to: "yyyy-MM-dd'T'HH:mm:ss'Z'"
 *
 * <p>Users can specify an alternate defaultTimeZone with {@link
 * DateNormalizingMetadataFilter#setDefaultTimeZone(String)} to apply if the file format does not
 * specify a timezone.
 */
public class DateNormalizingMetadataFilter extends MetadataFilter {

    private static TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DateNormalizingMetadataFilter.class);

    private TimeZone defaultTimeZone = UTC;

    @Override
    public void filter(Metadata metadata) throws TikaException {
        SimpleDateFormat dateFormatter = null;
        SimpleDateFormat utcFormatter = null;
        for (String n : metadata.names()) {

            Property property = Property.get(n);
            if (property != null) {
                if (property.getValueType().equals(Property.ValueType.DATE)) {
                    String dateString = metadata.get(property);
                    if (dateString.endsWith("Z")) {
                        continue;
                    }
                    if (dateFormatter == null) {
                        dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                        dateFormatter.setTimeZone(defaultTimeZone);
                        utcFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                        utcFormatter.setTimeZone(UTC);
                    }
                    Date d = null;
                    try {
                        d = dateFormatter.parse(dateString);
                        metadata.set(property, utcFormatter.format(d));
                    } catch (ParseException e) {
                        LOGGER.warn(
                                "Couldn't convert date to default time zone: >" + dateString + "<");
                    }
                }
            }
        }
    }

    @Field
    public void setDefaultTimeZone(String timeZoneId) {
        this.defaultTimeZone = TimeZone.getTimeZone(ZoneId.of(timeZoneId));
    }
}
