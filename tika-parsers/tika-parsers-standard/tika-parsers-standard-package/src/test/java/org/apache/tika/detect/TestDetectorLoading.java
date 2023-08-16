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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfig;

public class TestDetectorLoading {


    @Test
    public void testBasic() throws Exception {
        //integration test
        Detector detector = TikaConfig.getDefaultConfig().getDetector();
        List<Detector> detectors = ((CompositeDetector) detector).getDetectors();
        assertEquals(7, detectors.size());
        assertEquals("org.gagravarr.tika.OggDetector", detectors.get(0).getClass().getName());
        assertEquals("org.apache.tika.detect.gzip.GZipSpecializationDetector",
                detectors.get(2).getClass().getName());

        assertEquals("org.apache.tika.detect.microsoft.POIFSContainerDetector",
                detectors.get(3).getClass().getName());
        assertEquals("org.apache.tika.mime.MimeTypes", detectors.get(6).getClass().getName());
    }
}
