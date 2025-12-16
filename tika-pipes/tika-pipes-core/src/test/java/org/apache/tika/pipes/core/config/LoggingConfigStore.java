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
package org.apache.tika.pipes.core.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.plugins.ExtensionConfig;

/**
 * Example custom ConfigStore implementation for demonstration purposes.
 * This implementation logs all operations and could be extended to add
 * persistence, caching, or other custom behavior.
 * Thread-safe through synchronized access to the underlying map.
 */
public class LoggingConfigStore implements ConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingConfigStore.class);
    private final Map<String, ExtensionConfig> store = new HashMap<>();
    
    @Override
    public void put(String id, ExtensionConfig config) {
        LOG.debug("ConfigStore: Storing config with id={}", id);
        synchronized (store) {
            store.put(id, config);
        }
    }

    @Override
    public ExtensionConfig get(String id) {
        synchronized (store) {
            ExtensionConfig config = store.get(id);
            if (config != null) {
                LOG.debug("ConfigStore: Retrieved config with id={}", id);
            } else {
                LOG.debug("ConfigStore: Config not found for id={}", id);
            }
            return config;
        }
    }

    @Override
    public boolean containsKey(String id) {
        synchronized (store) {
            return store.containsKey(id);
        }
    }

    @Override
    public Set<String> keySet() {
        synchronized (store) {
            return Set.copyOf(store.keySet());
        }
    }

    @Override
    public int size() {
        synchronized (store) {
            return store.size();
        }
    }
}
