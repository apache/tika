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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StandardWriteFilterFactory implements MetadataWriteFilterFactory {

    public static int DEFAULT_MAX_SIZE = 10 * 1024 * 1024;
    private Set<String> includeFields = null;
    private int maxEstimatedBytes = DEFAULT_MAX_SIZE;
    private boolean includeEmpty = false;

    public MetadataWriteFilter newInstance() {
        return new StandardWriteFilter(maxEstimatedBytes, includeFields, includeEmpty);
    }

    public void setIncludeFields(List<String> includeFields) {
        Set<String> keys = ConcurrentHashMap.newKeySet(includeFields.size());
        keys.addAll(includeFields);
        this.includeFields = Collections.unmodifiableSet(keys);
    }

    public void setMaxEstimatedBytes(int maxEstimatedBytes) {
        this.maxEstimatedBytes = maxEstimatedBytes;
    }

    public void setIncludeEmpty(boolean includeEmpty) {
        this.includeEmpty = includeEmpty;
    }

    @Override
    public String toString() {
        return "WriteFilteringMetadataFactory{" + "includeFields=" + includeFields +
                ", maxEstimatedBytes=" + maxEstimatedBytes + ", includeEmpty=" + includeEmpty + '}';
    }

    public int getMaxEstimatedBytes() {
        return maxEstimatedBytes;
    }
}
