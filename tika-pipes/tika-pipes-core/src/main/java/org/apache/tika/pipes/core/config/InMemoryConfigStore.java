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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.plugins.ExtensionConfig;

/**
 * Default in-memory implementation of {@link ConfigStore} using a {@link ConcurrentHashMap}.
 * Thread-safe and suitable for single-instance deployments.
 */
public class InMemoryConfigStore implements ConfigStore {

    private final ConcurrentHashMap<String, ExtensionConfig> store = new ConcurrentHashMap<>();

    @Override
    public void put(String id, ExtensionConfig config) {
        store.put(id, config);
    }

    @Override
    public ExtensionConfig get(String id) {
        return store.get(id);
    }

    @Override
    public boolean containsKey(String id) {
        return store.containsKey(id);
    }

    @Override
    public Set<String> keySet() {
        return Set.copyOf(store.keySet());
    }

    @Override
    public int size() {
        return store.size();
    }
}
