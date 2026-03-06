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
import java.util.concurrent.ExecutionException;

import org.apache.ignite.IgniteServer;
import org.apache.ignite.InitParameters;
import org.apache.ignite.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Ignite 3.x server node that hosts the config store table.
 * The {@link org.apache.tika.pipes.ignite.IgniteConfigStore} connects to this node as a thin client.
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
        this.workDir = Paths.get(System.getProperty("ignite.work.dir",
                System.getProperty("java.io.tmpdir") + "/tika-ignite-work"));
    }

    /**
     * Start the Ignite server node and initialize the cluster synchronously.
     */
    public void start() throws Exception {
        LOG.info("Starting Ignite 3.x server: node={}, table={}, workDir={}",
                nodeName, tableName, workDir);

        if (Files.exists(workDir)) {
            deleteDirectory(workDir);
        }
        Files.createDirectories(workDir);

        Path configPath = workDir.resolve("ignite-config.conf");
        String config = """
                ignite {
                  network {
                    port = 3344
                    nodeFinder {
                      netClusterNodes = [ "localhost:3344" ]
                    }
                  }
                  clientConnector {
                    port = 10800
                  }
                }
                """;
        Files.writeString(configPath, config);

        node = IgniteServer.builder(nodeName, configPath, workDir)
                .serviceLoaderClassLoader(Thread.currentThread().getContextClassLoader())
                .build();
        try {
            node.startAsync().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.error("Ignite startup failed. Root cause: {}", cause.getMessage(), cause);
            throw new RuntimeException("Ignite server startup failed", cause);
        }

        InitParameters initParameters = InitParameters.builder()
                .clusterName("tika-cluster")
                .metaStorageNodes(node)
                .build();
        node.initClusterAsync(initParameters).get();

        Thread.sleep(2000);

        createTable();

        LOG.info("Ignite server is ready");
    }

    private void createTable() {
        try {
            // Create a single-replica distribution zone for single-node clusters.
            // Without REPLICAS=1, the default zone may require multiple replicas,
            // causing "Mandatory nodes was excluded from mapping" errors on 1-node clusters.
            String createZoneSql = "CREATE ZONE IF NOT EXISTS tika_zone " +
                    "WITH REPLICAS=1, PARTITIONS=10, STORAGE_PROFILES='default'";
            node.api().sql().execute(null, createZoneSql);
            LOG.info("Distribution zone 'tika_zone' created/verified");

            Table existingTable = node.api().tables().table(tableName);
            if (existingTable != null) {
                LOG.info("Table {} already exists", tableName);
                return;
            }

            String createTableSql = String.format(Locale.ROOT,
                    "CREATE TABLE IF NOT EXISTS %s (" +
                    "  id VARCHAR PRIMARY KEY," +
                    "  name VARCHAR," +
                    "  json VARCHAR(10000)" +
                    ") ZONE tika_zone",
                    tableName);

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
        Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        LOG.warn("Failed to delete {}", path, e);
                    }
                });
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
