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
package org.apache.tika.pipes.core.statestore;

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.pipes.api.statestore.StateStoreFactory;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Factory for creating InMemoryStateStore instances.
 */
@Extension
public class InMemoryStateStoreFactory implements StateStoreFactory {

    @Override
    public String getName() {
        return "in-memory";
    }

    @Override
    public StateStore buildExtension(ExtensionConfig extensionConfig)
            throws IOException, TikaConfigException {
        InMemoryStateStore store = new InMemoryStateStore();
        store.setExtensionConfig(extensionConfig);
        // In-memory store doesn't need configuration, but we initialize it anyway
        try {
            store.initialize(null);
        } catch (StateStoreException e) {
            throw new TikaConfigException("Failed to initialize InMemoryStateStore", e);
        }
        return store;
    }
}
