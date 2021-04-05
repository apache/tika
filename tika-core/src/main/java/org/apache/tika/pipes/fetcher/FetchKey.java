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
package org.apache.tika.pipes.fetcher;

/**
 * Pair of fetcherName (which fetcher to call) and the key
 * to send to that fetcher to retrieve a specific file.
 */
public class FetchKey {
    private String fetcherName;
    private String fetchKey;

    //this is for serialization...yuck
    public FetchKey(){

    }

    public FetchKey(String fetcherName, String fetchKey) {
        this.fetcherName = fetcherName;
        this.fetchKey = fetchKey;
    }

    public String getFetcherName() {
        return fetcherName;
    }

    public String getFetchKey() {
        return fetchKey;
    }

    @Override
    public String toString() {
        return "FetcherKeyPair{" + "fetcherName='" + fetcherName + '\'' + ", fetchKey='" +
                fetchKey + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FetchKey fetchKey = (FetchKey) o;

        if (fetcherName != null ? !fetcherName.equals(fetchKey.fetcherName) :
                fetchKey.fetcherName != null) {
            return false;
        }
        return this.fetchKey != null ? this.fetchKey.equals(fetchKey.fetchKey) :
                fetchKey.fetchKey == null;
    }

    @Override
    public int hashCode() {
        int result = fetcherName != null ? fetcherName.hashCode() : 0;
        result = 31 * result + (fetchKey != null ? fetchKey.hashCode() : 0);
        return result;
    }
}
