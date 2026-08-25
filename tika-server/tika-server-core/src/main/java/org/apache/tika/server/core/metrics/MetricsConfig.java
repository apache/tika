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
package org.apache.tika.server.core.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code metrics} block of the tika-server config. The port is the only switch:
 * unset means no registry, no meters, no scrape listener.
 */
public class MetricsConfig {

    private Integer port;
    private String host;
    private Map<String, String> commonTags = new LinkedHashMap<>();

    public boolean isEnabled() {
        return port != null;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    /** Bind address for the scrape listener; null follows the server's own host. */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Map<String, String> getCommonTags() {
        return commonTags;
    }

    public void setCommonTags(Map<String, String> commonTags) {
        this.commonTags = commonTags == null ? new LinkedHashMap<>() : commonTags;
    }
}
