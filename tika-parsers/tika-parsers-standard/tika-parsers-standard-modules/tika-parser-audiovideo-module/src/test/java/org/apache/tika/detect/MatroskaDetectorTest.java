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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class MatroskaDetectorTest {

    private final MatroskaDetector detector = new MatroskaDetector();

    private InputStream getResourceAsStream(String resourcePath) {
        return this.getClass().getResourceAsStream(resourcePath);
    }

    @Test
    public void testDetectMKV() throws IOException {
        assertEquals(MediaType.application("x-matroska"),
                detector.detect(getResourceAsStream("/test-documents/sample-mkv.noext"),
                        new Metadata()));

        assertEquals(MediaType.application("x-matroska"),
                detector.detect(getResourceAsStream("/test-documents/testMKV.mkv"),
                        new Metadata()));


    }

    @Test
    public void testDetectWEBM() throws IOException {
        assertEquals(MediaType.video("webm"),
                detector.detect(getResourceAsStream("/test-documents/sample-webm.noext"),
                        new Metadata()));
    }

    @Test
    public void testNullAndShort() throws Exception {
        assertEquals(MediaType.OCTET_STREAM,
                detector.detect(null, new Metadata()));

        byte[] bytes = new byte[10];
        assertEquals(MediaType.OCTET_STREAM,
                detector.detect(UnsynchronizedByteArrayInputStream.builder().setByteArray(bytes).get(), new Metadata()));

        bytes = new byte[0];
        assertEquals(MediaType.OCTET_STREAM,
                detector.detect(UnsynchronizedByteArrayInputStream.builder().setByteArray(bytes).get(), new Metadata()));

    }
}
