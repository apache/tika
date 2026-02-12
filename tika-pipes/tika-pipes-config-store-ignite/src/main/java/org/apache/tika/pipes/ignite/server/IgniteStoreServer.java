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
import org.apache.ignite.InitParameters;
import org.apache.ignite.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified Ignite 3.x embedded server.
 * Based on Apache Ignite 3 examples - starts synchronously.
 */
public class IgniteStoreServer implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(IgniteStoreServer.class);
    private static final String DEFAULT_TABLE_NAME = "tika_config_store";
    private static final String DEFAULT_NODE_NAME = "TikaIgniteServer";
    
    private IgniteServer node;
    private final String tableName;
    private final String nodeName;
    private final Path workDir;
    
    public IgniteStoreServer() {
        this(DEFAULT_TABLE_NAME, DEFAULT_NODE_NAME);
    }
    
    public IgniteStoreServer(String tableName, String nodeName) {
        this.tableName = tableName;
        this.nodeName = nodeName;
        this.workDir = Paths.get(System.getProperty("ignite.work.dir", "/var/cache/tika/ignite-work"));
    }
    
    /**
     * Start the Ignite server synchronously.
     */
    public void start() throws Exception {
        LOG.info("Starting Ignite 3.x server: node={}, table={}, workDir={}", 
            nodeName, tableName, workDir);
        
        // Clean and recreate work directory for fresh start
        if (Files.exists(workDir)) {
            LOG.info("Cleaning existing work directory");
            deleteDirectory(workDir);
        }
        
        // Ensure work directory exists
        Files.createDirectories(workDir);
        LOG.info("Created work directory: {}", workDir);
        
        // Create config file
        Path configPath = workDir.resolve("ignite-config.conf");
        String config = 
            "ignite {\n" +
            "  network {\n" +
            "    port = 3344\n" +
            "    nodeFinder {\n" +
            "      netClusterNodes = [ \"localhost:3344\" ]\n" +
            "    }\n" +
            "  }\n" +
            "  clientConnector {\n" +
            "    port = 10800\n" +
            "  }\n" +
            "}\n";
        Files.writeString(configPath, config);
        LOG.info("Created Ignite config: {}", configPath);
        
        // Start the server node
        LOG.info("Starting Ignite node: {}", nodeName);
        node = IgniteServer.start(nodeName, configPath, workDir);
        LOG.info("Ignite server started");
        
        // Initialize the cluster
        LOG.info("Initializing cluster");
        InitParameters initParameters = InitParameters.builder()
                .clusterName("tika-cluster")
                .metaStorageNodes(node)
                .build();
        
        node.initClusterAsync(initParameters).get();
        LOG.info("Cluster initialized");
        
        // Wait for cluster to be ready
        Thread.sleep(2000);
        
        // Create table
        createTable();
        
        LOG.info("Ignite server is ready");
    }
    
    private void createTable() {
        try {
            // Check if table exists
            Table existingTable = node.api().tables().table(tableName);
            if (existingTable != null) {
                LOG.info("Table {} already exists", tableName);
                return;
            }
            
            LOG.info("Creating table: {}", tableName);
            
            // Create table using SQL (Ignite 3.x uses default zone)
            String createTableSql = String.format(Locale.ROOT,
                "CREATE TABLE IF NOT EXISTS %s (" +
                "  id VARCHAR PRIMARY KEY," +
                "  name VARCHAR," +
                "  json VARCHAR(10000)" +
                ")",
                tableName
            );
            
            LOG.info("Creating table with SQL: {}", createTableSql);
            node.api().sql().execute(null, createTableSql);
            
            LOG.info("Table {} created successfully", tableName);
        } catch (Exception e) {
            LOG.error("Failed to create table: {}", tableName, e);
            throw new RuntimeException("Failed to create table", e);
        }
    }
    
    public boolean isRunning() {
        return node != null;
    }
    
    private void deleteDirectory(Path dir) throws Exception {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before dirs
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        LOG.warn("Failed to delete {}", path, e);
                    }
                });
        }
    }
    
    @Override
    public void close() {
        if (node != null) {
            LOG.info("Stopping Ignite server: {}", nodeName);
            node.shutdown();
            node = null;
        }
    }
}
