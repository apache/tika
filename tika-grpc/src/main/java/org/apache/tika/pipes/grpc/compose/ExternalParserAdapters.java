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
package org.apache.tika.pipes.grpc.compose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.adapter.ExternalParserAdapter;

/**
 * The {@link ExternalParserAdapter}s on a classpath, found with {@link ServiceLoader} and
 * keyed by {@link ExternalParserAdapter#producerId()}. An adapter that fails to load, has no
 * producer id, or shares its id with another is recorded rather than thrown: it only stops
 * the server when a {@code grpc.externalParsers} entry asks for it, so a stray or broken jar
 * that nothing configures is logged and ignored.
 */
public final class ExternalParserAdapters {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParserAdapters.class);

    private final Map<String, ExternalParserAdapter<?, ?>> byProducerId;
    private final Map<String, String> unusable;
    private final List<String> loadFailures;

    private ExternalParserAdapters(Map<String, ExternalParserAdapter<?, ?>> byProducerId,
                                   Map<String, String> unusable, List<String> loadFailures) {
        this.byProducerId = Collections.unmodifiableMap(byProducerId);
        this.unusable = Collections.unmodifiableMap(unusable);
        this.loadFailures = List.copyOf(loadFailures);
    }

    /** The adapters {@code classLoader} can see. */
    @SuppressWarnings("rawtypes")
    public static ExternalParserAdapters load(ClassLoader classLoader) {
        Found found = new Found();
        Iterator<ExternalParserAdapter> providers =
                ServiceLoader.load(ExternalParserAdapter.class, classLoader).iterator();
        while (true) {
            ExternalParserAdapter adapter;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                adapter = providers.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                // ServiceLoader goes on to the next provider after a failed one.
                LOG.warn("could not load an external parser adapter: {}", e.toString());
                found.loadFailures.add(e.toString());
                continue;
            }
            found.add(adapter);
        }
        return found.build();
    }

    /** These adapters, for tests and for embedding without a service file. */
    public static ExternalParserAdapters of(ExternalParserAdapter<?, ?>... adapters) {
        Found found = new Found();
        for (ExternalParserAdapter<?, ?> adapter : adapters) {
            found.add(adapter);
        }
        return found.build();
    }

    private static final class Found {
        final Map<String, ExternalParserAdapter<?, ?>> byProducerId = new TreeMap<>();
        final Map<String, String> unusable = new TreeMap<>();
        final List<String> loadFailures = new ArrayList<>();

        void add(ExternalParserAdapter<?, ?> adapter) {
            String producerId;
            try {
                producerId = adapter.producerId();
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("external parser adapter {} failed to report its producerId: {}",
                        adapter.getClass().getName(), e.toString());
                loadFailures.add(adapter.getClass().getName() + ": " + e);
                return;
            }
            if (producerId == null || producerId.isBlank()) {
                LOG.warn("external parser adapter {} has no producerId",
                        adapter.getClass().getName());
                loadFailures.add(adapter.getClass().getName() + " has no producerId");
                return;
            }
            if (unusable.containsKey(producerId)) {
                return;
            }
            ExternalParserAdapter<?, ?> other = byProducerId.putIfAbsent(producerId, adapter);
            if (other != null) {
                byProducerId.remove(producerId);
                unusable.put(producerId, "two external parser adapters have producerId '"
                        + producerId + "': " + other.getClass().getName() + " and "
                        + adapter.getClass().getName());
                LOG.warn(unusable.get(producerId));
            }
        }

        ExternalParserAdapters build() {
            return new ExternalParserAdapters(byProducerId, unusable, loadFailures);
        }
    }

    /** The adapter for {@code producerId}, or {@code null} if there is none or it is unusable. */
    public ExternalParserAdapter<?, ?> get(String producerId) {
        return byProducerId.get(producerId);
    }

    /**
     * The adapter a {@code grpc.externalParsers} entry asks for.
     *
     * @throws IllegalArgumentException if there is none, or two share the id; the message
     *                                  lists the adapters found and any that failed to load
     */
    public ExternalParserAdapter<?, ?> require(String producerId) {
        String problem = unusable.get(producerId);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        ExternalParserAdapter<?, ?> adapter = byProducerId.get(producerId);
        if (adapter == null) {
            throw new IllegalArgumentException("no adapter jar for external parser '"
                    + producerId + "' is on the classpath; adapters found: "
                    + byProducerId.keySet()
                    + (loadFailures.isEmpty() ? "" : "; failed to load: " + loadFailures));
        }
        return adapter;
    }

    /** Every usable adapter found, by producer id. */
    public Map<String, ExternalParserAdapter<?, ?>> all() {
        return byProducerId;
    }
}
