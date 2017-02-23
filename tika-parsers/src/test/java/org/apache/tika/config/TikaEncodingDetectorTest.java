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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.NonDetectingEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;
import org.junit.Test;

public class TikaEncodingDetectorTest extends AbstractTikaConfigTest {

    @Test
    public void testDefault() {
        EncodingDetector detector = TikaConfig.getDefaultConfig().getEncodingDetector();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector)detector).getDetectors();
        assertEquals(3, detectors.size());
        assertTrue(detectors.get(0) instanceof HtmlEncodingDetector);
        assertTrue(detectors.get(1) instanceof UniversalEncodingDetector);
        assertTrue(detectors.get(2) instanceof Icu4jEncodingDetector);
    }

    @Test
    public void testBlackList() throws Exception {
        TikaConfig config = getConfig("TIKA-2273-blacklist-encoding-detector-default.xml");
        EncodingDetector detector = config.getEncodingDetector();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector)detector).getDetectors();
        assertEquals(2, detectors.size());

        EncodingDetector detector1 = detectors.get(0);
        assertTrue(detector1 instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors1Children = ((CompositeEncodingDetector)detector1).getDetectors();
        assertEquals(2, detectors1Children.size());
        assertTrue(detectors1Children.get(0) instanceof UniversalEncodingDetector);
        assertTrue(detectors1Children.get(1) instanceof Icu4jEncodingDetector);

        assertTrue(detectors.get(1) instanceof NonDetectingEncodingDetector);

    }

    @Test
    public void testParameterization() throws Exception {
        TikaConfig config = getConfig("TIKA-2273-parameterize-encoding-detector.xml");
        EncodingDetector detector = config.getEncodingDetector();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector)detector).getDetectors();
        assertEquals(2, detectors.size());
        assertTrue(((Icu4jEncodingDetector)detectors.get(0)).getStripMarkup());
        assertTrue(detectors.get(1) instanceof NonDetectingEncodingDetector);

    }
}
