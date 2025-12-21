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
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;

/**
 * Loader for detectors with support for SPI fallback via "default-detector" marker.
 */
public class DetectorLoader extends AbstractSpiComponentLoader<Detector> {

    public DetectorLoader() {
        super("detectors", "default-detector", Detector.class);
    }

    @Override
    protected Detector loadComponent(String name, JsonNode configNode,
                                       LoaderContext context) throws TikaConfigException {
        return context.instantiate(name, configNode);
    }

    @Override
    protected Detector createDefaultComposite(Set<Class<? extends Detector>> exclusions,
                                               LoaderContext context) {
        return new DefaultDetector(
                TikaLoader.getMimeTypes(),
                new ServiceLoader(context.getClassLoader()),
                exclusions);
    }

    @Override
    protected Detector wrapInComposite(List<Detector> detectors, LoaderContext context) {
        return new CompositeDetector(TikaLoader.getMediaTypeRegistry(), detectors);
    }

    @Override
    protected Detector handleSpecialName(String name, JsonNode configNode,
                                          LoaderContext context) throws TikaConfigException {
        // "mime-types" is a special detector that uses the initialized MimeTypes registry
        if ("mime-types".equals(name)) {
            return TikaLoader.getMimeTypes();
        }
        return null;
    }
}
