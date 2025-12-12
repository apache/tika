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
package org.apache.tika.metadata.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;

@TikaComponent
public class IncludeFieldMetadataFilter extends MetadataFilterBase {

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public List<String> include = new ArrayList<>();
    }

    private final Set<String> includeSet;

    public IncludeFieldMetadataFilter() {
        this(new HashSet<>());
    }

    public IncludeFieldMetadataFilter(Set<String> fields) {
        this.includeSet = fields;
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public IncludeFieldMetadataFilter(Config config) {
        this.includeSet = new HashSet<>(config.include);
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public IncludeFieldMetadataFilter(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    /**
     * @param include comma-delimited list of fields to include
     */
    public void setInclude(List<String> include) {
        includeSet.addAll(include);
    }

    public List<String> getInclude() {
        return new ArrayList<>(includeSet);
    }

    @Override
    protected void filter(Metadata metadata) {

        for (String n : metadata.names()) {
            if (!includeSet.contains(n)) {
                metadata.remove(n);
            }
        }
    }
}
