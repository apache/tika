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
package org.apache.tika.config.loader;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.EngineRegistry;

/**
 * Loads {@code "engines"}: a map of user-chosen name to one engine,
 * {@code {"clip": {"openai-embedding-engine": {...}}}}. Null when the key is absent.
 */
class EngineLoader implements ComponentLoader<EngineRegistry> {

    static final String KEY = "engines";
    private static final Pattern LEGAL_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public EngineRegistry load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        JsonNode node = config.getRootNode().get(KEY);
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            throw new TikaConfigException("\"" + KEY + "\" must be an object keyed by engine "
                    + "name, e.g. {\"clip\": {\"openai-embedding-engine\": {...}}}");
        }
        Map<String, Engine> engines = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String name = e.getKey();
            if (!LEGAL_NAME.matcher(name).matches()) {
                throw new TikaConfigException("engine name \"" + name + "\" may use only "
                        + "letters, digits, '.', '_' and '-'");
            }
            JsonNode value = e.getValue();
            if (!value.isObject() || value.size() != 1) {
                throw new TikaConfigException("engine \"" + name + "\" must be one "
                        + "{\"<engine-type>\": {...}} object");
            }
            Map.Entry<String, JsonNode> type = value.fields().next();
            Engine engine;
            try {
                engine = ComponentInstantiator.instantiateComponent(type.getKey(),
                        type.getValue(), context.getObjectMapper(), context.getClassLoader(),
                        Engine.class);
            } catch (TikaConfigException ex) {
                throw new TikaConfigException("engine \"" + name + "\": " + ex.getMessage(), ex);
            }
            engines.put(name, engine);
        }
        return new EngineRegistry(engines);
    }
}
