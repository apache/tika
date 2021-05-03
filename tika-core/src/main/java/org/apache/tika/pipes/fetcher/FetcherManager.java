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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers supporting the same name.
 */
public class FetcherManager extends ConfigBase {

    public static FetcherManager load(Path p) throws IOException, TikaConfigException {
        try (InputStream is =
                     Files.newInputStream(p)) {
            return FetcherManager.buildComposite("fetchers", FetcherManager.class,
                    "fetcher", Fetcher.class, is);
        }
    }
    private final Map<String, Fetcher> fetcherMap = new ConcurrentHashMap<>();

    public FetcherManager(List<Fetcher> fetchers) throws TikaConfigException {
        for (Fetcher fetcher : fetchers) {
            String name = fetcher.getName();
            if (name == null || name.trim().length() == 0) {
                throw new TikaConfigException("fetcher name must not be blank");
            }
            if (fetcherMap.containsKey(fetcher.getName())) {
                throw new TikaConfigException(
                        "Multiple fetchers cannot support the same prefix: " + fetcher.getName());
            }
            fetcherMap.put(fetcher.getName(), fetcher);
        }
    }

    public Fetcher getFetcher(String fetcherName) throws IOException, TikaException {
        Fetcher fetcher = fetcherMap.get(fetcherName);
        if (fetcher == null) {
            throw new IllegalArgumentException(
                    "Can't find fetcher for fetcherName: " + fetcherName + ". I've loaded: " +
                            fetcherMap.keySet());
        }
        return fetcher;
    }

    public Set<String> getSupported() {
        return fetcherMap.keySet();
    }
}
