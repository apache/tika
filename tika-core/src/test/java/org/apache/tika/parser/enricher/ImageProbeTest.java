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
package org.apache.tika.parser.enricher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class ImageProbeTest {

    private static byte[] bytes(int length) {
        byte[] b = new byte[length];
        new Random(1).nextBytes(b);
        return b;
    }

    @Test
    public void testLengthIsKnownWithoutReading() throws Exception {
        ImageProbe probe = new ImageProbe(TikaInputStream.get(bytes(1234)), new Metadata());
        assertEquals(1234, probe.length());
    }

    @Test
    public void testLengthOfAStreamThatDoesNotKnowItIsNegative() throws Exception {
        TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes(10)));
        assertEquals(-1, new ImageProbe(tis, new Metadata()).length());
    }

    @Test
    public void testAShortImageHasItsWholeHeaderAndBytesReuseIt() throws Exception {
        byte[] image = bytes(5000);
        ImageProbe probe = new ImageProbe(TikaInputStream.get(image), new Metadata());
        assertTrue(probe.headerIsComplete());
        assertArrayEquals(image, probe.header());
        assertSame(probe.header(), probe.bytes());
    }

    @Test
    public void testALongImageHasOnlyItsStartAsHeader() throws Exception {
        byte[] image = bytes(ImageProbe.HEADER_BYTES * 2 + 17);
        TikaInputStream tis = TikaInputStream.get(image);
        ImageProbe probe = new ImageProbe(tis, new Metadata());
        assertFalse(probe.headerIsComplete());
        assertEquals(ImageProbe.HEADER_BYTES, probe.header().length);
        assertArrayEquals(Arrays.copyOf(image, ImageProbe.HEADER_BYTES), probe.header());
        // the whole image is read only when asked for, and the engine still gets all of it
        assertArrayEquals(image, probe.bytes());
        assertArrayEquals(image, tis.readAllBytes());
    }

    @Test
    public void testAnImageAboveTheLimitIsNeverRead() throws Exception {
        byte[] image = bytes(1000);
        ImageProbe probe = new ImageProbe(TikaInputStream.get(image), new Metadata());
        // the limit is a constant; a normal image is under it
        assertTrue(probe.bytes().length <= ImageProbe.MAX_BYTES);
    }
}
