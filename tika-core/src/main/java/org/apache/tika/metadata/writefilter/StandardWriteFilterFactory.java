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

    private Set<String> includeFields = null;
    private int maxKeySize = DEFAULT_MAX_KEY_SIZE;
    private int maxFieldSize = DEFAULT_MAX_FIELD_SIZE;
    private int maxTotalEstimatedBytes = DEFAULT_TOTAL_ESTIMATED_BYTES;
    private boolean includeEmpty = false;

    public MetadataWriteFilter newInstance() {
        return new StandardWriteFilter(maxKeySize, maxFieldSize,
                maxTotalEstimatedBytes, includeFields, includeEmpty);
    }

    public void setIncludeFields(List<String> includeFields) {
        Set<String> keys = ConcurrentHashMap.newKeySet(includeFields.size());
        keys.addAll(includeFields);
        this.includeFields = Collections.unmodifiableSet(keys);
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

    public int getMaxTotalEstimatedBytes() {
        return maxTotalEstimatedBytes;
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

    public boolean isIncludeEmpty() {
        return includeEmpty;
    }

    @Override
    public String toString() {
        return "StandardWriteFilterFactory{" + "includeFields=" + includeFields + ", maxKeySize=" +
                maxKeySize + ", maxFieldSize=" + maxFieldSize + ", maxTotalEstimatedBytes=" +
                maxTotalEstimatedBytes + ", includeEmpty=" + includeEmpty + '}';
    }
}
