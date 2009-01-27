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
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Content type detector that combines multiple different detection mechanisms.
 */
public class CompositeDetector implements Detector {

    private final List<Detector> detectors;

    public CompositeDetector(List<Detector> detectors) {
        this.detectors = detectors;
    }

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException { 
        MediaType type = MediaType.OCTET_STREAM;
        for (Detector detector : detectors) {
            MediaType detected = detector.detect(input, metadata);
            if (detected.isSpecializationOf(type)) {
                type = detected;
            }
        }
        return type;
    }

}
