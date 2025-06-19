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
package org.apache.tika.parser.ntfs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;


public class NTFSDetectorTest {

    private NTFSDetector detector;

    @BeforeEach
    public void setUp() {
        detector = new NTFSDetector();
    }

    @Test
    public void testDetectNTFS() throws Exception {
        // Load the dummy NTFS image created earlier
        // This stream represents a file starting with "AAA NTFS..."
        try (InputStream stream = getClass().getResourceAsStream("ntfs_test.img")) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.application("x-ntfs-image"), mediaType,
                    "Should detect NTFS image");
        }
    }

    @Test
    public void testDetectNonNTFS() throws Exception {
        // A simple text file, definitely not NTFS
        byte[] nonNtfsData = "This is a simple text file.".getBytes(StandardCharsets.UTF_8);
        try (InputStream stream = TikaInputStream.get(nonNtfsData)) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for non-NTFS file");
        }
    }

    @Test
    public void testDetectEmptyStream() throws Exception {
        try (InputStream stream = TikaInputStream.get(new byte[0])) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for an empty stream");
        }
    }

    @Test
    public void testDetectShortStream() throws Exception {
        // Stream shorter than the signature itself
        byte[] shortData = "NTF".getBytes(StandardCharsets.US_ASCII);
        try (InputStream stream = TikaInputStream.get(new byte[] {0x00, 0x00, 0x00, 'N', 'T', 'F'})) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for a stream too short to contain the full signature");
        }
    }

    @Test
    public void testDetectStreamWithDifferentSignature() throws Exception {
        byte[] diffSigData = "AAA NOTFS sig".getBytes(StandardCharsets.US_ASCII);
        try (InputStream stream = TikaInputStream.get(diffSigData)) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for a stream with a different signature");
        }
    }

    @Test
    public void testDetectNullStream() throws Exception {
        Metadata metadata = new Metadata();
        MediaType mediaType = detector.detect(null, metadata);
        assertEquals(MediaType.OCTET_STREAM, mediaType,
                "Should return OCTET_STREAM for a null stream");
    }
}
