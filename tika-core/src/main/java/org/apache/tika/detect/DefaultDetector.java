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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MimeTypes;

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
        List<Detector> detectors =
                loader.loadStaticServiceProviders(Detector.class);
        Collections.sort(detectors, new Comparator<Detector>() {
            public int compare(Detector d1, Detector d2) {
                String n1 = d1.getClass().getName();
                String n2 = d2.getClass().getName();
                boolean t1 = n1.startsWith("org.apache.tika.");
                boolean t2 = n2.startsWith("org.apache.tika.");
                if (t1 == t2) {
                    return n1.compareTo(n2);
                } else if (t1) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        // Finally the Tika MimeTypes as a fallback
        detectors.add(types);
        return detectors;
    }

    private transient final ServiceLoader loader;

    public DefaultDetector(MimeTypes types, ServiceLoader loader) {
        super(types.getMediaTypeRegistry(), getDefaultDetectors(types, loader));
        this.loader = loader;
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
