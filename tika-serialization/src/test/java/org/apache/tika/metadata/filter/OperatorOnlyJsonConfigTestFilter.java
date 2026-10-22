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

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.OperatorOnly;
import org.apache.tika.metadata.Metadata;

/** As {@link OperatorOnlySetterTestFilter}, but configured through a JsonConfig constructor. */
@TikaComponent(name = "operator-only-json-config-test-filter", spi = false)
public class OperatorOnlyJsonConfigTestFilter extends MetadataFilterBase {

    public static class Config {
        private String endpoint = "";
        private MetadataFilter delegate;

        public String getEndpoint() {
            return endpoint;
        }

        @OperatorOnly
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public MetadataFilter getDelegate() {
            return delegate;
        }

        public void setDelegate(MetadataFilter delegate) {
            this.delegate = delegate;
        }
    }

    private final Config config;

    public OperatorOnlyJsonConfigTestFilter(JsonConfig jsonConfig) {
        this.config = ConfigDeserializer.buildConfig(jsonConfig, Config.class);
    }

    public String getEndpoint() {
        return config.getEndpoint();
    }

    @Override
    protected void filter(Metadata metadata) {
    }
}
