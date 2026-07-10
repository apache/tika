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
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Google;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Google Motion Photos keep their metadata in a vendor XMP namespace. Two
 * variants share that namespace: the current Motion Photo format and the legacy
 * MicroVideo format. Both are covered here.
 */
public class MotionPhotoXmpTest extends TikaTest {

    /** XMP from a vendor namespace (Google Motion Photo) is exposed, not dropped. */
    @Test
    public void testMotionPhotoXmpIsExposed() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        try (TikaInputStream tis =
                     getResourceAsStream("/test-documents/testJPEG_MotionPhoto.jpg")) {
            new JpegParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("1", metadata.get("Camera:MotionPhoto"));
        assertEquals("1", metadata.get("Camera:MotionPhotoVersion"));
        assertEquals("500000", metadata.get("Camera:MotionPhotoPresentationTimestampUs"));
        // the camera scalars are also reachable through the declared, typed property
        assertEquals("1", metadata.get(Google.MOTION_PHOTO));
        // The embedded video item (its byte length lets a client range-fetch the
        // video without downloading the whole file) is exposed too.
        assertEquals("MotionPhoto",
                metadata.get("xmp-raw:Container:Directory[2]/Container:Item/Item:Semantic"));
        assertEquals("122562",
                metadata.get("xmp-raw:Container:Directory[2]/Container:Item/Item:Length"));
    }

    /** Keys use the canonical prefix even when the file declares another (GCamera). */
    @Test
    public void testCanonicalPrefixIsStable() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        try (TikaInputStream tis =
                     getResourceAsStream("/test-documents/testJPEG_MicroVideo.jpg")) {
            new JpegParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertEquals("1", metadata.get("Camera:MicroVideo"));
        assertEquals("4182318", metadata.get("Camera:MicroVideoOffset"));
        assertNull(metadata.get("GCamera:MicroVideoOffset"));
    }
}
