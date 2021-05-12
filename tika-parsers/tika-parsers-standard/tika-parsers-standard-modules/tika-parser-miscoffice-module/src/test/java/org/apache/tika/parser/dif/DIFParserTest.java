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
package org.apache.tika.parser.dif;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class DIFParserTest extends TikaTest {

    @Test
    public void testDifMetadata() throws Exception {
        Parser parser = new DIFParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/Zamora2010.dif")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals(metadata.get("DIF-Entry_ID"), "00794186-48f9-11e3-9dcb-00c0f03d5b7c");
        assertEquals(metadata.get("DIF-Metadata_Name"), "ACADIS IDN DIF");

        String content = handler.toString();
        assertContains("Title: Zamora 2010 Using Sediment Geochemistry", content);
        assertContains("Southernmost_Latitude : 78.833", content);
        assertContains("Northernmost_Latitude : 79.016", content);
        assertContains("Westernmost_Longitude : 11.64", content);
        assertContains("Easternmost_Longitude : 13.34", content);
    }
}
