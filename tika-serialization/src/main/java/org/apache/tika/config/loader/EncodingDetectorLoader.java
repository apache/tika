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

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;

/**
 * Loader for encoding detectors with support for SPI fallback via
 * "default-encoding-detector" marker.
 */
public class EncodingDetectorLoader extends AbstractSpiComponentLoader<EncodingDetector> {

    public EncodingDetectorLoader() {
        super("encoding-detectors", "default-encoding-detector", EncodingDetector.class);
    }

    @Override
    protected EncodingDetector loadComponent(String name, JsonNode configNode,
                                              LoaderContext context) throws TikaConfigException {
        return context.instantiate(name, configNode);
    }

    @Override
    protected EncodingDetector createDefaultComposite(
            Set<Class<? extends EncodingDetector>> exclusions, LoaderContext context) {
        return new DefaultEncodingDetector(
                new ServiceLoader(context.getClassLoader()),
                exclusions);
    }

    @Override
    protected EncodingDetector wrapInComposite(List<EncodingDetector> detectors,
                                                LoaderContext context) {
        // Always wrap in CompositeEncodingDetector for consistency
        // (parsers expect CompositeEncodingDetector)
        return new CompositeEncodingDetector(detectors);
    }
}
