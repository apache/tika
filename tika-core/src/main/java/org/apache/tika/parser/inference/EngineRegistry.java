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
package org.apache.tika.parser.inference;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code "engines"} map: user-chosen name to engine, built once at config load and
 * closed once with it.
 */
public final class EngineRegistry implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EngineRegistry.class);

    private final Map<String, Engine> engines;

    public EngineRegistry(Map<String, Engine> engines) {
        this.engines = Collections.unmodifiableMap(new LinkedHashMap<>(engines));
    }

    public Engine get(String name) {
        return engines.get(name);
    }

    public Map<String, Engine> getEngines() {
        return engines;
    }

    /** Closes every engine; one failure does not skip the rest, the first is rethrown. */
    @Override
    public void close() throws IOException {
        IOException first = null;
        for (Map.Entry<String, Engine> e : engines.entrySet()) {
            try {
                e.getValue().close();
            } catch (IOException | RuntimeException ex) {
                LOG.warn("engine {} failed to close", e.getKey(), ex);
                if (first == null) {
                    first = ex instanceof IOException io ? io : new IOException(ex);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
