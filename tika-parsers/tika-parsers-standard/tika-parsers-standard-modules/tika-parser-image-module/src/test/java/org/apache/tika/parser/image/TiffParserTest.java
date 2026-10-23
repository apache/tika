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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;

public class TiffParserTest extends TikaTest {

    @Test
    public void testBigTiff() throws Exception {
        Metadata metadata = getXML("testBigTIFF.tif").metadata;
        assertEquals("image/tiff", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertEquals("16", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals("12", metadata.get(TIFF.IMAGE_LENGTH));
        assertEquals("Apache", metadata.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("Tika BigTIFF", metadata.get(TIFF.EQUIPMENT_MODEL));
        assertEquals("tika-test", metadata.get(TIFF.SOFTWARE));
        assertEquals("Inch", metadata.get(TIFF.RESOLUTION_UNIT));
        assertEquals("72.0", metadata.get(TIFF.RESOLUTION_HORIZONTAL));
        assertEquals("0.004", metadata.get(TIFF.EXPOSURE_TIME));
        assertEquals("5.6", metadata.get(TIFF.F_NUMBER));
        assertEquals("200", metadata.get(TIFF.ISO_SPEED_RATINGS));
        assertEquals("50.0", metadata.get(TIFF.FOCAL_LENGTH));
        assertEquals("2024-05-06T07:08:09", metadata.get(TIFF.ORIGINAL_DATE));
    }
}
