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
package org.apache.tika.emitter;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fetcher.FetchPrefixKeyPair;
import org.apache.tika.fetcher.Fetcher;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that will apply the appropriate fetcher
 * to the fetcherString based on the prefix.
 *
 * This does not allow multiple fetchers supporting the same prefix.
 */
public class DefaultEmitter implements Emitter {

    private final Map<String, Emitter> emitterMap = new ConcurrentHashMap<>();

    private static List<Emitter> getDefaultFilters(
            ServiceLoader loader) {
        return loader.loadStaticServiceProviders(Emitter.class);
    }


    public DefaultEmitter(ServiceLoader serviceLoader) {
        this(getDefaultFilters(serviceLoader));
    }

    public DefaultEmitter(List<Emitter> emitters) {
        for (Emitter emitter : emitters) {
            for (String name : emitter.getSupported()) {
                if (emitterMap.containsKey(name)) {
                    throw new IllegalArgumentException(
                            "Multiple emitters cannot support the same name: "
                            + name);
                }
                emitterMap.put(name, emitter);
            }
        }
    }

    @Override
    public Set<String> getSupported() {
        return emitterMap.keySet();
    }


    @Override
    public void emit(String emitterName, List<Metadata> metadata)
            throws IOException, TikaException {

        Emitter emitter = emitterMap.get(emitterName);
        if (emitter == null) {
            throw new IllegalArgumentException("Can't find fetcher for prefix: "+
                    emitterName);
        }
        emitter.emit(emitterName, metadata);
    }
}
