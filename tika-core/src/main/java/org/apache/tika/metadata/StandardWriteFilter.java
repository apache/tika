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
package org.apache.tika.metadata;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.utils.StringUtils;

/**
 * This is to be used to limit the amount of metadata that a
 * parser can add based on the {@link #maxEstimatedSize}. The
 * maxEstimatedSize is measured in UTF-8 bytes.
 *
 * This can also be used to limit the fields that are stored
 * in the metadata object at write-time with {@link #includeFields}.
 *
 * <b>NOTE:</b> Fields in {@link #ALWAYS_INCLUDE_FIELDS} are never
 * always included, and their sizes are not included in the
 * calculation of metadata size.
 *
 * <b>NOTE:</b> after the maxEstimatedSize has been hit, no
 * further modifications to the metadata object will be allowed aside
 * from adding/setting fields in the {@link #ALWAYS_INCLUDE_FIELDS}.
 *
 * <b>NOTE:</b> as with {@link Metadata}, this object is not thread safe.
 */
public class StandardWriteFilter implements MetadataWriteFilter, Serializable {

    public static final Set<String> ALWAYS_INCLUDE_FIELDS = new HashSet<>();

    static {
        ALWAYS_INCLUDE_FIELDS.add(Metadata.CONTENT_LENGTH);
        ALWAYS_INCLUDE_FIELDS.add(Metadata.CONTENT_TYPE);
        ALWAYS_INCLUDE_FIELDS.add(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE.getName());
        ALWAYS_INCLUDE_FIELDS.add(TikaCoreProperties.CONTENT_TYPE_HINT.getName());
        ALWAYS_INCLUDE_FIELDS.add(TikaCoreProperties.TIKA_CONTENT.getName());
    }

    private final boolean includeEmpty;
    private final int maxEstimatedSize;

    private final Set<String> includeFields;

    //tracks the estimated size in utf8 bytes. Can be > maxEstimated size
    int estimatedSize = 0;

    /**
     *
     * @param maxEstimatedSize
     * @param includeFields if null or empty, all fields are included; otherwise, which fields
     *                      to add to the metadata object.
     * @param includeEmpty if <code>true</code>, this will set or add an empty value to the
     *                     metadata object.
     */
    public StandardWriteFilter(int maxEstimatedSize, Set<String> includeFields,
                               boolean includeEmpty) {
        if (maxEstimatedSize < 0) {
            throw new IllegalArgumentException("max estimated size must be > 0");
        }
        this.maxEstimatedSize = maxEstimatedSize;
        this.includeFields = includeFields;
        this.includeEmpty = includeEmpty;
    }

    @Override
    public void filterExisting(Map<String, String[]> data) {

    }

    @Override
    public boolean include(String field, String value) {
        return includeField(field) && includeValue(value);
    }

    @Override
    public String filter(String field, String value, Map<String, String[]> data) {
        if (ALWAYS_INCLUDE_FIELDS.contains(field)) {
            return value;
        }
        if (estimatedSize > maxEstimatedSize) {
            return null;
        }
        long length = value.getBytes(StandardCharsets.UTF_8).length;
        String toWrite = value;
        if (estimatedSize + length > maxEstimatedSize) {
            toWrite = truncate(value);
            data.put(TikaCoreProperties.METADATA_LIMIT_REACHED.getName(), new String[]{"true"});
        }
        //this will by default bump the estimated size over what was actually written
        //we are currently only using this as an indicator of whether to even try to write more.
        //this value is not necessarily accurate.
        estimatedSize += length;
        return toWrite;
    }

    private String truncate(String value) {
        //correctly handle multibyte characters
        int available = maxEstimatedSize - estimatedSize;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, available);
        CharBuffer cb = CharBuffer.allocate(available);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        // Ignore an incomplete character
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.decode(bb, cb, true);
        decoder.flush(cb);
        return new String(cb.array(), 0, cb.position());
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
        if (ALWAYS_INCLUDE_FIELDS.contains(name)) {
            return true;
        }
        if (includeFields == null) {
            return true;
        }
        if (includeFields.contains(name)) {
            return true;
        }
        return false;
    }

}
