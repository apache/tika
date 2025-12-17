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
package org.apache.tika.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Global Tika configuration settings that don't belong to specific components.
 * These are top-level settings in the JSON configuration file.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "xml-reader-utils": {
 *     "maxEntityExpansions": 1000,
 *     "maxNumReuses": 100,
 *     "poolSize": 10
 *   }
 * }
 * </pre>
 */
public class GlobalSettings {

    /**
     * Service loader configuration for handling initialization problems.
     */
    @JsonProperty("service-loader")
    private ServiceLoaderConfig serviceLoader;

    /**
     * XML reader utilities configuration for security limits.
     */
    @JsonProperty("xml-reader-utils")
    private XmlReaderUtilsConfig xmlReaderUtils;

    public ServiceLoaderConfig getServiceLoader() {
        return serviceLoader;
    }

    public void setServiceLoader(ServiceLoaderConfig serviceLoader) {
        this.serviceLoader = serviceLoader;
    }

    public XmlReaderUtilsConfig getXmlReaderUtils() {
        return xmlReaderUtils;
    }

    public void setXmlReaderUtils(XmlReaderUtilsConfig xmlReaderUtils) {
        this.xmlReaderUtils = xmlReaderUtils;
    }

    /**
     * Service loader configuration.
     */
    public static class ServiceLoaderConfig {
    }

    /**
     * XML reader utilities security configuration.
     */
    public static class XmlReaderUtilsConfig {
        /**
         * Maximum entity expansions allowed in XML parsing.
         */
        @JsonProperty("maxEntityExpansions")
        private Integer maxEntityExpansions;

        /**
         * Maximum number of times an XML reader can be reused from the pool.
         */
        @JsonProperty("maxNumReuses")
        private Integer maxNumReuses;

        /**
         * Size of the XML reader pool.
         */
        @JsonProperty("poolSize")
        private Integer poolSize;

        public Integer getMaxEntityExpansions() {
            return maxEntityExpansions;
        }

        public void setMaxEntityExpansions(Integer maxEntityExpansions) {
            this.maxEntityExpansions = maxEntityExpansions;
        }

        public Integer getMaxNumReuses() {
            return maxNumReuses;
        }

        public void setMaxNumReuses(Integer maxNumReuses) {
            this.maxNumReuses = maxNumReuses;
        }

        public Integer getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(Integer poolSize) {
            this.poolSize = poolSize;
        }
    }
}
