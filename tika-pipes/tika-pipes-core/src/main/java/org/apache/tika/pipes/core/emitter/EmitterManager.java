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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.core.PipesPluginsConfig;
import org.apache.tika.plugins.PluginConfig;

/**
 * Utility class that will apply the appropriate emitter
 * to the emitterString based on the prefix.
 * <p>
 * This does not allow multiple emitters supporting the same prefix.
 */
public class EmitterManager {

    private static final Logger LOG = LoggerFactory.getLogger(EmitterManager.class);


    private final Map<String, Emitter> emitterMap = new ConcurrentHashMap<>();

    public static EmitterManager load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    public static EmitterManager load(InputStream pipesPluginsConfigIs) throws IOException, TikaConfigException {
        PipesPluginsConfig pluginsConfig = PipesPluginsConfig.load(pipesPluginsConfigIs);
        PluginManager pluginManager = null;
        if (pluginsConfig.getPluginsDir().isPresent()) {
            pluginManager = new DefaultPluginManager(pluginsConfig.getPluginsDir().get());
        } else {
            pluginManager = new DefaultPluginManager();
        }
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        Map<String, Emitter> emitterMap = new HashMap<>();
        for (Emitter emitter : pluginManager.getExtensions(Emitter.class)) {
            LOG.warn("EMITTER PLUGIN ID: " + emitter.getPluginId() + " : " + emitter.getClass());
            Optional<PluginConfig> emitterConfig = pluginsConfig.getEmitterConfig(emitter.getPluginId());
            if (emitterConfig.isPresent()) {
                emitter.configure(emitterConfig.get());
                if (emitter instanceof Initializable) {
                    ((Initializable) emitter).checkInitialization(InitializableProblemHandler.THROW);
                }
            } else {
                LOG.warn("no configuration found for emitter pluginId={}", emitter.getPluginId());
            }
            emitterMap.put(emitter.getPluginId(), emitter);
        }
        return new EmitterManager(emitterMap);
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
        if (emitterMap.size() == 0) {
            throw new IllegalArgumentException("emitters size must == 1 for the no arg call");
        }
        if (emitterMap.size() > 1) {
            throw new IllegalArgumentException("need to specify 'emitterName' if > 1 emitters are" +
                    " available");
        }
        for (Emitter emitter : emitterMap.values()) {
            return emitter;
        }
        //this should be unreachable?!
        throw new IllegalArgumentException("emitters size must == 0");
    }
}
