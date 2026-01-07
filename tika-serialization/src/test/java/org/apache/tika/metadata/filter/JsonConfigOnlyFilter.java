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

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;

/**
 * Test filter that ONLY has a JsonConfig constructor (no no-arg constructor).
 * Used to verify that TikaModule correctly handles components without no-arg constructors.
 */
@TikaComponent
public class JsonConfigOnlyFilter extends MetadataFilterBase {

    public static class Config {
        public String prefix = "";
    }

    private final String prefix;

    /**
     * Constructor that requires JsonConfig - no no-arg constructor available.
     */
    public JsonConfigOnlyFilter(JsonConfig jsonConfig) {
        Config config = ConfigDeserializer.buildConfig(jsonConfig, Config.class);
        this.prefix = config.prefix;
    }

    /**
     * Constructor with explicit Config object for programmatic use.
     */
    public JsonConfigOnlyFilter(Config config) {
        this.prefix = config.prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    protected void filter(Metadata metadata) {
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            metadata.remove(name);
            for (String value : values) {
                metadata.add(name, prefix + value);
            }
        }
    }
}
