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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;


public class WebPParserTest {

    Parser parser = new AutoDetectParser();
/*
    Two photos in test-documents (testWebp_Alpha_Lossy.webp and testWebp_Alpha_Lossless.webp)
    are in the public domain.  These files were retrieved from:
    https://github.com/drewnoakes/metadata-extractor-images/tree/master/webp
    These photos are also available here:
    https://developers.google.com/speed/webp/gallery2#webp_links
    Credits for the photo:
    "Free Stock Photo in High Resolution - Yellow Rose 3 - Flowers"
    Image Author: Jon Sullivan
 */
    @Test
    public void testSimple() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream =
                getClass().getResourceAsStream("/test-documents/testWebp_Alpha_Lossy.webp");

        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("301", metadata.get("Image Height"));
        assertEquals("400", metadata.get("Image Width"));
        assertEquals("true", metadata.get("Has Alpha"));
        assertEquals("false", metadata.get("Is Animation"));
        assertEquals("image/webp", metadata.get(Metadata.CONTENT_TYPE));

        IOUtils.closeQuietly(stream);

        metadata = new Metadata();
        stream = getClass().getResourceAsStream("/test-documents/testWebp_Alpha_Lossless.webp");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        //unfortunately, there isn't much metadata in lossless
        assertEquals("image/webp", metadata.get(Metadata.CONTENT_TYPE));

    }

}
