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
package org.apache.tika.parser.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

public class FLVParserTest {

    /**
     * Deeply nested AMF objects used to recurse in readAMFData until the stack
     * overflowed (an uncaught Error); the reader must bound the nesting depth.
     */
    @Test
    public void testAmfNestingIsBounded() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(3); //top-level AMF object marker
        for (int i = 0; i < 100_000; i++) {
            bos.write(new byte[]{0, 0}); //empty key (uint16 length 0)
            bos.write(3);                //value type = object -> recurse
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        assertThrows(IOException.class, () -> new FLVParser().readAMFData(dis, -1));
    }

    @Test
    public void testFLV() throws Exception {
        String path = "/test-documents/testFLV.flv";
        Metadata metadata = new Metadata();

        String content =
                new Tika().parseToString(FLVParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("", content);
        assertEquals("video/x-flv", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("true", metadata.get("flv:hasVideo"));
        assertEquals("false", metadata.get("flv:stereo"));
        assertEquals("true", metadata.get("flv:hasAudio"));
        assertEquals("120.0", metadata.get("flv:height"));
        assertEquals("16.0", metadata.get("flv:audiosamplesize"));
    }

}
