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

        assertEquals("heic", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Major Brand"));
        assertEquals("512 pixels", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Width"));
        assertEquals("512 pixels", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Height"));
        assertEquals("image/heic", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("23.177917", metadata.get(Metadata.LATITUDE));
        assertEquals("113.394317", metadata.get(Metadata.LONGITUDE));

        assertEquals("2018-02-05T07:11:43Z", metadata.get(Geographic.TIMESTAMP));

        IOUtils.closeQuietly(stream);
    }

}
