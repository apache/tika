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
package org.apache.tika.pipes.core.emitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.EmitterFactory;
import org.apache.tika.plugins.PluginComponentLoader;
import org.apache.tika.plugins.TikaConfigs;

/**
 * Utility class that will apply the appropriate emitter
 * to the emitterString based on the prefix.
 * <p>
 * This does not allow multiple emitters supporting the same prefix.
 */
public class EmitterManager {
    public static final String CONFIG_KEY = "emitters";

    private static final Logger LOG = LoggerFactory.getLogger(EmitterManager.class);

    private final Map<String, Emitter> emitterMap = new ConcurrentHashMap<>();

    public static EmitterManager load(PluginManager pluginManager, TikaConfigs tikaConfigs) throws IOException, TikaConfigException {
        JsonNode fetchersNode = tikaConfigs.getRoot().get(CONFIG_KEY);
        Map<String, Emitter> fetchers =
                PluginComponentLoader.loadInstances(pluginManager, EmitterFactory.class, fetchersNode);
        return new EmitterManager(fetchers);
    }

    private EmitterManager() {

    }

    private EmitterManager(Map<String, Emitter> emitters) {
        emitterMap.putAll(emitters);
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
     * Convenience method that returns an emitter if only one emitter
     * is specified in the tika-config file.  If 0 or > 1 emitters
     * are specified, this throws an IllegalArgumentException.
     * @return
     */
    public Emitter getEmitter() {
        if (emitterMap.isEmpty()) {
            throw new IllegalArgumentException("emitters size must == 1 for the no arg call");
        }
        if (emitterMap.size() > 1) {
            throw new IllegalArgumentException("need to specify 'emitterId' if > 1 emitters are" +
                    " available");
        }
        for (Emitter emitter : emitterMap.values()) {
            return emitter;
        }
        //this should be unreachable?!
        throw new IllegalArgumentException("emitters size must == 0");
    }
}
