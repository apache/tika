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
package org.apache.tika.pipes.fetcher.config;

import java.io.IOException;
import java.util.Properties;

public abstract class FetcherConfig {
    private String fetcherId;

    abstract public String getPluginId();

    public String getFetcherId() {
        return fetcherId;
    }

    public FetcherConfig setFetcherId(String fetcherId) {
        this.fetcherId = fetcherId;
        return this;
    }

    public static String getPluginIdForFetcherConfig(Class<?> clazz) {
        Properties properties = new Properties();
        try {
            properties.load(clazz.getResourceAsStream("/plugin.properties"));
            return properties.getProperty("plugin.id");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find plugin.properties for plugin", e);
        }
    }
}
