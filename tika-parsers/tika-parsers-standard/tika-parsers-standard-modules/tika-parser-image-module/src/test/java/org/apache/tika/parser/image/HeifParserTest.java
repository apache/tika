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

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;


public class HeifParserTest {

    Parser parser = new AutoDetectParser();

    /*
        Example photo in test-documents (IMG_1034.heic)
        are in the public domain.  These files were retrieved from:
        https://github.com/drewnoakes/metadata-extractor-images/tree/master/heic
     */
    @Test
    public void testSimple() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream = getClass().getResourceAsStream("/test-documents/IMG_1034.heic");

        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("heic", metadata.get("Major Brand"));
        assertEquals("512 pixels", metadata.get("Width"));
        assertEquals("512 pixels", metadata.get("Height"));
        assertEquals("image/heic", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("23.177917", metadata.get(Metadata.LATITUDE));
        assertEquals("113.394317", metadata.get(Metadata.LONGITUDE));

        assertEquals("2018-02-05T07:11:43Z", metadata.get(Geographic.TIMESTAMP));

        IOUtils.closeQuietly(stream);
    }

    /*
        testHEIC_livePhoto.heic carries the unmodified EXIF payload (including
        the Apple maker note) of the still half of an Apple Live Photo
        (iPhone 15 Pro, iOS 18.5), retrieved from the MIT licensed osxphotos
        test suite:
        https://github.com/RhetTbull/osxphotos/tree/main/tests/Test-Live-15.7.2.photoslibrary
        The EXIF item was repackaged into a minimal HEIC container (the
        picture item data is replaced by filler bytes) to keep the fixture
        small.
     */
    @Test
    public void testAppleLivePhotoMakerNote() throws Exception {
        //the content identifier pairs the still with its video half; the
        //Live Photo ID (Apple maker note tag 0x0017) is written as LONG8 and
        //was dropped before metadata-extractor 2.21.0. TIKA-4776
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testHEIC_livePhoto.heic")) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());

            assertEquals("CD28D161-D5EC-4CDE-8B60-DACCC1363B6B",
                    metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Content Identifier"));
            assertEquals("5283876",
                    metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Live Photo ID"));
        }
    }

}
