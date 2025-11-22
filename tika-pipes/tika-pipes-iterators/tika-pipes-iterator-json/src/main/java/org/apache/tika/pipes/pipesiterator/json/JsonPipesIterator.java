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
package org.apache.tika.pipes.pipesiterator.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Iterates through a UTF-8 text file with one FetchEmitTuple
 * json object per line.
 */
public class JsonPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonPipesIterator.class);

    private final JsonPipesIteratorConfig config;

    private JsonPipesIterator(JsonPipesIteratorConfig config, ExtensionConfig extensionConfig) throws TikaConfigException {
        super(extensionConfig);
        this.config = config;

        if (config.getJsonPath() == null) {
            throw new TikaConfigException("jsonPath must not be empty");
        }
    }

    public static JsonPipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        JsonPipesIteratorConfig config = JsonPipesIteratorConfig.load(extensionConfig.jsonConfig());
        return new JsonPipesIterator(config, extensionConfig);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        try (BufferedReader reader = Files.newBufferedReader(config.getJsonPath(), StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while (line != null) {
                try (Reader r = new StringReader(line)) {
                    FetchEmitTuple t = JsonFetchEmitTuple.fromJson(r);
                    LOGGER.info("from json: " + t);
                    tryToAdd(t);
                    line = reader.readLine();
                }
            }
        }
    }
}
