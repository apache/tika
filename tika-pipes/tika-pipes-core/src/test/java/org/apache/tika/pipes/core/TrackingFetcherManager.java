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
package org.apache.tika.pipes.core;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.pf4j.PluginManager;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.pipes.core.config.InMemoryConfigStore;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;

/** A manager of {@link TrackedFetcher}s, no plugins needed; records every instance it builds. */
public class TrackingFetcherManager extends AbstractComponentManager<Fetcher, FetcherFactory> {

    public static final String TYPE = "tracked";

    private final List<TrackedFetcher> built = new CopyOnWriteArrayList<>();

    private final FetcherFactory factory = new FetcherFactory() {
        @Override
        public String getName() {
            return TYPE;
        }

        @Override
        public Fetcher buildExtension(ExtensionConfig config) {
            TrackedFetcher fetcher = new TrackedFetcher(config);
            built.add(fetcher);
            return fetcher;
        }

        @Override
        public Class<?> getConfigClass() {
            return Object.class;
        }
    };

    public TrackingFetcherManager(String... configuredIds) {
        super(null, configs(configuredIds), true, new InMemoryConfigStore());
    }

    public static ExtensionConfig config(String id) {
        return new ExtensionConfig(id, TYPE, "{}");
    }

    private static Map<String, ExtensionConfig> configs(String... ids) {
        Map<String, ExtensionConfig> configs = new HashMap<>();
        for (String id : ids) {
            configs.put(id, config(id));
        }
        return configs;
    }

    public List<TrackedFetcher> getBuilt() {
        return built;
    }

    @Override
    protected Map<String, FetcherFactory> getFactories(PluginManager pluginManager) {
        return Map.of(TYPE, factory);
    }

    @Override
    protected String getConfigKey() {
        return "fetchers";
    }

    @Override
    protected Class<FetcherFactory> getFactoryClass() {
        return FetcherFactory.class;
    }

    @Override
    protected String getComponentName() {
        return "fetcher";
    }

    @Override
    protected TikaException createNotFoundException(String message) {
        return new FetcherNotFoundException(message);
    }

    public static class TrackedFetcher extends AbstractTikaExtension implements Fetcher, Closeable {

        private final AtomicInteger closes = new AtomicInteger();

        TrackedFetcher(ExtensionConfig config) {
            super(config);
        }

        public int getCloses() {
            return closes.get();
        }

        @Override
        public TikaInputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) {
            if (closes.get() > 0) {
                throw new IllegalStateException("fetch after close");
            }
            return TikaInputStream.get(new byte[0]);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
