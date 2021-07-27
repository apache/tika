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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EmptyDetector;
import org.apache.tika.detect.microsoft.POIFSContainerDetector;
import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.microsoft.pst.OutlookPSTParser;

/**
 * Junit test class for {@link TikaConfig}, which cover things
 * that {@link TikaConfigTest} can't do due to a need for the
 * full set of detectors
 */
public class TikaDetectorConfigTest extends AbstractTikaConfigTest {

    @Test
    public void testDetectorExcludeFromDefault() throws Exception {
        TikaConfig config = getConfig("TIKA-1702-detector-exclude.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        CompositeDetector detector = (CompositeDetector) config.getDetector();

        // Should be wrapping two detectors
        assertEquals(2, detector.getDetectors().size());


        // First should be DefaultDetector, second Empty, that order
        assertEquals(DefaultDetector.class, detector.getDetectors().get(0).getClass());
        assertEquals(EmptyDetector.class, detector.getDetectors().get(1).getClass());


        // Get the DefaultDetector from the config
        DefaultDetector confDetector = (DefaultDetector) detector.getDetectors().get(0);

        // Get a fresh "default" DefaultParser
        DefaultDetector normDetector = new DefaultDetector(config.getMimeRepository());


        // The default one will offer the Zip and POIFS detectors
        assertDetectors(normDetector, true, true);


        // The one from the config won't, as we excluded those
        assertDetectors(confDetector, false, false);
    }

    /**
     * TIKA-1708 - If the Zip detector is disabled, either explicitly,
     * or via giving a list of detectors that it isn't part of, ensure
     * that detection of PST files still works
     */
    @Test
    public void testPSTDetectionWithoutZipDetector() throws Exception {
        // Check the one with an exclude
        TikaConfig configWX = getConfig("TIKA-1708-detector-default.xml");
        assertNotNull(configWX.getParser());
        assertNotNull(configWX.getDetector());
        CompositeDetector detectorWX = (CompositeDetector) configWX.getDetector();

        // Check it has the POIFS one, but not the zip one
        assertDetectors(detectorWX, true, false);


        // Check the one with an explicit list
        TikaConfig configCL = getConfig("TIKA-1708-detector-composite.xml");
        assertNotNull(configCL.getParser());
        assertNotNull(configCL.getDetector());
        CompositeDetector detectorCL = (CompositeDetector) configCL.getDetector();
        assertEquals(2, detectorCL.getDetectors().size());

        // Check it also has the POIFS one, but not the zip one
        assertDetectors(detectorCL, true, false);


        // Check that both detectors have a mimetypes with entries
        assertTrue(configWX.getMediaTypeRegistry().getTypes().size() > 100,
                "Not enough mime types: " + configWX.getMediaTypeRegistry().getTypes().size());
        assertTrue(configCL.getMediaTypeRegistry().getTypes().size() > 100,
                "Not enough mime types: " + configCL.getMediaTypeRegistry().getTypes().size());


        // Now check they detect PST files correctly
        try (TikaInputStream outer = TikaInputStream
                .get(getResourceAsStream("/test-documents/testPST.pst"))) {
            try (TikaInputStream stream = TikaInputStream.get(outer.getFile())) {

                assertEquals(OutlookPSTParser.MS_OUTLOOK_PST_MIMETYPE,
                        detectorWX.detect(stream, new Metadata()));
                assertEquals(OutlookPSTParser.MS_OUTLOOK_PST_MIMETYPE,
                        detectorCL.detect(stream, new Metadata()));
            }
        }
    }

    private void assertDetectors(CompositeDetector detector, boolean shouldHavePOIFS,
                                 boolean shouldHaveZip) {
        boolean hasZip = false;
        boolean hasPOIFS = false;
        for (Detector d : detector.getDetectors()) {
            if (d instanceof DefaultZipContainerDetector) {
                if (shouldHaveZip) {
                    hasZip = true;
                } else {
                    fail("Shouldn't have the ZipContainerDetector from config");
                }
            }
            if (d instanceof POIFSContainerDetector) {
                if (shouldHavePOIFS) {
                    hasPOIFS = true;
                } else {
                    fail("Shouldn't have the POIFSContainerDetector from config");
                }
            }
        }
        if (shouldHavePOIFS) {
            assertTrue(hasPOIFS, "Should have the POIFSContainerDetector");
        }
        if (shouldHaveZip) {
            assertTrue(hasZip, "Should have the ZipContainerDetector");
        }
    }
}
