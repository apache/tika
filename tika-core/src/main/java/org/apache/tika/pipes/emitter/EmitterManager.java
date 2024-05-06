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
package org.apache.tika.pipes.emitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;

/**
 * Utility class that will apply the appropriate fetcher to the fetcherString based on the prefix.
 *
 * <p>This does not allow multiple fetchers supporting the same prefix.
 */
public class EmitterManager extends ConfigBase {

    private final Map<String, Emitter> emitterMap = new ConcurrentHashMap<>();

    public static EmitterManager load(Path tikaConfigPath) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(tikaConfigPath)) {
            return EmitterManager.buildComposite(
                    "emitters", EmitterManager.class, "emitter", Emitter.class, is);
        }
    }

    private EmitterManager() {}

    public EmitterManager(List<Emitter> emitters) {
        for (Emitter emitter : emitters) {
            if (emitterMap.containsKey(emitter.getName())) {
                throw new IllegalArgumentException(
                        "Multiple emitters cannot support the same name: " + emitter.getName());
            }
            emitterMap.put(emitter.getName(), emitter);
        }
    }

    public Set<String> getSupported() {
        return emitterMap.keySet();
    }

    public Emitter getEmitter(String emitterName) {
        Emitter emitter = emitterMap.get(emitterName);
        if (emitter == null) {
            throw new IllegalArgumentException("Can't find emitter for prefix: " + emitterName);
        }
        return emitter;
    }

    /**
     * Convenience method that returns an emitter if only one emitter is specified in the
     * tika-config file. If 0 or > 1 emitters are specified, this throws an
     * IllegalArgumentException.
     *
     * @return
     */
    public Emitter getEmitter() {
        if (emitterMap.size() == 0) {
            throw new IllegalArgumentException("emitters size must == 1 for the no arg call");
        }
        if (emitterMap.size() > 1) {
            throw new IllegalArgumentException(
                    "need to specify 'emitterName' if > 1 emitters are" + " available");
        }
        for (Emitter emitter : emitterMap.values()) {
            return emitter;
        }
        // this should be unreachable?!
        throw new IllegalArgumentException("emitters size must == 0");
    }
}
