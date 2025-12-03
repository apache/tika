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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;

/**
 * Loader for translators.
 * Only one translator is supported at a time.
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
        try {
            // The translator node should be an object with a "class" field
            if (!translatorNode.has("class")) {
                throw new TikaConfigException("Translator configuration must have a 'class' field");
            }

            String className = translatorNode.get("class").asText();
            ComponentRegistry registry = new ComponentRegistry("translators", classLoader);
            Class<?> translatorClass = registry.getComponentClass(className);

            // Remove "class" field from config before extraction
            ObjectNode configCopy = ((ObjectNode) translatorNode).deepCopy();
            configCopy.remove("class");

            // Extract framework config (e.g., _decorate if present)
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configCopy, objectMapper);

            // Instantiate translator
            return instantiateTranslator(translatorClass, frameworkConfig.getComponentConfigJson());

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load translator", e);
        }
    }

    private Translator instantiateTranslator(Class<?> translatorClass, JsonConfig jsonConfig)
            throws TikaConfigException {
        return ComponentInstantiator.instantiate(translatorClass, jsonConfig, classLoader,
                "Translator", objectMapper);
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
