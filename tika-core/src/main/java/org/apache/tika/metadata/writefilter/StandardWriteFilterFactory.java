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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class for {@link StandardWriteFilter}. See that class
 * for how the estimated sizes are calculated on Strings.
 */
public class StandardWriteFilterFactory implements MetadataWriteFilterFactory {


    public static int DEFAULT_MAX_KEY_SIZE = 1024;
    public static int DEFAULT_MAX_FIELD_SIZE = 100 * 1024;
    public static int DEFAULT_TOTAL_ESTIMATED_BYTES = 10 * 1024 * 1024;
    public static int DEFAULT_MAX_VALUES_PER_FIELD = 10;

    private Set<String> includeFields = Collections.EMPTY_SET;
    private Set<String> excludeFields = Collections.EMPTY_SET;
    private int maxKeySize = DEFAULT_MAX_KEY_SIZE;
    private int maxFieldSize = DEFAULT_MAX_FIELD_SIZE;
    private int maxTotalEstimatedBytes = DEFAULT_TOTAL_ESTIMATED_BYTES;
    private int maxValuesPerField = DEFAULT_MAX_VALUES_PER_FIELD;
    private boolean includeEmpty = false;

    public MetadataWriteFilter newInstance() {

        if (maxFieldSize < 0) {
            throw new IllegalArgumentException("maxFieldSize must be > 0");
        }

        if (maxValuesPerField < 1) {
            throw new IllegalArgumentException("maxValuesPerField must be > 0");
        }

        if (maxTotalEstimatedBytes < 0) {
            throw new IllegalArgumentException("max estimated size must be > 0");
        }

        return new StandardWriteFilter(maxKeySize, maxFieldSize,
                maxTotalEstimatedBytes, maxValuesPerField, includeFields,
                excludeFields, includeEmpty);
    }

    public void setIncludeFields(List<String> includeFields) {
        Set<String> keys = ConcurrentHashMap.newKeySet(includeFields.size());
        keys.addAll(includeFields);
        this.includeFields = Collections.unmodifiableSet(keys);
    }

    public void setExcludeFields(List<String> excludeFields) {
        Set<String> keys = ConcurrentHashMap.newKeySet(excludeFields.size());
        keys.addAll(excludeFields);
        this.excludeFields = Collections.unmodifiableSet(keys);
    }

    public void setMaxTotalEstimatedBytes(int maxTotalEstimatedBytes) {
        this.maxTotalEstimatedBytes = maxTotalEstimatedBytes;
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

    public int getMaxKeySize() {
        return maxKeySize;
    }

    public int getMaxFieldSize() {
        return maxFieldSize;
    }

    public int getMaxTotalEstimatedBytes() {
        return maxTotalEstimatedBytes;
    }

    public int getMaxValuesPerField() {
        return maxValuesPerField;
    }

    public boolean isIncludeEmpty() {
        return includeEmpty;
    }

    @Override
    public String toString() {
        return "StandardWriteFilterFactory{" + "includeFields=" + includeFields + ", maxKeySize=" +
                maxKeySize + ", maxFieldSize=" + maxFieldSize + ", maxTotalEstimatedBytes=" +
                maxTotalEstimatedBytes + ", maxValuesPerField=" + maxValuesPerField +
                ", includeEmpty=" + includeEmpty + '}';
    }
}
