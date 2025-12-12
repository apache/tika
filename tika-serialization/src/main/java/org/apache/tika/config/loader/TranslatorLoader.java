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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;

/**
 * Loader for translators.
 * Only one translator is supported at a time.
 * <p>
 * JSON format uses wrapper object style:
 * <pre>
 * {
 *   "translator": {
 *     "google-translator": {
 *       "apiKey": "..."
 *     }
 *   }
 * }
 * </pre>
 */
public class TranslatorLoader {

    private static final Logger LOG = LoggerFactory.getLogger(TranslatorLoader.class);

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;

    public TranslatorLoader(ClassLoader classLoader, ObjectMapper objectMapper) {
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads a translator from JSON config.
     * <p>
     * If "translator" section exists in config, uses that translator.
     * If section missing, uses DefaultTranslator to discover translator via SPI.
     *
     * @param config the Tika JSON configuration
     * @return the translator
     * @throws TikaConfigException if loading fails
     */
    public Translator load(TikaJsonConfig config) throws TikaConfigException {
        // Check if translator section exists in config
        if (config.hasComponentSection("translator")) {
            JsonNode translatorNode = config.getRootNode().get("translator");
            return loadConfiguredTranslator(translatorNode);
        } else {
            // No configured translator - use DefaultTranslator to load from SPI
            return createDefaultTranslator();
        }
    }

    private Translator loadConfiguredTranslator(JsonNode translatorNode)
            throws TikaConfigException {
        if (!translatorNode.isObject() || translatorNode.isEmpty()) {
            throw new TikaConfigException(
                    "Translator configuration must be an object with translator type as key");
        }

        // Get the single field name (translator type) and its config
        Iterator<Map.Entry<String, JsonNode>> properties = translatorNode.properties().iterator();
        Map.Entry<String, JsonNode> entry = properties.next();

        if (properties.hasNext()) {
            throw new TikaConfigException(
                    "Translator configuration must have exactly one translator type");
        }

        String typeName = entry.getKey();
        JsonNode configNode = entry.getValue();

        return deserializeTranslator(typeName, configNode);
    }

    /**
     * Deserializes a translator, trying JsonConfig constructor first, then Jackson bean deserialization.
     */
    private Translator deserializeTranslator(String name, JsonNode configNode)
            throws TikaConfigException {
        return ComponentInstantiator.instantiate(name, configNode, objectMapper, classLoader);
    }

    /**
     * Creates a DefaultTranslator that loads a translator from SPI.
     *
     * @return the DefaultTranslator with SPI-loaded translator
     */
    private DefaultTranslator createDefaultTranslator() {
        return new DefaultTranslator(new org.apache.tika.config.ServiceLoader(classLoader));
    }
}
