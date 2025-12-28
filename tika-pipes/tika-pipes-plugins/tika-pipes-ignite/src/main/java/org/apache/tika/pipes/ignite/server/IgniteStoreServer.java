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
package org.apache.tika.pipes.ignite.server;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.ignite.ExtensionConfigDTO;

/**
 * Embedded Ignite server that hosts the distributed cache.
 * This runs as a background thread within the tika-grpc process.
 * Tika gRPC and forked PipesServer instances connect as clients.
 */
public class IgniteStoreServer implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(IgniteStoreServer.class);
    private static final String DEFAULT_CACHE_NAME = "tika-config-store";
    private static final String DEFAULT_INSTANCE_NAME = "TikaIgniteServer";
    
    private Ignite ignite;
    private final String cacheName;
    private final CacheMode cacheMode;
    private final String instanceName;
    
    public IgniteStoreServer() {
        this(DEFAULT_CACHE_NAME, CacheMode.REPLICATED, DEFAULT_INSTANCE_NAME);
    }
    
    public IgniteStoreServer(String cacheName, CacheMode cacheMode, String instanceName) {
        this.cacheName = cacheName;
        this.cacheMode = cacheMode;
        this.instanceName = instanceName;
    }
    
    /**
     * Start the Ignite server node in a background daemon thread.
     */
    public void startAsync() {
        Thread serverThread = new Thread(() -> {
            try {
                start();
            } catch (Exception e) {
                LOG.error("Failed to start Ignite server", e);
            }
        }, "IgniteServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Wait for server to initialize
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void start() throws Exception {
        LOG.info("Starting Ignite server: instance={}, cache={}, mode={}", 
            instanceName, cacheName, cacheMode);
        
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(instanceName);
        cfg.setClientMode(false); // Server mode
        cfg.setPeerClassLoadingEnabled(true);
        
        // Set work directory to /var/cache/tika to match Tika's cache location
        cfg.setWorkDirectory(System.getProperty("ignite.work.dir", "/var/cache/tika/ignite-work"));
        
        ignite = Ignition.start(cfg);
        
        CacheConfiguration<String, ExtensionConfigDTO> cacheCfg = 
            new CacheConfiguration<>(cacheName);
        cacheCfg.setCacheMode(cacheMode);
        cacheCfg.setBackups(cacheMode == CacheMode.PARTITIONED ? 1 : 0);
        
        IgniteCache<String, ExtensionConfigDTO> cache = ignite.getOrCreateCache(cacheCfg);
        
        LOG.info("Ignite server started successfully with cache: {}", cache.getName());
        LOG.info("Ignite topology: {} nodes", ignite.cluster().nodes().size());
    }
    
    public boolean isRunning() {
        return ignite != null;
    }
    
    @Override
    public void close() {
        if (ignite != null) {
            LOG.info("Stopping Ignite server: {}", instanceName);
            ignite.close();
            ignite = null;
        }
    }
}
