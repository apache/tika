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
public class ExcludeFieldMetadataFilter extends MetadataFilterBase {

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public List<String> exclude = new ArrayList<>();
    }

    private final Set<String> excludeSet;

    public ExcludeFieldMetadataFilter() {
        this(new HashSet<>());
    }

    public ExcludeFieldMetadataFilter(Set<String> exclude) {
        this.excludeSet = exclude;
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public ExcludeFieldMetadataFilter(Config config) {
        this.excludeSet = new HashSet<>(config.exclude);
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public ExcludeFieldMetadataFilter(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    @Override
    protected void filter(Metadata metadata) {
        for (String field : excludeSet) {
            metadata.remove(field);
        }
    }

    /**
     * @param exclude list of fields to exclude
     */
    public void setExclude(List<String> exclude) {
        this.excludeSet.addAll(exclude);
    }

    public List<String> getExclude() {
        return new ArrayList<>(excludeSet);
    }
}
