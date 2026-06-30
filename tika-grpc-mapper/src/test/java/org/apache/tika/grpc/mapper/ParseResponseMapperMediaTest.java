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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.MediaMetadata;
import org.apache.tika.grpc.v1.ParseResponse;

class ParseResponseMapperMediaTest extends ParseFixtureSupport {

    @Test
    void mapsMp3TagsAndAudioProperties() throws Exception {
        Assumptions.assumeTrue(getResourceAsUrl("/test-documents/test2.mp3") != null,
                "test2.mp3 fixture not on classpath");

        ParseResponse response = map(parseBody("test2.mp3"), "test2.mp3");

        assertTrue(response.hasMedia());
        MediaMetadata media = response.getMedia();
        assertEquals("Plus loin vers l'ouest", media.getTitle());
        assertEquals("Merzhin", media.getCreator());
        assertEquals("44100", media.getSamplerate());
        assertEquals("2", media.getChannels());
    }

}
