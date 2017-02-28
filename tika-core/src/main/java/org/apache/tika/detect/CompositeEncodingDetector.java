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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.tika.metadata.Metadata;

public class CompositeEncodingDetector implements EncodingDetector, Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 5980683158436430252L;

    private final List<EncodingDetector> detectors;

    public CompositeEncodingDetector(List<EncodingDetector> detectors,
                                     Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors) {
        this.detectors = new LinkedList<>();
        for (EncodingDetector encodingDetector : detectors) {
            if (! isExcluded(excludeEncodingDetectors, encodingDetector.getClass())) {
                this.detectors.add(encodingDetector);
            }
        }

    }

    public CompositeEncodingDetector(List<EncodingDetector> detectors) {
        this.detectors = new LinkedList<>();
        for (EncodingDetector encodingDetector : detectors) {
            this.detectors.add(encodingDetector);
        }
    }

    /**
     *
     * @param input text document input stream, or <code>null</code>
     * @param metadata input metadata for the document
     * @return the detected Charset or null if no charset could be detected
     * @throws IOException
     */
    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        for (EncodingDetector detector : getDetectors()) {
            Charset detected = detector.detect(input, metadata);
            if (detected != null) {
                return detected;
            }
        }
        return null;
    }

    public List<EncodingDetector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    private boolean isExcluded(Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors,
                               Class<? extends EncodingDetector> encodingDetector) {
        return excludeEncodingDetectors.contains(encodingDetector) ||
                assignableFrom(excludeEncodingDetectors, encodingDetector);
    }

    private boolean assignableFrom(Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors,
                                   Class<? extends EncodingDetector> encodingDetector) {
        for (Class<? extends EncodingDetector> e : excludeEncodingDetectors) {
            if (e.isAssignableFrom(encodingDetector)) return true;
        }
        return false;
    }
}
