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

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Test cases for the {@link TypeDetector} class.
 */
public class TypeDetectorTest extends TestCase {

    private Detector detector = new TypeDetector();
    
    private static final Map<String, String> params = new
        TreeMap<String, String>();
    static{
        params.put("a", "b");
    }
    
    private static final MediaType TEXT_PLAIN_A_EQ_B = 
          new MediaType("text", "plain", params);

    public void testDetect() {
        assertDetect(MediaType.TEXT_PLAIN, "text/plain");
        assertDetect(MediaType.TEXT_PLAIN, "TEXT/PLAIN");
        assertDetect(MediaType.TEXT_PLAIN, " text/\tplain\n");
        assertDetect(TEXT_PLAIN_A_EQ_B, "text/plain; a=b");
        assertDetect(TEXT_PLAIN_A_EQ_B, "\ttext/plain; a=b\n");

        assertDetect(MediaType.OCTET_STREAM, "text\\plain");

        // test also the zero input cases
        assertDetect(MediaType.OCTET_STREAM, "");
        assertDetect(MediaType.OCTET_STREAM, null);
        try {
            assertEquals(
                    MediaType.OCTET_STREAM,
                    detector.detect(null, new Metadata()));
        } catch (IOException e) {
            fail("TypeDetector should never throw an IOException");
        }
    }

    private void assertDetect(MediaType type, String name){
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, name);
        try {
            assertEquals(type, detector.detect(null, metadata));
        } catch (IOException e) {
            fail("TypeDetector should never throw an IOException");
        }
    }

}
