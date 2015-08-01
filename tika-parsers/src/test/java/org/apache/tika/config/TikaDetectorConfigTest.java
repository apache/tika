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
package org.apache.tika.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EmptyDetector;
import org.apache.tika.parser.microsoft.POIFSContainerDetector;
import org.apache.tika.parser.pkg.ZipContainerDetector;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Junit test class for {@link TikaConfig}, which cover things
 *  that {@link AbstractTikaConfigTest} can't do due to a need for the
 *  full set of detectors
 */
public class TikaDetectorConfigTest extends AbstractTikaConfigTest {
    @Test
    @Ignore // TODO Finish support
    public void testDetectorExcludeFromDefault() throws Exception {
        TikaConfig config = getConfig("TIKA-1702-detector-blacklist.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        CompositeDetector detector = (CompositeDetector)config.getDetector();
        
        // Should be wrapping two detectors
        assertEquals(2, detector.getDetectors().size());

        
        // First should be DefaultDetector, second Empty, that order
        assertEquals(DefaultDetector.class, detector.getDetectors().get(0).getClass());
        assertEquals(EmptyDetector.class,   detector.getDetectors().get(1).getClass());
        
        
        // Get the DefaultDetector from the config
        DefaultDetector confDetecotor = (DefaultDetector)detector.getDetectors().get(0);
        
        // Get a fresh "default" DefaultParser
        DefaultDetector normDetector = new DefaultDetector(config.getMimeRepository());
        
        
        // The default one will offer the Zip and POIFS detectors
        boolean hasZip = false;
        boolean hasPOIFS = false;
        for (Detector d : normDetector.getDetectors()) {
            if (d instanceof ZipContainerDetector) {
                hasZip = true;
            }
            if (d instanceof POIFSContainerDetector) {
                hasPOIFS = true;
            }
        }
        assertTrue(hasZip);
        assertTrue(hasPOIFS);
        
        
        // The one from the config won't, as we excluded those
        for (Detector d : confDetecotor.getDetectors()) {
            if (d instanceof ZipContainerDetector)
                fail("Shouldn't have the ZipContainerDetector from config");
            if (d instanceof POIFSContainerDetector)
                fail("Shouldn't have the POIFSContainerDetector from config");
        }
    }
}
