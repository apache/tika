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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import junit.framework.TestCase;

/**
 * Test cases for the {@link NameDetector} class.
 */
public class NameDetectorTest extends TestCase {

    private Detector detector;

    protected void setUp() {
        Map<Pattern, MediaType> patterns = new HashMap<Pattern, MediaType>();
        patterns.put(
                Pattern.compile(".*\\.txt", Pattern.CASE_INSENSITIVE),
                MediaType.TEXT_PLAIN);
        patterns.put(Pattern.compile("README"), MediaType.TEXT_PLAIN);
        detector = new NameDetector(patterns);
    }

    public void testDetect() {
        assertDetect(MediaType.TEXT_PLAIN, "text.txt");
        assertDetect(MediaType.TEXT_PLAIN, "text.txt ");    // trailing space
        assertDetect(MediaType.TEXT_PLAIN, "text.txt\n");   // trailing newline
        assertDetect(MediaType.TEXT_PLAIN, "text.txt?a=b"); // URL query
        assertDetect(MediaType.TEXT_PLAIN, "text.txt#abc"); // URL fragment
        assertDetect(MediaType.TEXT_PLAIN, "text%2Etxt");   // URL encoded
        assertDetect(MediaType.TEXT_PLAIN, "text.TXT");     // case insensitive
        assertDetect(MediaType.OCTET_STREAM, "text.txt.gz");

        assertDetect(MediaType.TEXT_PLAIN, "README");
        assertDetect(MediaType.TEXT_PLAIN, " README ");     // space around
        assertDetect(MediaType.TEXT_PLAIN, "\tREADME\n");   // other whitespace
        assertDetect(MediaType.TEXT_PLAIN, "/a/README");    // leading path
        assertDetect(MediaType.TEXT_PLAIN, "\\b\\README");  // windows path
        assertDetect(MediaType.OCTET_STREAM, "ReadMe");     // case sensitive
        assertDetect(MediaType.OCTET_STREAM, "README.NOW");

        // tough one
        assertDetect(
                MediaType.TEXT_PLAIN,
                " See http://www.example.com:1234/README.txt?a=b#c \n");
        assertDetect(MediaType.TEXT_PLAIN, "See README.txt"); // even this!
        assertDetect(MediaType.OCTET_STREAM, "See README");   // but not this

        // test also the zero input cases
        assertDetect(MediaType.OCTET_STREAM, "");
        assertDetect(MediaType.OCTET_STREAM, null);
        try {
            assertEquals(
                    MediaType.OCTET_STREAM,
                    detector.detect(null, new Metadata()));
        } catch (IOException e) {
            fail("NameDetector should never throw an IOException");
        }
    }

    private void assertDetect(MediaType type, String name){
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, name);
        try {
            assertEquals(type, detector.detect(null, metadata));
        } catch (IOException e) {
            fail("NameDetector should never throw an IOException");
        }
    }

}
