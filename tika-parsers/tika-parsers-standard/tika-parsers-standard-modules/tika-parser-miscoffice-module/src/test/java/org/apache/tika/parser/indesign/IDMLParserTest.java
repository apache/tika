/**
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

package org.apache.tika.parser.indesign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMP;
import org.apache.tika.parser.Parser;


/**
 * Test case for the IDML Parser.
 */
public class IDMLParserTest extends TikaTest {

    /**
     * Shared IDMLParser instance.
     */
    private final Parser parser = new IDMLParser();

    //TODO PDFBOX30 xmp related stuff fails with PDFBox 3.0, see PDFBOX-5649
    @Test
    public void testParserToText() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testIndesign.idml", parser, metadata);
        assertEquals("3", metadata.get("TotalPageCount"));
        assertEquals("2", metadata.get("MasterSpreadPageCount"));
        assertEquals("1", metadata.get("SpreadPageCount"));
        assertEquals("application/vnd.adobe.indesign-idml-package", metadata.get(Metadata.CONTENT_TYPE));
        // failure below could be because of missing javax.xml.bind if xmpbox 2.* is used
        // check the swallowed throwable in XMPMetadataExtractor.parse()
        assertEquals("2020-09-20T20:07:44Z", metadata.get(XMP.CREATE_DATE));
        assertEquals("2020-09-20T20:07:44Z", metadata.get(XMP.MODIFY_DATE));
        assertEquals("Adobe InDesign CC 14.0 (Windows)", metadata.get(XMP.CREATOR_TOOL));
        assertContains("Lorem ipsum dolor sit amet, consectetur adipiscing elit", content);
    }

    //TODO PDFBOX30 xmp related stuff fails with PDFBox 3.0, see PDFBOX-5649
    @Test
    public void testParserToXML() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testIndesign.idml", parser, metadata).xml;
        // failure below could be because of missing javax.xml.bind if xmpbox 2.* is used
        // check the swallowed throwable in XMPMetadataExtractor.parse()
        assertEquals("Adobe InDesign CC 14.0 (Windows)", metadata.get(XMP.CREATOR_TOOL));
        assertEquals("3", metadata.get("TotalPageCount"));
        assertContains("<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit", xml);
        assertContains("<meta name=\"xmp:CreatorTool\" content=\"Adobe InDesign CC 14.0 (Windows)\" />", xml);
    }

}
