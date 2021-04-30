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
package org.apache.tika.pipes.fetchiterator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;

/**
 * This class currently only supports one fetch iterator at a time.
 */
public class FetchIteratorManager extends ConfigBase implements Initializable {

    public static FetchIteratorManager build(Path tikaConfigFile) throws IOException,
            TikaConfigException {
        try (InputStream is = Files.newInputStream(tikaConfigFile)) {
            return buildComposite("fetchIterators", FetchIteratorManager.class, "fetchIterator",
                    FetchIterator.class, is);
        }
    }

    private List<FetchIterator> fetchIterators = new ArrayList<>();
    public FetchIteratorManager(List<FetchIterator> fetchIterators) {
        this.fetchIterators = fetchIterators;
    }

    public FetchIterator getFetchIterator() {
        return fetchIterators.get(0);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        if (fetchIterators.size() == 0) {
            throw new TikaConfigException("must be at least one fetch iterator");
        }
        if (fetchIterators.size() > 1) {
            throw new TikaConfigException("Sorry, we currently only support a single fetch iterator");
        }
    }
}
