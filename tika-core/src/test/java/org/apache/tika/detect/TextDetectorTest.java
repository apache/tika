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
import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Test cases for the {@link TextDetector} class.
 */
public class TextDetectorTest extends TestCase {

    private final Detector detector = new TextDetector();

    public void testDetectNull() throws Exception {
        assertEquals(
                MediaType.OCTET_STREAM,
                detector.detect(null, new Metadata()));
    }

    /**
     * Test for type detection of empty documents.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-483">TIKA-483</a>
     */
    public void testDetectEmpty() throws Exception {
        assertNotText(new byte[0]);
    }

    public void testDetectText() throws Exception {
        assertText("Hello, World!".getBytes("UTF-8"));
        assertText(" \t\r\n".getBytes("UTF-8"));
        assertNotText(new byte[] { -1, -2, -3, 0x09, 0x0A, 0x0C, 0x0D, 0x1B });
        assertNotText(new byte[] { 0 });
        assertNotText(new byte[] { 'H', 'e', 'l', 'l', 'o', 0 });

        byte[] data = new byte[512];
        Arrays.fill(data, (byte) '.');
        assertText(data);
        Arrays.fill(data, 100, 110, (byte) 0x1f);
        assertText(data); // almost text
        Arrays.fill(data, 100, 111, (byte) 0x1f);
        assertNotText(data); // no longer almost text, too many control chars
        Arrays.fill(data, (byte) 0x1f);
        assertNotText(data);

        data = new byte[513];
        Arrays.fill(data, (byte) '.');
        data[0] = 0x1f;
        assertText(data);
        Arrays.fill(data, 100, 150, (byte) 0x83);
        assertText(data); // almost text
        Arrays.fill(data, 100, 200, (byte) 0x83);
        assertNotText(data); // no longer almost text, too many non-ASCII
        Arrays.fill(data, (byte) 0x1f);
        assertNotText(data);
    }

    private void assertText(byte[] data) {
        try {
            InputStream stream = new ByteArrayInputStream(data);
            assertEquals(
                    MediaType.TEXT_PLAIN,
                    detector.detect(stream, new Metadata()));

            // Test that the stream has been reset
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], (byte) stream.read());
            }
            assertEquals(-1, stream.read());
        } catch (IOException e) {
            fail("Unexpected exception from TextDetector");
        }
    }

    private void assertNotText(byte[] data) {
        try {
            assertEquals(
                    MediaType.OCTET_STREAM,
                    detector.detect(
                            new ByteArrayInputStream(data), new Metadata()));
        } catch (IOException e) {
            fail("Unexpected exception from TextDetector");
        }
    }

}
