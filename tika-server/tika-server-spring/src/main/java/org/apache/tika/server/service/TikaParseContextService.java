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
package org.apache.tika.server.service;

import java.util.List;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.config.ParseContextConfig;

/**
 * Service for configuring ParseContext from HTTP headers.
 * Replaces the static fillParseContext method from TikaResource.
 */
@Service
public class TikaParseContextService {
    private final List<ParseContextConfig> parseContextConfigs;
    
    public TikaParseContextService() {
        // Load all available ParseContextConfig implementations using Tika's ServiceLoader
        this.parseContextConfigs = new ServiceLoader(getClass().getClassLoader())
                .loadServiceProviders(ParseContextConfig.class);
    }
    
    /**
     * Fills the ParseContext based on HTTP headers and metadata.
     * This is equivalent to the static fillParseContext method from TikaResource.
     * 
     * @param httpHeaders the HTTP headers from the request
     * @param metadata the metadata object
     * @param parseContext the parse context to configure
     */
    public void fillParseContext(MultiValueMap<String, String> httpHeaders, Metadata metadata, ParseContext parseContext) {
        jakarta.ws.rs.core.MultivaluedMap<String, String> jakartaHeaders = new MultivaluedHashMap<>();
        for (String key : httpHeaders.keySet()) {
            List<String> values = httpHeaders.get(key);
            if (values != null) {
                for (String value : values) {
                    jakartaHeaders.add(key, value);
                }
            }
        }
        for (ParseContextConfig config : parseContextConfigs) {
            config.configure(jakartaHeaders, metadata, parseContext);
        }
    }
}
