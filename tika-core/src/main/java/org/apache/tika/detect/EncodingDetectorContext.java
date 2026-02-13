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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Context object used by {@link TextQualityEncodingDetector} to collect
 * results from all child detectors before arbitrating. Stored temporarily
 * in {@link org.apache.tika.parser.ParseContext} during detection and
 * removed afterward to prevent contamination during recursive parsing.
 *
 * @since Apache Tika 3.2
 */
public class EncodingDetectorContext {

    private final List<Result> results = new ArrayList<>();

    /**
     * Record a detection result from a child detector.
     *
     * @param charset      the detected charset (must not be null)
     * @param detectorName the simple class name of the detector
     */
    public void addResult(Charset charset, String detectorName) {
        results.add(new Result(charset, detectorName));
    }

    /**
     * @return unmodifiable list of all results in detection order
     */
    public List<Result> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * @return unique charsets in detection order
     */
    public Set<Charset> getUniqueCharsets() {
        Set<Charset> charsets = new LinkedHashSet<>();
        for (Result r : results) {
            charsets.add(r.getCharset());
        }
        return charsets;
    }

    /**
     * A single detection result pairing a charset with the detector that found it.
     */
    public static class Result {
        private final Charset charset;
        private final String detectorName;

        public Result(Charset charset, String detectorName) {
            this.charset = charset;
            this.detectorName = detectorName;
        }

        public Charset getCharset() {
            return charset;
        }

        public String getDetectorName() {
            return detectorName;
        }

        @Override
        public String toString() {
            return detectorName + "=" + charset.name();
        }
    }
}
