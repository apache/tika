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
package org.apache.tika.pipes.ignite.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Configuration for {@link org.apache.tika.pipes.ignite.IgniteConfigStore}.
 *
 * <p>Example JSON configuration:
 * <pre>
 * {
 *   "tableName": "my-tika-table",
 *   "replicas": 2,
 *   "igniteInstanceName": "MyTikaCluster",
 *   "autoClose": true
 * }
 * </pre>
 */
public class IgniteConfigStoreConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static IgniteConfigStoreConfig load(final String json) throws TikaConfigException {
        try {
            if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
                return new IgniteConfigStoreConfig();
            }
            return OBJECT_MAPPER.readValue(json, IgniteConfigStoreConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to parse IgniteConfigStoreConfig from JSON", e);
        }
    }

    private String tableName = "tika_config_store";
    private int replicas = 2;
    private String igniteInstanceName = "TikaIgniteConfigStore";
    private boolean autoClose = true;
    private int partitions = 10;

    public String getTableName() {
        return tableName;
    }

    public IgniteConfigStoreConfig setTableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public int getReplicas() {
        return replicas;
    }

    public IgniteConfigStoreConfig setReplicas(int replicas) {
        this.replicas = replicas;
        return this;
    }

    public String getIgniteInstanceName() {
        return igniteInstanceName;
    }

    public IgniteConfigStoreConfig setIgniteInstanceName(String igniteInstanceName) {
        this.igniteInstanceName = igniteInstanceName;
        return this;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public IgniteConfigStoreConfig setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }

    public int getPartitions() {
        return partitions;
    }

    public IgniteConfigStoreConfig setPartitions(int partitions) {
        this.partitions = partitions;
        return this;
    }
}
