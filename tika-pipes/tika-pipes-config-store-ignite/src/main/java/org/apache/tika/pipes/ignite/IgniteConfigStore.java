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

import java.util.HashSet;
import java.util.Set;

import org.apache.ignite.Ignite;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.ignite.config.IgniteConfigStoreConfig;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Apache Ignite 3.x-based implementation of {@link ConfigStore}.
 * Provides distributed configuration storage for Tika Pipes using the Ignite 3.x client API.
 * <p>
 * This implementation is thread-safe and suitable for multi-instance deployments
 * where configurations need to be shared across multiple servers.
 * <p>
 * Note: This uses Ignite 3.x with built-in Apache Calcite SQL engine (no H2 dependency).
 */
public class IgniteConfigStore implements ConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteConfigStore.class);
    private static final String DEFAULT_TABLE_NAME = "tika_config_store";
    private static final String DEFAULT_INSTANCE_NAME = "TikaIgniteConfigStore";

    private Ignite ignite;
    private KeyValueView<String, ExtensionConfigDTO> kvView;
    private String tableName = DEFAULT_TABLE_NAME;
    private int replicas = 2;
    private int partitions = 10;
    private String igniteInstanceName = DEFAULT_INSTANCE_NAME;
    private boolean autoClose = true;
    private ExtensionConfig extensionConfig;
    private boolean closed = false;

    public IgniteConfigStore() {
    }

    public IgniteConfigStore(ExtensionConfig extensionConfig) throws TikaConfigException {
        this.extensionConfig = extensionConfig;

        IgniteConfigStoreConfig config = IgniteConfigStoreConfig.load(extensionConfig.json());
        this.tableName = config.getTableName();
        this.replicas = config.getReplicas();
        this.partitions = config.getPartitions();
        this.igniteInstanceName = config.getIgniteInstanceName();
        this.autoClose = config.isAutoClose();
    }

    public IgniteConfigStore(String tableName) {
        this.tableName = tableName;
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

        LOG.info("Initializing IgniteConfigStore with table: {}, replicas: {}, partitions: {}, instance: {}",
                tableName, replicas, partitions, igniteInstanceName);

        try {
            ignite = org.apache.ignite.client.IgniteClient.builder()
                    .addresses("127.0.0.1:10800")
                    .build();

            Table table = ignite.tables().table(tableName);
            if (table == null) {
                throw new IllegalStateException("Table " + tableName + " not found. Ensure IgniteStoreServer is running.");
            }

            kvView = table.keyValueView(String.class, ExtensionConfigDTO.class);

            LOG.info("IgniteConfigStore initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize IgniteConfigStore", e);
            throw new TikaConfigException("Failed to connect to Ignite cluster. Ensure IgniteStoreServer is running.", e);
        }
    }

    @Override
    public void put(String id, ExtensionConfig config) {
        checkInitialized();
        try {
            kvView.put(null, id, new ExtensionConfigDTO(config.name(), config.json()));
        } catch (Exception e) {
            LOG.error("Failed to put config with id: {}", id, e);
            throw new RuntimeException("Failed to put config", e);
        }
    }

    @Override
    public ExtensionConfig get(String id) {
        checkInitialized();
        try {
            ExtensionConfigDTO dto = kvView.get(null, id);
            return dto != null ? new ExtensionConfig(id, dto.getName(), dto.getJson()) : null;
        } catch (Exception e) {
            LOG.error("Failed to get config with id: {}", id, e);
            throw new RuntimeException("Failed to get config", e);
        }
    }

    @Override
    public boolean containsKey(String id) {
        checkInitialized();
        try {
            return kvView.get(null, id) != null;
        } catch (Exception e) {
            LOG.error("Failed to check if key exists: {}", id, e);
            throw new RuntimeException("Failed to check key", e);
        }
    }

    @Override
    public Set<String> keySet() {
        checkInitialized();
        try {
            var resultSet = ignite.sql().execute(null, "SELECT id FROM " + tableName);
            Set<String> keys = new HashSet<>();
            while (resultSet.hasNext()) {
                keys.add(resultSet.next().stringValue("id"));
            }
            return keys;
        } catch (Exception e) {
            LOG.error("Failed to get keySet", e);
            throw new RuntimeException("Failed to get keySet", e);
        }
    }

    @Override
    public int size() {
        checkInitialized();
        try {
            var resultSet = ignite.sql().execute(null, "SELECT COUNT(*) as cnt FROM " + tableName);
            if (resultSet.hasNext()) {
                return (int) resultSet.next().longValue("cnt");
            }
            return 0;
        } catch (Exception e) {
            LOG.error("Failed to get size", e);
            throw new RuntimeException("Failed to get size", e);
        }
    }

    @Override
    public ExtensionConfig remove(String id) {
        checkInitialized();
        try {
            ExtensionConfigDTO removed = kvView.getAndRemove(null, id);
            return removed != null ? new ExtensionConfig(id, removed.getName(), removed.getJson()) : null;
        } catch (Exception e) {
            LOG.error("Failed to remove config with id: {}", id, e);
            throw new RuntimeException("Failed to remove config", e);
        }
    }

    public void close() {
        if (ignite != null && autoClose) {
            LOG.info("Closing IgniteConfigStore");
            try {
                ((AutoCloseable) ignite).close();
            } catch (Exception e) {
                LOG.error("Error closing Ignite", e);
            }
            ignite = null;
            kvView = null;
            closed = true;
        }
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public void setIgniteInstanceName(String igniteInstanceName) {
        this.igniteInstanceName = igniteInstanceName;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    private void checkInitialized() {
        if (kvView == null) {
            throw new IllegalStateException("IgniteConfigStore not initialized. Call init() first.");
        }
    }
}
