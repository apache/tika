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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZeroSizeFileDetectorTest {

    private Detector detector;

    @Before
    public void setUp() {
        detector = new ZeroSizeFileDetector();
    }

    @Test
    public void testDetectZeroValue() {
        byte[] data = "".getBytes(UTF_8);
        detect(data, MediaType.EMPTY);
        System.out.println();
    }

    @Test
    public void testDetectNonZeroValue() {
        byte[] data = "Testing 1...2...3".getBytes(UTF_8);
        detect(data, MediaType.OCTET_STREAM);
        System.out.println();
    }

    private void detect(byte[] data, MediaType type) {
        try {
            InputStream stream = new ByteArrayInputStream(data);
            assertEquals(type, detector.detect(stream, new Metadata()));
        } catch (IOException e) {
            fail("Unexpected exception from ZeroSizeFileDetector");
        }
    }

}
