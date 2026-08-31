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
package org.apache.tika.parser.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

public class CoverArtTest {

    @Test
    public void testFrontCoverWins() {
        //back cover, front cover: the front cover, wherever it is
        assertEquals(1, CoverArt.thumbnailIndex(Arrays.asList(4, 3)));
        assertEquals(0, CoverArt.thumbnailIndex(Arrays.asList(3, 0)));
    }

    @Test
    public void testOtherBeatsClassifiedNonCovers() {
        //back cover, leaflet, "Other": the unclassified one is the main art
        assertEquals(2, CoverArt.thumbnailIndex(Arrays.asList(4, 5, 0)));
        //unknown type counts like "Other"
        assertEquals(1, CoverArt.thumbnailIndex(Arrays.asList(4, -1)));
    }

    @Test
    public void testFirstPictureAsLastResort() {
        assertEquals(0, CoverArt.thumbnailIndex(Arrays.asList(4, 5)));
        assertEquals(-1, CoverArt.thumbnailIndex(Collections.emptyList()));
    }
}
