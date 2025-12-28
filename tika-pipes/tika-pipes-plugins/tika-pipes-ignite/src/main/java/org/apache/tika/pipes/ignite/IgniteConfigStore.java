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
package org.apache.tika.pipes.ignite;

import java.util.Set;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.ignite.config.IgniteConfigStoreConfig;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Apache Ignite-based implementation of {@link ConfigStore}.
 * Provides distributed configuration storage for Tika Pipes clustering.
 * <p>
 * This implementation is thread-safe and suitable for multi-instance deployments
 * where configurations need to be shared across multiple servers.
 * <p>
 * Configuration options:
 * <ul>
 *   <li>cacheName - Name of the Ignite cache (default: "tika-config-store")</li>
 *   <li>cacheMode - Cache replication mode: PARTITIONED or REPLICATED (default: REPLICATED)</li>
 *   <li>igniteInstanceName - Name of the Ignite instance (default: "TikaIgniteConfigStore")</li>
 * </ul>
 */
public class IgniteConfigStore implements ConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteConfigStore.class);
    private static final String DEFAULT_CACHE_NAME = "tika-config-store";
    private static final String DEFAULT_INSTANCE_NAME = "TikaIgniteConfigStore";

    private Ignite ignite;
    private IgniteCache<String, ExtensionConfigDTO> cache;
    private String cacheName = DEFAULT_CACHE_NAME;
    private CacheMode cacheMode = CacheMode.REPLICATED;
    private String igniteInstanceName = DEFAULT_INSTANCE_NAME;
    private boolean autoClose = true;
    private ExtensionConfig extensionConfig;
    private boolean closed = false;

    public IgniteConfigStore() {
    }

    public IgniteConfigStore(ExtensionConfig extensionConfig) throws TikaConfigException {
        this.extensionConfig = extensionConfig;
        
        IgniteConfigStoreConfig config = IgniteConfigStoreConfig.load(extensionConfig.json());
        this.cacheName = config.getCacheName();
        this.cacheMode = config.getCacheModeEnum();
        this.igniteInstanceName = config.getIgniteInstanceName();
        this.autoClose = config.isAutoClose();
    }

    public IgniteConfigStore(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return extensionConfig;
    }

    @Override
    public void init() throws Exception {
        if (closed) {
            throw new IllegalStateException("Cannot reinitialize IgniteConfigStore after it has been closed");
        }
        if (ignite != null) {
            LOG.warn("IgniteConfigStore already initialized");
            return;
        }

        LOG.info("Initializing IgniteConfigStore with cache: {}, mode: {}, instance: {}",
                cacheName, cacheMode, igniteInstanceName);

        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(igniteInstanceName);
        cfg.setClientMode(false);

        ignite = Ignition.start(cfg);

        CacheConfiguration<String, ExtensionConfigDTO> cacheCfg = new CacheConfiguration<>(cacheName);
        cacheCfg.setCacheMode(cacheMode);
        cacheCfg.setBackups(cacheMode == CacheMode.PARTITIONED ? 1 : 0);

        cache = ignite.getOrCreateCache(cacheCfg);
        LOG.info("IgniteConfigStore initialized successfully");
    }

    @Override
    public void put(String id, ExtensionConfig config) {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        cache.put(id, new ExtensionConfigDTO(config));
    }

    @Override
    public ExtensionConfig get(String id) {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        ExtensionConfigDTO dto = cache.get(id);
        return dto != null ? dto.toExtensionConfig() : null;
    }

    @Override
    public boolean containsKey(String id) {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        return cache.containsKey(id);
    }

    @Override
    public Set<String> keySet() {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        return Set.copyOf(cache.query(new org.apache.ignite.cache.query.ScanQuery<String, ExtensionConfigDTO>())
                .getAll()
                .stream()
                .map(javax.cache.Cache.Entry::getKey)
                .toList());
    }

    @Override
    public int size() {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        return cache.size();
    }

    @Override
    public ExtensionConfig remove(String id) {
        if (cache == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
        ExtensionConfigDTO removed = cache.getAndRemove(id);
        return removed != null ? removed.toExtensionConfig() : null;
    }

    public void close() {
        if (ignite != null && autoClose) {
            LOG.info("Closing IgniteConfigStore");
            ignite.close();
            ignite = null;
            cache = null;
            closed = true;
        }
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public void setCacheMode(CacheMode cacheMode) {
        this.cacheMode = cacheMode;
    }

    public void setIgniteInstanceName(String igniteInstanceName) {
        this.igniteInstanceName = igniteInstanceName;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }
}
