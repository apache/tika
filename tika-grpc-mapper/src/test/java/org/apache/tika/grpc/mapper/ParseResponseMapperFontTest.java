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

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.ParseResponse;

class ParseResponseMapperFontTest extends ParseFixtureSupport {

    @Test
    void mapsTrueTypeFontMimeAndResourceName() throws Exception {
        ParseResponse response = map(parseBody("testTrueType3.ttf"), "testTrueType3.ttf");

        assertTrue(response.hasFont());
        assertEquals("application/x-font-ttf", response.getContentType());
        assertTrue(response.getFont().getMimeType().isEmpty());
        assertEquals("Open Sans Bold", response.getDublinCore().getTitle());
    }

    @Test
    void mapsAdobeFontMetricMimeType() throws Exception {
        ParseResponse response = map(parseBody("testAFM.afm"), "testAFM.afm");

        assertTrue(response.hasFont());
        assertEquals("application/x-font-adobe-metric", response.getContentType());
        assertTrue(response.getFont().getMimeType().isEmpty());
        assertEquals("TestFullName", response.getDublinCore().getTitle());
    }

}
