/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.utils.StringUtils;

/**
 * Content type detector that combines multiple different detection mechanisms.
 */
public class CompositeDetector implements Detector {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 5980683158436430252L;

    protected final MediaTypeRegistry registry;
    protected final List<Detector> detectors;

    public CompositeDetector(MediaTypeRegistry registry, List<Detector> detectors,
                             Collection<Class<? extends Detector>> excludeDetectors) {
        if (excludeDetectors == null || excludeDetectors.isEmpty()) {
            this.detectors = detectors;
        } else {
            this.detectors = new ArrayList<>();
            for (Detector d : detectors) {
                if (!isExcluded(excludeDetectors, d.getClass())) {
                    this.detectors.add(d);
                }
            }
        }
        this.registry = registry;
    }

    public CompositeDetector(MediaTypeRegistry registry, List<Detector> detectors) {
        this(registry, detectors, null);
    }

    public CompositeDetector(List<Detector> detectors) {
        this(new MediaTypeRegistry(), detectors);
    }

    public CompositeDetector(Detector... detectors) {
        this(Arrays.asList(detectors));
    }

    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        MediaType override = detectOverrides(metadata);
        if (override != null) {
            return override;
        }
        MediaType type = MediaType.OCTET_STREAM;

        for (Detector detector : getDetectors()) {
            MediaType detected = detector.detect(input, metadata);
            if (registry.isSpecializationOf(detected, type)) {
                type = detected;
            }
        }
        return type;
    }

    /**
     * @param metadata Metadata object
     * @return mediaType if a parseable mediatype was sent in via user or parser overrides
     */
    protected static MediaType detectOverrides(Metadata metadata) {
        String override = metadata.get(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE);
        if (!StringUtils.isBlank(override)) {
            MediaType mt = MediaType.parse(override);
            if (mt != null) {
                return mt;
            }
        }
        override = metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
        if (!StringUtils.isBlank(override)) {
            MediaType mt = MediaType.parse(override);
            if (mt != null) {
                return mt;
            }
        }
        return null;
    }

    /**
     * Returns the component detectors.
     */
    public List<Detector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    protected boolean isExcluded(Collection<Class<? extends Detector>> excludeDetectors,
                                 Class<? extends Detector> d) {
        return excludeDetectors.contains(d) || assignableFrom(excludeDetectors, d);
    }

    protected boolean assignableFrom(Collection<Class<? extends Detector>> excludeDetectors,
                                     Class<? extends Detector> d) {
        for (Class<? extends Detector> e : excludeDetectors) {
            if (e.isAssignableFrom(d)) {
                return true;
            }
        }
        return false;
    }
}
