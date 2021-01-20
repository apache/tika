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
package org.apache.tika.fetcher;

public class FetchPrefixKeyPair {
    private final String prefix;
    private final String key;

    private FetchPrefixKeyPair(String prefix, String key) {
        this.prefix = prefix;
        this.key = key;
    }

    public static FetchPrefixKeyPair create(String fetcherString) throws FetcherStringException {
        int prefixIndex = fetcherString.indexOf(":");
        if (prefixIndex < 0) {
            throw new FetcherStringException("Can't find fetcher prefix, e.g. the 's3' in s3:/myfile");
        }
        String prefix = fetcherString.substring(0, prefixIndex);
        String key = fetcherString.substring(prefixIndex+1);
        return new FetchPrefixKeyPair(prefix, key);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "FetchPrefixKeyPair{" +
                "prefix='" + prefix + '\'' +
                ", key='" + key + '\'' +
                '}';
    }
}
