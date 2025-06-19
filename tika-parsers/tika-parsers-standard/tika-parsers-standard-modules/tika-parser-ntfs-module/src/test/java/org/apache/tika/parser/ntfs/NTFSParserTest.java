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
package org.apache.tika.parser.ntfs;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;


public class NTFSParserTest extends TikaTest {

    // This test currently uses a very simple dummy file "test-ntfs.img"
    // which only has the "NTFS" signature. SleuthKit will not be able to
    // fully parse this as a valid NTFS image.
    // A more comprehensive test would require a real, small NTFS image
    // containing known files and directories.

    // protected ParseContext recursingContext;
    // private Parser autoDetectParser;
    // private TypeDetector typeDetector;


    // @BeforeEach
    // public void setUp() throws Exception {
    //     typeDetector = new TypeDetector();
    //     autoDetectParser = new AutoDetectParser(typeDetector);
    //     recursingContext = new ParseContext();
    //     recursingContext.set(Parser.class, autoDetectParser);
    // }


    @Test
    public void testNTFSSimple() throws Exception {
        // XMLResult xml = getXML("small_ntfs_test_image.img");
        XMLResult xml = getXML("raw_ntfs_image.img");
        System.out.println(xml.xml);
        assert xml.xml.contains("NTFSParser");

        // ContentHandler handler = new BodyContentHandler();
        // Metadata metadata = new Metadata();

        // try (InputStream stream = getResourceAsStream("/test-documents/raw_ntfs_image.img")) {
        //     AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
        // }

        // String content = handler.toString();
        // System.out.println(content);
        // assertContains("testWORD.doc", content);

    }
}
