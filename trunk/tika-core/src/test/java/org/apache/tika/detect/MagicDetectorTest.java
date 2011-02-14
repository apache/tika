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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Test cases for the {@link MagicDetector} class.
 */
public class MagicDetectorTest extends TestCase {

    public void testDetectNull() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(html, "<html".getBytes("ASCII"));
        assertEquals(
                MediaType.OCTET_STREAM,
                detector.detect(null, new Metadata()));
    }

    public void testDetectSimple() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(html, "<html".getBytes("ASCII"));

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<html><head/><body/></html>");
        assertDetect(detector, MediaType.OCTET_STREAM, "<HTML");
        assertDetect(detector, MediaType.OCTET_STREAM, "<?xml?><html");
        assertDetect(detector, MediaType.OCTET_STREAM, " <html");
        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    public void testDetectOffsetRange() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(
                html, "<html".getBytes("ASCII"), null, 0, 64);

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<html><head/><body/></html>");
        assertDetect(detector, html, "<?xml?><html/>");
        assertDetect(detector, html, "\n    <html");
        assertDetect(detector, html, "\u0000<html");
        assertDetect(detector, MediaType.OCTET_STREAM, "<htm");
        assertDetect(detector, MediaType.OCTET_STREAM, " html");
        assertDetect(detector, MediaType.OCTET_STREAM, "<HTML");

        assertDetect(detector, html,
                "0........1.........2.........3.........4.........5.........6"
                + "1234<html");
        assertDetect(detector, MediaType.OCTET_STREAM,
                "0........1.........2.........3.........4.........5.........6"
                + "12345<html");

        assertDetect(detector, MediaType.OCTET_STREAM, "");
}

    public void testDetectMask() throws Exception {
        MediaType html = new MediaType("text", "html");
        byte up = (byte) 0xdf;
        Detector detector = new MagicDetector(
                html,
                new byte[] { '<',  'H',  'T',  'M',  'L' },
                new byte[] { (byte) 0xff, up, up, up, up },
                0, 64);

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<HTML><head/><body/></html>");
        assertDetect(detector, html, "<?xml?><HtMl/>");
        assertDetect(detector, html, "\n    <html");
        assertDetect(detector, html, "\u0000<HTML");
        assertDetect(detector, MediaType.OCTET_STREAM, "<htm");
        assertDetect(detector, MediaType.OCTET_STREAM, " html");

        assertDetect(detector, html,
                "0        1         2         3         4         5         6"
                + "1234<html");
        assertDetect(detector, MediaType.OCTET_STREAM,
                "0        1         2         3         4         5         6"
                + "12345<html");

        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    private void assertDetect(Detector detector, MediaType type, String data) {
        try {
            byte[] bytes = data.getBytes("ASCII");
            InputStream stream = new ByteArrayInputStream(bytes);
            assertEquals(type, detector.detect(stream, new Metadata()));

            // Test that the stream has been reset
            for (int i = 0; i < bytes.length; i++) {
                assertEquals(bytes[i], (byte) stream.read());
            }
            assertEquals(-1, stream.read());
        } catch (IOException e) {
            fail("Unexpected exception from MagicDetector");
        }
    }

}
