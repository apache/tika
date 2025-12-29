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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.apache.ignite.IgniteServer;
import org.apache.ignite.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Ignite 3.x server that hosts the distributed table.
 * This runs as a background thread within the tika-grpc process.
 * Tika gRPC and forked PipesServer instances connect as clients.
 * 
 * Note: Uses Ignite 3.x with Calcite SQL engine (no H2).
 */
public class IgniteStoreServer implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(IgniteStoreServer.class);
    private static final String DEFAULT_TABLE_NAME = "tika_config_store";
    private static final String DEFAULT_NODE_NAME = "TikaIgniteServer";
    
    private IgniteServer ignite;
    private final String tableName;
    private final int replicas;
    private final int partitions;
    private final String nodeName;
    private final Path workDir;
    
    public IgniteStoreServer() {
        this(DEFAULT_TABLE_NAME, 2, 10, DEFAULT_NODE_NAME);
    }
    
    public IgniteStoreServer(String tableName, int replicas, int partitions, String nodeName) {
        this.tableName = tableName;
        this.replicas = replicas;
        this.partitions = partitions;
        this.nodeName = nodeName;
        this.workDir = Paths.get(System.getProperty("ignite.work.dir", "/var/cache/tika/ignite-work"));
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
            Thread.sleep(5000);  // Give it more time for Ignite 3.x initialization
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void start() throws Exception {
        LOG.info("Starting Ignite 3.x server: node={}, table={}, replicas={}, partitions={}", 
            nodeName, tableName, replicas, partitions);
        
        try {
            // Ensure work directory exists
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
                LOG.info("Created work directory: {}", workDir);
            }
            
            // Start the server node directly
            // Note: In Ignite 3.x embedded mode, the server manages its own initialization
            LOG.info("Starting Ignite node: {} at {}", nodeName, workDir);
            ignite = IgniteServer.start(nodeName, workDir, null);
            LOG.info("Ignite server started successfully");
            
            // Wait a bit for the cluster to be ready
            Thread.sleep(3000);
            
            // Create table if it doesn't exist
            createTableIfNeeded();
            
            LOG.info("Ignite server is ready");
        } catch (Exception e) {
            LOG.error("Failed to start Ignite server", e);
            throw e;
        }
    }
    
    private void createTableIfNeeded() {
        try {
            // Get the API interface from the server
            org.apache.ignite.Ignite api = ignite.api();
            
            Table existingTable = api.tables().table(tableName);
            if (existingTable != null) {
                LOG.info("Table {} already exists", tableName);
                return;
            }
            
            LOG.info("Creating table: {} with replicas={}, partitions={}", tableName, replicas, partitions);
            
            // Create table using SQL
            String createTableSql = String.format(Locale.ROOT,
                "CREATE TABLE IF NOT EXISTS %s (" +
                "  id VARCHAR PRIMARY KEY," +
                "  contextKey VARCHAR," +
                "  entityType VARCHAR," +
                "  factoryName VARCHAR," +
                "  json VARCHAR(10000)" +
                ") WITH PRIMARY_ZONE='%s_ZONE'",
                tableName, tableName.toUpperCase(Locale.ROOT)
            );
            
            // First create a distribution zone
            String createZoneSql = String.format(Locale.ROOT,
                "CREATE ZONE IF NOT EXISTS %s_ZONE WITH " +
                "REPLICAS=%d, " +
                "PARTITIONS=%d, " +
                "STORAGE_PROFILES='default'",
                tableName.toUpperCase(Locale.ROOT), replicas, partitions
            );
            
            LOG.info("Creating distribution zone with SQL: {}", createZoneSql);
            api.sql().execute(null, createZoneSql);
            
            LOG.info("Creating table with SQL: {}", createTableSql);
            api.sql().execute(null, createTableSql);
            
            LOG.info("Table {} created successfully", tableName);
        } catch (Exception e) {
            LOG.error("Failed to create table: {}", tableName, e);
            throw new RuntimeException("Failed to create table", e);
        }
    }
    
    public boolean isRunning() {
        return ignite != null;
    }
    
    @Override
    public void close() {
        if (ignite != null) {
            LOG.info("Stopping Ignite server: {}", nodeName);
            try {
                ((AutoCloseable) ignite).close();
            } catch (Exception e) {
                LOG.error("Error stopping Ignite server", e);
            }
            ignite = null;
        }
    }
}
