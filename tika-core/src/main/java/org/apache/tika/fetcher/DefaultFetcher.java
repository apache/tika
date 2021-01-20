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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that will apply the appropriate fetcher
 * to the fetcherString based on the prefix.
 *
 * This forbids multiple fetchers supporting the same prefix.
 */
public class DefaultFetcher implements Fetcher {

    private final Map<String, Fetcher> fetcherMap = new ConcurrentHashMap<>();

    public DefaultFetcher(List<Fetcher> fetchers) {
        for (Fetcher fetcher : fetchers) {
            for (String supportedPrefix : fetcher.getSupportedPrefixes()) {
                if (fetcherMap.containsKey(supportedPrefix)) {
                    throw new IllegalArgumentException(
                            "Multiple fetchers cannot support the same prefix: "
                            + supportedPrefix);
                }
                fetcherMap.put(supportedPrefix, fetcher);
            }
        }
    }

    @Override
    public Set<String> getSupportedPrefixes() {
        return fetcherMap.keySet();
    }

    @Override
    public InputStream fetch(String fetcherString, Metadata metadata)
            throws IOException, TikaException {
        FetchPrefixKeyPair fetchPrefixKeyPair = FetchPrefixKeyPair.create(fetcherString);

        Fetcher fetcher = fetcherMap.get(fetchPrefixKeyPair.getPrefix());
        if (fetcher == null) {
            throw new IllegalArgumentException("Can't find fetcher for prefix: "+
                    fetchPrefixKeyPair.getPrefix());
        }
        return fetcher.fetch(fetcherString, metadata);
    }
}
