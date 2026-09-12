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
package org.apache.tika.config.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.parser.inference.InferenceDispatcher;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.TextInferenceFilter;

/**
 * Loads {@code "metadata-filters"} and appends the TEXT inference stage when an
 * {@code "inference"} binding takes TEXT, so text embedding runs after the configured filters
 * over the finished metadata list wherever they run.
 */
class MetadataFilterLoader implements ComponentLoader<MetadataFilter> {

    static final String KEY = "metadata-filters";

    @Override
    public MetadataFilter load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        List<MetadataFilter> filters = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : config.getArrayComponents(KEY)) {
            try {
                ObjectNode wrapper = context.getObjectMapper().createObjectNode();
                wrapper.set(entry.getKey(), entry.getValue());
                filters.add(context.getObjectMapper().treeToValue(wrapper, MetadataFilter.class));
            } catch (Exception e) {
                throw new TikaConfigException("Failed to load MetadataFilter: " + entry.getKey(), e);
            }
        }
        InferenceDispatcher dispatcher = context.get(InferenceDispatcher.class);
        if (dispatcher != null && dispatcher.hasInput(InputKind.TEXT)) {
            filters.add(new TextInferenceFilter(dispatcher));
        }
        if (filters.isEmpty()) {
            return NoOpFilter.NOOP_FILTER;
        }
        return new CompositeMetadataFilter(filters);
    }
}
