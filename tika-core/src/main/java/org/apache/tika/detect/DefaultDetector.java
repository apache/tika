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
package org.apache.tika.detect;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MimeTypes;

/**
 * A composite detector based on all the {@link Detector} implementations
 * available through the {@link ServiceRegistry service provider mechanism}.
 *
 * @since Apache Tika 0.9
 */
public class DefaultDetector extends CompositeDetector {

    /** Serial version UID */
    private static final long serialVersionUID = -8170114575326908027L;

    private static List<Detector> getDefaultDetectors(
            MimeTypes types, ServiceLoader loader) {
        List<Detector> detectors = new ArrayList<Detector>();
        detectors.add(types);
        detectors.addAll(loader.loadServiceProviders(Detector.class));
        return detectors;
    }

    private DefaultDetector(MimeTypes types, ServiceLoader loader) {
        super(types.getMediaTypeRegistry(), getDefaultDetectors(types, loader));
    }

    public DefaultDetector(MimeTypes types, ClassLoader loader) {
        this(types, new ServiceLoader(loader));
    }

    public DefaultDetector(ClassLoader loader) {
        this(MimeTypes.getDefaultMimeTypes(), loader);
    }

    public DefaultDetector(MimeTypes types) {
        this(types, new ServiceLoader());
    }

    public DefaultDetector() {
        this(MimeTypes.getDefaultMimeTypes());
    }

}
