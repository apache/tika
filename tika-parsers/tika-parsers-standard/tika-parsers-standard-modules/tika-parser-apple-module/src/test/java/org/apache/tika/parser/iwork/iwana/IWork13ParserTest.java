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
package org.apache.tika.parser.iwork.iwana;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Limited testing for the iWorks 13 format parser, which
 * currently doesn't do much more than detection and handling
 * some embedded files....
 */
public class IWork13ParserTest extends TikaTest {
    private IWork13PackageParser iWorkParser;
    private ParseContext parseContext;

    @BeforeEach
    public void setUp() {
        iWorkParser = new IWork13PackageParser();
        parseContext = new ParseContext();
        parseContext.set(Parser.class, AUTO_DETECT_PARSER);
    }

    @Test
    public void testParseKeynote13() throws Exception {
        InputStream input = getResourceAsStream("/test-documents/testKeynote2013.key");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata, parseContext);

        assertEquals(9, metadata.size());
        assertEquals(IWork13PackageParser.IWork13DocumentType.KEYNOTE13.getType().toString(),
                metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testParseNumbers13() throws Exception {
        InputStream input = getResourceAsStream("/test-documents/testNumbers2013.numbers");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata, parseContext);

        // Currently parsing is a no-op, and we can't get the type without
        //  decoding the Snappy stream
        // TODO Test properly when a full Parser is added
        assertEquals(
                IWork13PackageParser.IWork13DocumentType.UNKNOWN13.getType().toString(),
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("preview.jpg", handler.toString().trim());
    }

    @Test
    public void testParsePages13() throws Exception {
        InputStream input = getResourceAsStream("/test-documents/testPages2013.pages");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata, parseContext);

        // Currently parsing is a no-op, and we can't get the type without
        //  decoding the Snappy stream
        // TODO Test properly when a full Parser is added
        assertEquals(
                IWork13PackageParser.IWork13DocumentType.UNKNOWN13.getType().toString(),
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("preview.jpg", handler.toString().trim());
    }

    @Test
    public void testNumbers13WFileName() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testNumbers2013.numbers");
        List<Metadata> metadataList = getRecursiveMetadata("testNumbers2013.numbers", metadata);
        assertEquals(2, metadataList.size());
        assertEquals("application/vnd.apple.numbers.13",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadataList.get(0).get("iworks:isMultiPage"));
        assertEquals("C5ED6463-575C-43B9-8FDA-1957B186C422",
                metadataList.get(0).get("iworks:versionUUID"));
        assertEquals("image/jpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }
}
