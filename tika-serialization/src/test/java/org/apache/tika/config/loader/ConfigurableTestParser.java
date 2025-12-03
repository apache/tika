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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Test parser with configurable properties for testing JSON configuration loading.
 */
@TikaComponent(name = "configurable-test-parser")
public class ConfigurableTestParser implements Parser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long serialVersionUID = 1L;

    private final TestParserConfig config;

    /**
     * Constructor for JSON-based configuration.
     */
    public ConfigurableTestParser(JsonConfig jsonConfig) throws TikaConfigException {
        try {
            this.config = OBJECT_MAPPER.readValue(jsonConfig.json(), TestParserConfig.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse JSON config", e);
        }
    }

    /**
     * Zero-arg constructor for SPI fallback.
     */
    public ConfigurableTestParser() {
        this.config = new TestParserConfig();
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(MediaType.parse("application/test+configurable"));
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Simple implementation that writes config to metadata
        metadata.set("parser-name", config.getName());
        metadata.set("buffer-size", String.valueOf(config.getBufferSize()));
        metadata.set("enabled", String.valueOf(config.isEnabled()));
        metadata.set("mode", config.getMode());
    }

    public TestParserConfig getConfig() {
        return config;
    }

    /**
     * Configuration POJO for ConfigurableTestParser.
     */
    public static class TestParserConfig {
        private String name = "default";
        private int bufferSize = 1024;
        private boolean enabled = true;
        private String mode = "normal";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
