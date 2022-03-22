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
package org.apache.tika.metadata.writefilter;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * This is to be used to limit the amount of metadata that a
 * parser can add based on the {@link #maxTotalEstimatedSize}. The
 * maxEstimatedSize is measured in UTF-16 bytes.
 *
 * The size is estimated as a rough order of magnitude of what is
 * required to store the string in memory in Java.  We recognize
 * that Java uses more bytes to store length, offset etc. for strings. But
 * the extra overhead varies by java version and implementation,
 * and we just need a basic estimate.  We also recognize actual
 * memory usage is affected by interning strings, etc.
 * Please forgive us or consider writing your own write filter. :)
 *
 * This can also be used to limit which fields are stored
 * in the metadata object at write-time with {@link #includeFields}.
 *
 * <b>NOTE:</b> Fields in {@link #ALWAYS_SET_FIELDS} are
 * always set no matter the current state of {@link #maxTotalEstimatedSize}.
 * Except for {@link TikaCoreProperties#TIKA_CONTENT}, they are truncated at
 * {@link #maxFieldSize}, and their sizes contribute to the {@link #maxTotalEstimatedSize}.
 *
 * <b>NOTE:</b> Fields in {@link #ALWAYS_ADD_FIELDS} are
 * always added no matter the current state of {@link #maxTotalEstimatedSize}.
 * Except for {@link TikaCoreProperties#TIKA_CONTENT}, each addition is truncated at
 * {@link #maxFieldSize}, and their sizes contribute to the {@link #maxTotalEstimatedSize}.
 *
 * <b>NOTE:</b> as with {@link Metadata}, this object is not thread safe.
 */
public class StandardWriteFilter implements MetadataWriteFilter, Serializable {

    public static final Set<String> ALWAYS_SET_FIELDS = new HashSet<>();
    public static final Set<String> ALWAYS_ADD_FIELDS = new HashSet<>();

    static {
        ALWAYS_SET_FIELDS.add(Metadata.CONTENT_LENGTH);
        ALWAYS_SET_FIELDS.add(Metadata.CONTENT_TYPE);
        ALWAYS_SET_FIELDS.add(Metadata.CONTENT_ENCODING);
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE.getName());
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE.getName());
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.CONTENT_TYPE_HINT.getName());
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.TIKA_CONTENT.getName());
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.RESOURCE_NAME_KEY);
        ALWAYS_SET_FIELDS.add(AccessPermissions.EXTRACT_CONTENT.getName());
        ALWAYS_SET_FIELDS.add(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY.getName());
        ALWAYS_SET_FIELDS.add(Metadata.CONTENT_DISPOSITION);
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.CONTAINER_EXCEPTION.getName());
        ALWAYS_SET_FIELDS.add(TikaCoreProperties.EMBEDDED_EXCEPTION.getName());
        //Metadata.CONTENT_LOCATION? used by the html parser
    }

    static {
        ALWAYS_ADD_FIELDS.add(TikaCoreProperties.TIKA_PARSED_BY.getName());
    }

    private static String METADATA_TRUNCATED_KEY = TikaCoreProperties.TRUNCATED_METADATA.getName();
    private static String TIKA_CONTENT_KEY = TikaCoreProperties.TIKA_CONTENT.getName();
    private static String[] TRUE = new String[]{"true"};

    private final boolean includeEmpty;
    private final int maxTotalEstimatedSize;
    private final int maxFieldSize;
    private final int maxKeySize;

    private final Set<String> includeFields;

    private Map<String, Integer> fieldSizes = new HashMap<>();

    //tracks the estimated size in utf16 bytes. Can be > maxEstimated size
    int estimatedSize = 0;

    /**
     * @param maxKeySize maximum key size in UTF-16 bytes-- keys will be truncated to this
     *                   length; if less than 0, keys will not be truncated
     * @param maxEstimatedSize
     * @param includeFields if null or empty, all fields are included; otherwise, which fields
     *                      to add to the metadata object.
     * @param includeEmpty if <code>true</code>, this will set or add an empty value to the
     *                     metadata object.
     */
    public StandardWriteFilter(int maxKeySize, int maxFieldSize, int maxEstimatedSize,
                               Set<String> includeFields,
                               boolean includeEmpty) {

        this.maxKeySize = maxKeySize;
        this.maxFieldSize = maxFieldSize;
        if (maxEstimatedSize < 0) {
            throw new IllegalArgumentException("max estimated size must be > 0");
        }
        this.maxTotalEstimatedSize = maxEstimatedSize;
        this.includeFields = includeFields;
        this.includeEmpty = includeEmpty;
    }

    @Override
    public void filterExisting(Map<String, String[]> data) {
        //this is somewhat costly, but it ensures that
        //metadata that was placed in the metadata object before this
        //filter was applied is removed.
        //It should only be called once, and probably not on that
        //many fields.
        Map<String, String[]> tmp = new HashMap<>();
        for (Map.Entry<String, String[]> e : data.entrySet()) {
            String name = e.getKey();
            String[] vals = e.getValue();
            if (! includeField(name)) {
                continue;
            }
            for (int i = 0; i < vals.length; i++) {
                String v = vals[i];
                if (include(name, v)) {
                    add(name, v, tmp);
                }
            }
        }
        data.clear();
        data.putAll(tmp);
    }


    @Override
    public void set(String field, String value, Map<String, String[]> data) {
        if (! include(field, value)) {
            return;
        }
        if (ALWAYS_SET_FIELDS.contains(field) || ALWAYS_ADD_FIELDS.contains(field)) {
            setAlwaysInclude(field, value, data);
            return;
        }
        StringSizePair filterKey = filterKey(field, value, data);
        setFilterKey(filterKey, value, data);
    }

    private void setAlwaysInclude(String field, String value, Map<String, String[]> data) {
        if (TIKA_CONTENT_KEY.equals(field)) {
            data.put(field, new String[]{ value });
            return;
        }
        int addedSize = estimateSize(value);
        String toSet = value;
        if (addedSize > maxFieldSize) {
            toSet = truncate(value, maxFieldSize, data);
            addedSize = estimateSize(toSet);
        }
        int totalAdded = data.containsKey(field) ? 0 : estimateSize(field);
        totalAdded += addedSize;
        if (data.containsKey(field)) {
            String[] vals = data.get(field);
            //this should only ever be single valued!!!
            if (vals.length > 0) {
                totalAdded -= estimateSize(vals[0]);
            }
        }
        estimatedSize += totalAdded;
        data.put(field, new String[]{toSet});
    }

    private void addAlwaysInclude(String field, String value, Map<String, String[]> data) {
        if (TIKA_CONTENT_KEY.equals(field)) {
            data.put(field, new String[]{ value });
            return;
        }
        if (! data.containsKey(field)) {
            setAlwaysInclude(field, value, data);
            return;
        }
        int addedSize = estimateSize(value);
        String toSet = value;
        if (addedSize > maxFieldSize) {
            toSet = truncate(value, maxFieldSize, data);
            addedSize = estimateSize(toSet);
        }
        int totalAdded = data.containsKey(field) ? 0 : estimateSize(field);
        totalAdded += addedSize;
        if (data.containsKey(field)) {
            String[] vals = data.get(field);
            //this should only ever be single valued!!!
            if (vals.length > 0) {
                totalAdded -= estimateSize(vals[0]);
            }
        }
        estimatedSize += totalAdded;

        data.put(field, appendValue(data.get(field), toSet));
    }


    //calculate the max field length allowed if we are
    //setting a value
    private int maxAllowedToSet(StringSizePair filterKey) {
        Integer existingSizeInt = fieldSizes.get(filterKey.string);
        int existingSize = existingSizeInt == null ? 0 : existingSizeInt;

        //this is how much is allowed by the overall total limit
        int allowedByMaxTotal = maxTotalEstimatedSize - estimatedSize;

        //if we're overwriting a value, that value's data size is now available
        allowedByMaxTotal += existingSize;

        //if we're adding a key, we need to subtract that value
        allowedByMaxTotal -= existingSizeInt == null ? filterKey.size : 0;

        return Math.min(maxFieldSize, allowedByMaxTotal);
    }


    @Override
    public void add(String field, String value, Map<String, String[]> data) {
        if (! include(field, value)) {
            return;
        }
        if (ALWAYS_SET_FIELDS.contains(field)) {
            setAlwaysInclude(field, value, data);
            return;
        } else if (ALWAYS_ADD_FIELDS.contains(field)) {
            addAlwaysInclude(field, value, data);
            return;
        }
        StringSizePair filterKey = filterKey(field, value, data);
        if (! data.containsKey(filterKey.string)) {
            setFilterKey(filterKey, value, data);
            return;
        }

        Integer fieldSizeInteger = fieldSizes.get(filterKey.string);
        int fieldSize = fieldSizeInteger == null ? 0 : fieldSizeInteger;
        int maxAllowed = maxAllowedToAdd(filterKey);
        if (maxAllowed <= 0) {
            setTruncated(data);
            return;
        }
        int valueLength = estimateSize(value);
        String toAdd = value;
        if (valueLength > maxAllowed) {
            toAdd = truncate(value, maxAllowed, data);
            valueLength = estimateSize(toAdd);
            if (valueLength == 0) {
                return;
            }
        }

        int addedOverall = valueLength;
        if (fieldSizeInteger == null) {
            //if there was no value before, we're adding
            //a key.  If there was a value before, do not
            //add the key length.
            addedOverall += filterKey.size;
        }
        estimatedSize += addedOverall;

        fieldSizes.put(filterKey.string, valueLength + fieldSize);

        data.put(filterKey.string, appendValue(data.get(filterKey.string), toAdd ));
    }

    private String[] appendValue(String[] values, final String value) {
        if (value == null) {
            return values;
        }
        String[] newValues = new String[values.length + 1];
        System.arraycopy(values, 0, newValues, 0, values.length);
        newValues[newValues.length - 1] = value;
        return newValues;
    }

    //calculate the max field length allowed if we are
    //adding a value
    private int maxAllowedToAdd(StringSizePair filterKey) {
        Integer existingSizeInt = fieldSizes.get(filterKey.string);
        int existingSize = existingSizeInt == null ? 0 : existingSizeInt;
        //how much can we add to this field
        int allowedByMaxField = maxFieldSize - existingSize;

        //this is how much is allowed by the overall total limit
        int allowedByMaxTotal = maxTotalEstimatedSize - estimatedSize - 1;

        //if we're adding a new key, we need to subtract that value
        allowedByMaxTotal -= existingSizeInt == null ? filterKey.size : 0;

        return Math.min(allowedByMaxField, allowedByMaxTotal);
    }

    private void setFilterKey(StringSizePair filterKey, String value,
                              Map<String, String[]> data) {
        //if you can't even add the key, give up now
        if (! data.containsKey(filterKey.string) &&
                (filterKey.size + estimatedSize > maxTotalEstimatedSize)) {
            setTruncated(data);
            return;
        }

        Integer fieldSizeInteger = fieldSizes.get(filterKey.string);
        int fieldSize = fieldSizeInteger == null ? 0 : fieldSizeInteger;
        int maxAllowed = maxAllowedToSet(filterKey);
        if (maxAllowed <= 0) {
            setTruncated(data);
            return;
        }
        int valueLength = estimateSize(value);
        String toSet = value;
        if (valueLength > maxAllowed) {
            toSet = truncate(value, maxAllowed, data);
            valueLength = estimateSize(toSet);
            if (valueLength == 0) {
                return;
            }
        }

        int addedOverall = 0;
        if (fieldSizeInteger == null) {
            //if there was no value before, we're adding
            //a key.  If there was a value before, do not
            //add the key length.
            addedOverall += filterKey.size;
        }
        addedOverall += valueLength - fieldSize;
        estimatedSize += addedOverall;

        fieldSizes.put(filterKey.string, valueLength);

        data.put(filterKey.string, new String[]{ toSet });

    }

    private void setTruncated(Map<String, String[]> data) {
        data.put(METADATA_TRUNCATED_KEY, TRUE);
    }

    private StringSizePair filterKey(String field, String value, Map<String, String[]> data) {
        int size = estimateSize(field);
        if (size <= maxKeySize) {
            return new StringSizePair(field, size, false);
        }

        String toWrite = truncate(field, maxKeySize, data);
        return new StringSizePair(toWrite,
                estimateSize(toWrite),
                true);
    }

    private String truncate(String value, int length, Map<String, String[]> data) {
        setTruncated(data);

        //correctly handle multibyte characters
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, length);
        CharBuffer cb = CharBuffer.allocate(length);
        CharsetDecoder decoder = StandardCharsets.UTF_16BE.newDecoder();
        // Ignore last (potentially) incomplete character
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.decode(bb, cb, true);
        decoder.flush(cb);
        return new String(cb.array(), 0, cb.position());
    }

    private boolean include(String field, String value) {
        return includeField(field) && includeValue(value);
    }

    /**
     * Tests for null or empty. Does not check for length
     * @param value
     * @return
     */
    private boolean includeValue(String value) {
        if (includeEmpty) {
            return true;
        }
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return true;
    }

    private boolean includeField(String name) {
        if (ALWAYS_SET_FIELDS.contains(name)) {
            return true;
        }
        if (includeFields == null ||
                includeFields.contains(name)) {
            return true;
        }
        return false;
    }

    private static int estimateSize(String s) {
        return 2 * s.length();
    }

    private static class StringSizePair {
        final String string;
        final int size;//utf-16 bytes -- estimated
        final boolean truncated;

        public StringSizePair(String string, int size, boolean truncated) {
            this.string = string;
            this.size = size;
            this.truncated = truncated;
        }
    }
}
