/*
 * Copyright 2016 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.image;

import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * Test class for the ICNSParser
 */
public class ICNSParserTest {
    private final Parser parser = new ICNSParser();

    /**
     * Tests a very basic icns file, with one icon and no masks
     */
    @Test
    public void testICNS_basic() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/icns");
        metadata.set("Icons count", "1");
        metadata.set("Icons details", "512x512 (JPEG 2000 or PNG format)");
              
        
        InputStream stream =
                getClass().getResourceAsStream("/test-documents/testICNS_basic.icns");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
    }
    
    /**
     * Tests a file with multiple icons and masks
     */
    @Test
    public void testICNS() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/icns");
        metadata.set("Icons count", "2");
        metadata.set("Icons details", "16x16 (24 bpp), 32x32 (24 bpp)");
        metadata.set("Masked icon count", "2");
        metadata.set("Masked icon details", "16x16 (8 bpp), 32x32 (8 bpp)");
        
        
        InputStream stream =
                getClass().getResourceAsStream("/test-documents/testICNS.icns");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
    }
}
