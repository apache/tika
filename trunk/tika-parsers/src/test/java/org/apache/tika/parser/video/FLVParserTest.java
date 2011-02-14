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

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

public class FLVParserTest extends TestCase {

    public void testFLV() throws Exception {
        String path = "/test-documents/testFLV.flv";
        Metadata metadata = new Metadata();

        String content = new Tika().parseToString(
                FLVParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("", content);
        assertEquals("video/x-flv", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadata.get("hasVideo"));
        assertEquals("false", metadata.get("stereo"));
        assertEquals("true", metadata.get("hasAudio"));
        assertEquals("120.0", metadata.get("height"));
        assertEquals("16.0", metadata.get("audiosamplesize"));
    }

}
