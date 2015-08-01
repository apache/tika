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

import java.util.Collection;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * A composite detector based on all the {@link Detector} implementations
 * available through the {@link ServiceRegistry service provider mechanism}.
 * 
 * Detectors are loaded and returned in a specified order, of user supplied
 *  followed by non-MimeType Tika, followed by the Tika MimeType class.
 * If you need to control the order of the Detectors, you should instead
 *  construct your own {@link CompositeDetector} and pass in the list
 *  of Detectors in the required order.
 *
 * @since Apache Tika 0.9
 */
public class DefaultDetector extends CompositeDetector {

    /** Serial version UID */
    private static final long serialVersionUID = -8170114575326908027L;

    /**
     * Finds all statically loadable detectors and sort the list by name,
     * rather than discovery order. Detectors are used in the given order,
     * so put the Tika parsers last so that non-Tika (user supplied)
     * parsers can take precedence.
     *
     * @param loader service loader
     * @return ordered list of statically loadable detectors
     */
    private static List<Detector> getDefaultDetectors(
            MimeTypes types, ServiceLoader loader) {
        List<Detector> detectors = loader.loadStaticServiceProviders(Detector.class);
        ServiceLoaderUtils.sortLoadedClasses(detectors);
        
        // Finally the Tika MimeTypes as a fallback
        detectors.add(types);
        return detectors;
    }

    private transient final ServiceLoader loader;

    public DefaultDetector(MimeTypes types, ServiceLoader loader,
                           Collection<Class<? extends Detector>> excludeDetectors) {
        super(types.getMediaTypeRegistry(), getDefaultDetectors(types, loader), excludeDetectors);
        this.loader = loader;
    }
    
    public DefaultDetector(MimeTypes types, ServiceLoader loader) {
        this(types, loader, null);
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

    @Override
    public List<Detector> getDetectors() {
        if (loader != null) {
            List<Detector> detectors =
                    loader.loadDynamicServiceProviders(Detector.class);
            detectors.addAll(super.getDetectors());
            return detectors;
        } else {
            return super.getDetectors();
        }
    }

}
