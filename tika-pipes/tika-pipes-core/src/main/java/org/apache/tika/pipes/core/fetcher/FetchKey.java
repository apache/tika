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
package org.apache.tika.pipes.core.fetcher;

import java.io.Serializable;
import java.util.Objects;

/**
 * Pair of fetcherPluginId (which fetcher to call) and the key
 * to send to that fetcher to retrieve a specific file.
 */
public class FetchKey implements Serializable {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    private String fetcherPluginId;
    private String fetchKey;
    private long rangeStart = -1;
    private long rangeEnd = -1;

    //this is for serialization...yuck
    public FetchKey() {

    }

    public FetchKey(String fetcherPluginId, String fetchKey) {
        this(fetcherPluginId, fetchKey, -1, -1);
    }

    public FetchKey(String fetcherPluginId, String fetchKey, long rangeStart, long rangeEnd) {
        this.fetcherPluginId = fetcherPluginId;
        this.fetchKey = fetchKey;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    public String getFetcherPluginId() {
        return fetcherPluginId;
    }

    public String getFetchKey() {
        return fetchKey;
    }

    public boolean hasRange() {
        return rangeStart > -1 && rangeEnd > -1;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FetchKey fetchKey1 = (FetchKey) o;
        return rangeStart == fetchKey1.rangeStart && rangeEnd == fetchKey1.rangeEnd &&
                Objects.equals(fetcherPluginId, fetchKey1.fetcherPluginId) &&
                Objects.equals(fetchKey, fetchKey1.fetchKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fetcherPluginId, fetchKey, rangeStart, rangeEnd);
    }

    @Override
    public String toString() {
        return "FetchKey{" + "fetcherPluginId='" + fetcherPluginId + '\'' + ", fetchKey='" + fetchKey +
                '\'' + ", rangeStart=" + rangeStart + ", rangeEnd=" + rangeEnd + '}';
    }
}
