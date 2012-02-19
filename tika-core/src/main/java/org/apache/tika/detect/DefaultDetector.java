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

    private static List<Detector> getDefaultDetectors(
            MimeTypes types, ServiceLoader loader) {
        // Find all the detectors available as services
        List<Detector> svcDetectors = loader.loadServiceProviders(Detector.class);
        List<Detector> detectors = new ArrayList<Detector>(svcDetectors.size()+1);
        
        // Sort the list by classname, rather than discovery order 
        Collections.sort(svcDetectors, new Comparator<Detector>() {
            public int compare(Detector d1, Detector d2) {
               return d1.getClass().getName().compareTo(
                     d2.getClass().getName());
            }
        });
        
        // Add the non-Tika (user supplied) detectors First
        for (Detector d : svcDetectors) {
           if (! d.getClass().getName().startsWith("org.apache.tika")) {
              detectors.add(d);
           }
        }
        
        // Add the Tika detectors next
        for (Detector d : svcDetectors) {
           if (d.getClass().getName().startsWith("org.apache.tika")) {
              detectors.add(d);
           }
        }
        
        // Finally add the Tika MimeTypes as a fallback
        detectors.add(types);
        
        return detectors;
    }

    public DefaultDetector(MimeTypes types, ServiceLoader loader) {
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
