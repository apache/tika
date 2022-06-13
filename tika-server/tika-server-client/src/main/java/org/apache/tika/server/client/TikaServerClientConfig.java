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
package org.apache.tika.server.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.ConfigBase;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;

public class TikaServerClientConfig extends ConfigBase implements Initializable {


    public static TikaServerClientConfig build(Path configFile)
            throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(configFile)) {
            return buildSingle("serverClientConfig", TikaServerClientConfig.class, is);
        }
    }

    enum MODE {
        PIPES, ASYNC
    }

    private HttpClientFactory httpClientFactory;
    private int numThreads = 1;
    private MODE mode = MODE.PIPES;
    private List<String> tikaEndpoints = new ArrayList<>();

    private long maxWaitMillis = 60000;

    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    /**
     * maximum time in milliseconds to wait for a new fetchemittuple to be
     * available from the queue.  The client will end if no tuple is available
     * within this amount of time.
     *
     * @param maxWaitMs
     */
    public void setMaxWaitMillis(long maxWaitMs) {
        this.maxWaitMillis = maxWaitMs;
    }

    public void setMode(String mode) {
        if ("pipes".equals(mode)) {
            this.mode = MODE.PIPES;
            return;
        }
        throw new IllegalArgumentException("I regret that we have not yet implemented: '" + mode + "'");
    }

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public MODE getMode() {
        return mode;
    }

    public List<String> getTikaEndpoints() {
        return tikaEndpoints;
    }

    public void setTikaEndpoints(List<String> tikaEndpoints) {
        this.tikaEndpoints = tikaEndpoints;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (tikaEndpoints.size() == 0) {
            throw new TikaConfigException("tikaEndpoints must not be empty");
        }
    }
}
