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
package org.apache.tika.pipes.core.fetcher.config;

import org.apache.tika.pipes.api.fetcher.FetcherConfig;

public class DefaultFetcherConfig implements FetcherConfig {

    private String plugId;
    private String configJson;

    public DefaultFetcherConfig(String plugId, String configJson) {
        this.plugId = plugId;
        this.configJson = configJson;
    }
    @Override
    public String getPluginId() {
        return plugId;
    }

    @Override
    public FetcherConfig setPluginId(String pluginId) {
        this.plugId = pluginId;
        return this;
    }

    @Override
    public String getConfigJson() {
        return configJson;
    }

    @Override
    public FetcherConfig setConfigJson(String configJson) {
        this.configJson = configJson;
        return this;
    }
}
