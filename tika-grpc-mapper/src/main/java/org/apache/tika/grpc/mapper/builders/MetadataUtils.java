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
package org.apache.tika.grpc.mapper.builders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.MetadataEntry;
import org.apache.tika.grpc.v1.StringList;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Utility methods for mapping Tika metadata to protobuf structures.
 * 
 * Provides common functionality for all metadata builders including:
 * - Type conversion utilities
 * - The full typed metadata mirror ({@link #buildMetadataEntries})
 * - Base fields population
 * - Safe field extraction with logging
 */
public class MetadataUtils {

    /**
     * Utility class; not meant to be instantiated.
     */
    private MetadataUtils() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(MetadataUtils.class);
    
    /**
     * Maps a string field from Tika metadata to protobuf builder.
     * 
     * @param metadata Tika metadata object
     * @param key Metadata key (can be String or Property)
     * @param setter Protobuf builder setter method
     * @param mappedFields Set to track mapped fields
     */
    public static void mapStringField(Metadata metadata, Object key, Consumer<String> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
            mappedFields.add(keyStr);
            LOG.trace(String.format(Locale.ROOT, "Mapped string field: %s = %s", keyStr, value));
        }
    }
    
    /**
     * Maps an integer field from Tika metadata to protobuf builder. Parses the trimmed value as an
     * int and, on success, applies it via the setter and marks the key as mapped; on a parse failure
     * the key is left unmapped so it falls through to the additional-metadata struct.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter method
     * @param mappedFields set to track mapped fields
     */
    public static void mapIntField(Metadata metadata, Object key, Consumer<Integer> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                int intValue = Integer.parseInt(value.trim());
                setter.accept(intValue);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped int field: %s = %d", keyStr, intValue));
            } catch (NumberFormatException e) {
                LOG.warn(String.format(Locale.ROOT, "Failed to parse integer value for field %s: %s", keyStr, value));
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a long field from Tika metadata to protobuf builder. Parses the trimmed value as a long
     * and, on success, applies it via the setter and marks the key as mapped; on a parse failure the
     * key is left unmapped so it falls through to the additional-metadata struct.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter method
     * @param mappedFields set to track mapped fields
     */
    public static void mapLongField(Metadata metadata, Object key, Consumer<Long> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                long longValue = Long.parseLong(value.trim());
                setter.accept(longValue);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped long field: %s = %d", keyStr, longValue));
            } catch (NumberFormatException e) {
                LOG.warn(String.format(Locale.ROOT, "Failed to parse long value for field %s: %s", keyStr, value));
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a double field from Tika metadata to protobuf builder. Parses the trimmed value as a
     * double and, on success, applies it via the setter and marks the key as mapped; on a parse
     * failure the key is left unmapped so it falls through to the additional-metadata struct.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter method
     * @param mappedFields set to track mapped fields
     */
    public static void mapDoubleField(Metadata metadata, Object key, Consumer<Double> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                double doubleValue = Double.parseDouble(value.trim());
                setter.accept(doubleValue);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped double field: %s = %f", keyStr, doubleValue));
            } catch (NumberFormatException e) {
                LOG.warn(String.format(Locale.ROOT, "Failed to parse double value for field %s: %s", keyStr, value));
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a boolean field from Tika metadata to protobuf builder. Interprets the trimmed value,
     * case-insensitively, as true when it equals {@code "true"}, {@code "yes"}, or {@code "1"}, applies
     * the result via the setter, and marks the key as mapped.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter method
     * @param mappedFields set to track mapped fields
     */
    public static void mapBooleanField(Metadata metadata, Object key, Consumer<Boolean> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            String trimmedValue = value.trim().toLowerCase(Locale.ROOT);
            boolean boolValue = "true".equals(trimmedValue) || "yes".equals(trimmedValue) || "1".equals(trimmedValue);
            setter.accept(boolValue);
            mappedFields.add(keyStr);
            LOG.trace(String.format(Locale.ROOT, "Mapped boolean field: %s = %b (from '%s')", keyStr, boolValue, value));
        }
    }

    /**
     * Maps a boolean field and also sets a raw string fallback via the provided rawSetter.
     * Always sets the raw value if present; sets the boolean based on common truthy strings
     * ({@code "true"}, {@code "yes"}, or {@code "1"}, case-insensitive). Does nothing if the value
     * is absent or blank.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter for the parsed boolean value
     * @param rawSetter protobuf builder setter for the original trimmed string value
     * @param mappedFields set to track mapped fields
     */
    public static void mapBooleanFieldWithRaw(
            Metadata metadata,
            Object key,
            Consumer<Boolean> setter,
            Consumer<String> rawSetter,
            Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        String trimmed = value.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        boolean boolValue = "true".equals(lowered) || "yes".equals(lowered) || "1".equals(lowered);
        setter.accept(boolValue);
        rawSetter.accept(trimmed);
        mappedFields.add(keyStr);
        LOG.trace(String.format(Locale.ROOT, "Mapped boolean field (with raw): %s = %b; raw='%s'", keyStr, boolValue, trimmed));
    }
    
    /**
     * Maps a timestamp field from Tika metadata to a protobuf {@link Timestamp}. For a
     * {@link Property} key the value is read as a date directly; for a string key the value is parsed
     * either as epoch milliseconds or as an ISO-8601 instant. On success the resulting timestamp is
     * applied via the setter and the key is marked as mapped.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter method
     * @param mappedFields set to track mapped fields
     */
    public static void mapTimestampField(Metadata metadata, Object key, Consumer<Timestamp> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        
        // Try to get as Date first (preferred)
        Date dateValue = null;
        try {
            if (key instanceof Property) {
                dateValue = metadata.getDate((Property) key);
            } else {
                // For string keys, try to parse the string value as date
                String stringValue = metadata.get(keyStr);
                if (stringValue != null && !stringValue.trim().isEmpty()) {
                    // Tika usually stores dates in ISO format or as milliseconds
                    try {
                        long millis = Long.parseLong(stringValue.trim());
                        dateValue = new Date(millis);
                    } catch (NumberFormatException e) {
                        // Try parsing as ISO date string
                        try {
                            Instant instant = Instant.parse(stringValue.trim());
                            dateValue = Date.from(instant);
                        } catch (Exception parseException) {
                            LOG.warn(String.format(Locale.ROOT, "Failed to parse date value for field %s: %s", keyStr, stringValue));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn(String.format(Locale.ROOT, "Failed to extract date for field %s: %s", keyStr, e.getMessage()));
        }
        
        if (dateValue != null) {
            Instant instant = dateValue.toInstant();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
            setter.accept(timestamp);
            mappedFields.add(keyStr);
            LOG.trace(String.format(Locale.ROOT, "Mapped timestamp field: %s = %s", keyStr, instant));
        }
    }

    /**
     * Maps a timestamp field with a raw fallback. Attempts to resolve the value to a protobuf
     * {@link Timestamp} (via the property's date, or by parsing the string as epoch milliseconds or an
     * ISO-8601 instant); on success it applies the timestamp via the setter, otherwise it applies the
     * original trimmed string via rawSetter. Either way the key is marked as mapped. Does nothing if
     * the value is absent or blank.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter for the parsed {@link Timestamp}
     * @param rawSetter protobuf builder setter for the original trimmed string value used as fallback
     * @param mappedFields set to track mapped fields
     */
    public static void mapTimestampFieldWithRaw(
            Metadata metadata,
            Object key,
            Consumer<Timestamp> setter,
            Consumer<String> rawSetter,
            Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String original = metadata.get(keyStr);
        if (original == null || original.trim().isEmpty()) {
            return;
        }
        // First try the standard timestamp mapping path
        Date dateValue = null;
        try {
            if (key instanceof Property) {
                dateValue = metadata.getDate((Property) key);
            }
        } catch (Exception e) {
            // ignore and fallback to parsing below
        }
        if (dateValue == null) {
            // Fallback: parse from string (millis or ISO-8601)
            try {
                long millis = Long.parseLong(original.trim());
                dateValue = new Date(millis);
            } catch (NumberFormatException e) {
                try {
                    Instant instant = Instant.parse(original.trim());
                    dateValue = Date.from(instant);
                } catch (Exception ignore) {
                    // parsing failed
                }
            }
        }

        if (dateValue != null) {
            Instant instant = dateValue.toInstant();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
            setter.accept(timestamp);
            mappedFields.add(keyStr);
            LOG.trace(String.format(Locale.ROOT, "Mapped timestamp field (with raw available): %s = %s", keyStr, instant));
        } else {
            rawSetter.accept(original.trim());
            mappedFields.add(keyStr);
            LOG.trace(String.format(Locale.ROOT, "Mapped timestamp raw fallback: %s = '%s'", keyStr, original.trim()));
        }
    }
    
    /**
     * Maps a repeated string field from Tika metadata to protobuf builder. Collects all non-blank
     * trimmed values for the key and, if any remain, applies them via the setter and marks the key as
     * mapped.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter accepting the iterable of string values
     * @param mappedFields set to track mapped fields
     */
    public static void mapRepeatedStringField(Metadata metadata, Object key, Consumer<Iterable<String>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<String> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    valueList.add(value.trim());
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped repeated string field: %s = %s", keyStr, valueList));
            }
        }
    }
    
    /**
     * Maps a repeated integer field from Tika metadata to protobuf builder. Parses each non-blank
     * value as an int (skipping any that fail to parse) and, if any values remain, applies them via the
     * setter and marks the key as mapped.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter accepting the iterable of integer values
     * @param mappedFields set to track mapped fields
     */
    public static void mapRepeatedIntField(Metadata metadata, Object key, Consumer<Iterable<Integer>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<Integer> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        int intValue = Integer.parseInt(value.trim());
                        valueList.add(intValue);
                    } catch (NumberFormatException e) {
                        LOG.warn(String.format(Locale.ROOT, "Failed to parse integer value in repeated field %s: %s", keyStr, value));
                    }
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped repeated int field: %s = %s", keyStr, valueList));
            }
        }
    }
    
    /**
     * Maps a repeated double field from Tika metadata to protobuf builder. Parses each non-blank
     * value as a double (skipping any that fail to parse) and, if any values remain, applies them via
     * the setter and marks the key as mapped.
     *
     * @param metadata Tika metadata object
     * @param key metadata key (can be String or Property)
     * @param setter protobuf builder setter accepting the iterable of double values
     * @param mappedFields set to track mapped fields
     */
    public static void mapRepeatedDoubleField(Metadata metadata, Object key, Consumer<Iterable<Double>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<Double> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        double doubleValue = Double.parseDouble(value.trim());
                        valueList.add(doubleValue);
                    } catch (NumberFormatException e) {
                        LOG.warn(String.format(Locale.ROOT, "Failed to parse double value in repeated field %s: %s", keyStr, value));
                    }
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.trace(String.format(Locale.ROOT, "Mapped repeated double field: %s = %s", keyStr, valueList));
            }
        }
    }
    
    /**
     * Builds BaseFields with common parsing metadata.
     * 
     * @param parserClass Tika parser class name
     * @param tikaVersion Tika version
     * @param metadata Original Tika metadata for additional info
     * @return BaseFields with parsing metadata
     */
    public static BaseFields buildBaseFields(String parserClass, String tikaVersion, Metadata metadata) {
        BaseFields.Builder builder = BaseFields.newBuilder();
        
        
        // Set parser information
        if (parserClass != null && !parserClass.isEmpty()) {
            builder.setParserClass(parserClass);
        }
        
        if (tikaVersion != null && !tikaVersion.isEmpty()) {
            builder.setTikaVersion(tikaVersion);
        }
        
        // Set parse timestamp
        builder.setParseTimestamp(Instant.now().toString());
        
        // Add any parsing warnings (if present in metadata)
        String[] warnings = metadata.getValues("tika:parsing-warning");
        if (warnings != null && warnings.length > 0) {
            for (String warning : warnings) {
                if (warning != null && !warning.trim().isEmpty()) {
                    builder.addParseWarnings(warning.trim());
                }
            }
        }
        
        LOG.debug("Built base fields, parser: {}, version: {}", parserClass, tikaVersion);
        
        return builder.build();
    }
    
    /**
     * Converts a key (String or Property) to string representation.
     */
    static String getKeyString(Object key) {
        if (key instanceof Property) {
            return ((Property) key).getName();
        } else if (key instanceof String) {
            return (String) key;
        } else {
            return key.toString();
        }
    }
    
    /**
     * Gets the current Tika version from the shaded Tika package's implementation version.
     *
     * @return the Tika implementation version, or {@code "unknown"} if it cannot be determined
     */
    public static String getTikaVersion() {
        try {
            // Try to get Tika version from package info
            Package tikaPackage = org.apache.tika.Tika.class.getPackage();
            if (tikaPackage != null && tikaPackage.getImplementationVersion() != null) {
                return tikaPackage.getImplementationVersion();
            }
        } catch (Exception e) {
            LOG.debug("Could not determine Tika version", e);
        }
        return "unknown";
    }

    /**
     * Attempts to retrieve raw document bytes that may have been injected into the metadata
     * under a special base64-encoded key ({@code pipe:raw-bytes-b64}) by the caller. Returns null if
     * the key is absent, the encoded value exceeds the soft size limit, or decoding fails.
     *
     * @param metadata Tika metadata object that may carry the base64-encoded raw bytes
     * @return the decoded raw document bytes, or {@code null} if not present, too large, or undecodable
     */
    public static byte[] tryGetRawBytes(Metadata metadata) {
        try {
            String b64 = metadata.get("pipe:raw-bytes-b64");
            if (b64 == null || b64.isEmpty()) {
                return null;
            }
            // Soft limit: if extremely large, skip to avoid memory blowups
            if (b64.length() > 16_000_000) { // ~12MB base64
                LOG.debug("Skipping raw-bytes decode due to size limit");
                return null;
            }
            return Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            LOG.debug("Failed to decode raw-bytes from metadata", e);
            return null;
        }
    }

    /**
     * Builds typed metadata entries for every Tika key. Registered {@link Property} definitions
     * drive coercion into {@code integer}, {@code number}, {@code boolean}, or {@code date};
     * unregistered keys attempt the same parsing before falling back to {@code text}.
     * Multivalue keys always use {@code text_list}.
     *
     * @param metadata Tika metadata object
     * @return one {@link MetadataEntry} per non-empty Tika key
     */
    public static List<MetadataEntry> buildMetadataEntries(Metadata metadata) {
        List<MetadataEntry> entries = new ArrayList<>();
        if (metadata == null) {
            return entries;
        }
        for (String field : metadata.names()) {
            String[] values = metadata.getValues(field);
            if (values == null || values.length == 0) {
                continue;
            }
            MetadataEntry.Builder entryBuilder = MetadataEntry.newBuilder().setKey(field);
            Property property = Property.get(field);
            if (values.length == 1) {
                coerceSingleMetadataValue(entryBuilder, metadata, field, values[0], property);
            } else {
                StringList.Builder listBuilder = StringList.newBuilder();
                for (String value : values) {
                    if (value != null && !value.trim().isEmpty()) {
                        listBuilder.addValues(value.trim());
                    }
                }
                if (listBuilder.getValuesCount() > 0) {
                    entryBuilder.setTextList(listBuilder.build());
                }
            }
            if (hasMetadataEntryValue(entryBuilder)) {
                entries.add(entryBuilder.build());
            }
        }
        return entries;
    }

    private static boolean hasMetadataEntryValue(MetadataEntry.Builder entryBuilder) {
        return entryBuilder.hasText()
                || entryBuilder.hasTextList()
                || entryBuilder.hasInteger()
                || entryBuilder.hasNumber()
                || entryBuilder.hasBoolean()
                || entryBuilder.hasDate();
    }

    private static void coerceSingleMetadataValue(
            MetadataEntry.Builder entryBuilder,
            Metadata metadata,
            String field,
            String rawValue,
            Property property) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return;
        }
        String trimmed = rawValue.trim();
        if (property != null && applyRegisteredPropertyValue(entryBuilder, metadata, property, trimmed)) {
            return;
        }
        if (applyHeuristicTypedValue(entryBuilder, trimmed)) {
            return;
        }
        entryBuilder.setText(trimmed);
    }

    private static boolean applyRegisteredPropertyValue(
            MetadataEntry.Builder entryBuilder,
            Metadata metadata,
            Property property,
            String trimmed) {
        Property primary = property.getPrimaryProperty();
        return switch (primary.getValueType()) {
            case INTEGER -> applyIntegerValue(entryBuilder, metadata, primary, trimmed);
            case REAL, RATIONAL -> applyNumberValue(entryBuilder, trimmed);
            case BOOLEAN -> {
                entryBuilder.setBoolean(parseBooleanLoose(trimmed));
                yield true;
            }
            case DATE -> applyDateValue(entryBuilder, metadata, primary, trimmed);
            default -> false;
        };
    }

    private static boolean applyIntegerValue(
            MetadataEntry.Builder entryBuilder,
            Metadata metadata,
            Property property,
            String trimmed) {
        Integer intValue = metadata.getInt(property);
        if (intValue != null) {
            entryBuilder.setInteger(intValue.longValue());
            return true;
        }
        try {
            entryBuilder.setInteger(Long.parseLong(trimmed));
            return true;
        } catch (NumberFormatException e) {
            LOG.trace(String.format(Locale.ROOT, "Integer coercion failed for %s: %s", property.getName(), trimmed));
            return false;
        }
    }

    private static boolean applyNumberValue(MetadataEntry.Builder entryBuilder, String trimmed) {
        try {
            entryBuilder.setNumber(Double.parseDouble(trimmed));
            return true;
        } catch (NumberFormatException e) {
            LOG.trace(String.format(Locale.ROOT, "Number coercion failed for value: %s", trimmed));
            return false;
        }
    }

    private static boolean applyDateValue(
            MetadataEntry.Builder entryBuilder,
            Metadata metadata,
            Property property,
            String trimmed) {
        Date dateValue = metadata.getDate(property);
        if (dateValue == null) {
            dateValue = parseDateString(trimmed);
        }
        if (dateValue != null) {
            entryBuilder.setDate(toTimestamp(dateValue));
            return true;
        }
        return false;
    }

    /**
     * Best-effort typing for keys that are not registered Tika properties.
     */
    private static boolean applyHeuristicTypedValue(MetadataEntry.Builder entryBuilder, String trimmed) {
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if ("true".equals(lowered) || "false".equals(lowered) || "yes".equals(lowered)
                || "no".equals(lowered)) {
            entryBuilder.setBoolean(parseBooleanLoose(trimmed));
            return true;
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                entryBuilder.setInteger(Long.parseLong(trimmed));
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?([Ee][+-]?\\d+)?")) {
            return applyNumberValue(entryBuilder, trimmed);
        }
        Date dateValue = parseDateString(trimmed);
        if (dateValue != null) {
            entryBuilder.setDate(toTimestamp(dateValue));
            return true;
        }
        return false;
    }

    private static boolean parseBooleanLoose(String value) {
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(lowered) || "yes".equals(lowered) || "1".equals(lowered);
    }

    private static Date parseDateString(String value) {
        String trimmed = value.trim();
        try {
            return Date.from(Instant.parse(trimmed));
        } catch (Exception ignored) {
            // not ISO-8601
        }
        try {
            long millis = Long.parseLong(trimmed);
            if (trimmed.length() >= 12 || millis > 1_000_000_000_000L) {
                return new Date(millis);
            }
        } catch (NumberFormatException ignored) {
            // not epoch millis
        }
        return null;
    }

    private static Timestamp toTimestamp(Date date) {
        Instant instant = date.toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
