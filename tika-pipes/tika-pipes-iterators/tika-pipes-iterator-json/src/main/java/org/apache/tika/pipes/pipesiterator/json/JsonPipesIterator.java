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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Initializable;
import org.apache.tika.serialization.pipes.JsonFetchEmitTuple;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

/**
 * Iterates through a UTF-8 text file with one FetchEmitTuple
 * json object per line.
 */
public class JsonPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonPipesIterator.class);

    private Path jsonPath;

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        try (BufferedReader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
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

    public void setJsonPath(String jsonPath) {
        this.jsonPath = Paths.get(jsonPath);
    }
}
