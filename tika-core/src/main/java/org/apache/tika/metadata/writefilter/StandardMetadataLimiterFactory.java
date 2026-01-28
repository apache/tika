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

import java.util.HashSet;
import java.util.Set;

import org.apache.tika.config.TikaComponent;

/**
 * Standard factory for creating {@link StandardMetadataLimiter} instances.
 * <p>
 * This factory holds configuration for metadata size limits and field prioritization.
 * It creates limiter instances that enforce these limits when metadata is written.
 * <p>
 * <b>Size limits</b> prevent memory exhaustion from malicious/pathological files:
 * <ul>
 *   <li>{@code maxKeySize} - maximum metadata key length in UTF-16 bytes</li>
 *   <li>{@code maxFieldSize} - maximum value size per field in UTF-16 bytes</li>
 *   <li>{@code maxTotalEstimatedBytes} - total metadata budget in UTF-16 bytes</li>
 *   <li>{@code maxValuesPerField} - maximum number of values per multi-valued field</li>
 * </ul>
 * <p>
 * <b>Prioritization fields</b> reduce the likelihood of hitting limits:
 * <ul>
 *   <li>{@code includeFields} - if non-empty, ONLY these fields are stored</li>
 *   <li>{@code excludeFields} - these fields are never stored</li>
 *   <li>{@code includeEmpty} - whether to store empty/null values</li>
 * </ul>
 * <p>
 * See {@link StandardMetadataLimiter} for details on how sizes are calculated.
 *
 * @since Apache Tika 4.0 (renamed from StandardWriteFilterFactory)
 */
@TikaComponent(name = "standard-metadata-limiter-factory", contextKey = MetadataWriteLimiterFactory.class)
public class StandardMetadataLimiterFactory implements MetadataWriteLimiterFactory {

    public static final int DEFAULT_MAX_KEY_SIZE = 1024;
    public static final int DEFAULT_MAX_FIELD_SIZE = 100 * 1024;
    public static final int DEFAULT_MAX_TOTAL_BYTES = 10 * 1024 * 1024;
    public static final int DEFAULT_MAX_VALUES_PER_FIELD = 10;

    private Set<String> includeFields = new HashSet<>();
    private Set<String> excludeFields = new HashSet<>();
    private int maxKeySize = DEFAULT_MAX_KEY_SIZE;
    private int maxFieldSize = DEFAULT_MAX_FIELD_SIZE;
    private int maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;
    private int maxValuesPerField = DEFAULT_MAX_VALUES_PER_FIELD;
    private boolean includeEmpty = false;

    @Override
    public MetadataWriteLimiter newInstance() {
        if (maxFieldSize < 0) {
            throw new IllegalArgumentException("maxFieldSize must be > 0");
        }

        if (maxValuesPerField < 1) {
            throw new IllegalArgumentException("maxValuesPerField must be > 0");
        }

        if (maxTotalBytes < 0) {
            throw new IllegalArgumentException("maxTotalBytes must be > 0");
        }

        return new StandardMetadataLimiter(maxKeySize, maxFieldSize,
                maxTotalBytes, maxValuesPerField, includeFields,
                excludeFields, includeEmpty);
    }

    public void setIncludeFields(Set<String> includeFields) {
        this.includeFields = includeFields != null ? new HashSet<>(includeFields) : new HashSet<>();
    }

    public void setExcludeFields(Set<String> excludeFields) {
        this.excludeFields = excludeFields != null ? new HashSet<>(excludeFields) : new HashSet<>();
    }

    public void setMaxTotalBytes(int maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }

    public void setMaxKeySize(int maxKeySize) {
        this.maxKeySize = maxKeySize;
    }

    public void setMaxFieldSize(int maxFieldSize) {
        this.maxFieldSize = maxFieldSize;
    }

    public void setIncludeEmpty(boolean includeEmpty) {
        this.includeEmpty = includeEmpty;
    }

    public void setMaxValuesPerField(int maxValuesPerField) {
        this.maxValuesPerField = maxValuesPerField;
    }

    public Set<String> getIncludeFields() {
        return includeFields;
    }

    public Set<String> getExcludeFields() {
        return excludeFields;
    }

    public int getMaxKeySize() {
        return maxKeySize;
    }

    public int getMaxFieldSize() {
        return maxFieldSize;
    }

    public int getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public int getMaxValuesPerField() {
        return maxValuesPerField;
    }

    public boolean isIncludeEmpty() {
        return includeEmpty;
    }

    @Override
    public String toString() {
        return "StandardMetadataLimiterFactory{" +
                "maxKeySize=" + maxKeySize +
                ", maxFieldSize=" + maxFieldSize +
                ", maxTotalBytes=" + maxTotalBytes +
                ", maxValuesPerField=" + maxValuesPerField +
                ", includeFields=" + includeFields +
                ", excludeFields=" + excludeFields +
                ", includeEmpty=" + includeEmpty +
                '}';
    }
}
