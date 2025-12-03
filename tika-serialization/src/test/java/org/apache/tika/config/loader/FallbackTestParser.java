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
 * Simple test parser for fallback chain testing.
 */
@TikaComponent(name = "fallback-test-parser")
public class FallbackTestParser implements Parser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long serialVersionUID = 1L;

    private final FallbackConfig config;

    /**
     * Constructor for JSON-based configuration.
     */
    public FallbackTestParser(JsonConfig jsonConfig) throws TikaConfigException {
        try {
            this.config = OBJECT_MAPPER.readValue(jsonConfig.json(), FallbackConfig.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse JSON config", e);
        }
    }

    /**
     * Zero-arg constructor for SPI fallback.
     */
    public FallbackTestParser() {
        this.config = new FallbackConfig();
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(MediaType.parse("application/test+fallback"));
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        if (config.isFailOnPurpose()) {
            throw new TikaException("Intentional failure for testing fallback: " + config.getMessage());
        }
        // Success case
        metadata.set("fallback-parser", "success");
        metadata.set("message", config.getMessage());
    }

    public FallbackConfig getConfig() {
        return config;
    }

    /**
     * Configuration POJO for FallbackTestParser.
     */
    public static class FallbackConfig {
        private String message = "default message";
        private boolean failOnPurpose = false;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isFailOnPurpose() {
            return failOnPurpose;
        }

        public void setFailOnPurpose(boolean failOnPurpose) {
            this.failOnPurpose = failOnPurpose;
        }
    }
}
