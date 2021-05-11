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
package org.apache.tika.parser.multiple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.DummyParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.utils.ParserUtils;

public class MultipleParserTest {
    /**
     * Tests how {@link AbstractMultipleParser} works out which
     * mime types to offer, based on the types of the parsers
     */
    @Test
    public void testMimeTypeSupported() {
        // TODO
        // Some media types
        Set<MediaType> onlyOct = Collections.singleton(MediaType.OCTET_STREAM);
        Set<MediaType> octAndText =
                new HashSet<>(Arrays.asList(MediaType.OCTET_STREAM, MediaType.TEXT_PLAIN));
        // TODO One with a subtype
    }

    /**
     * Test {@link FallbackParser}
     */
    @Test
    public void testFallback() throws Exception {
        ParseContext context = new ParseContext();
        BodyContentHandler handler;
        Metadata metadata;
        Parser p;
        String[] usedParsers;

        // Some media types
        Set<MediaType> onlyOct = Collections.singleton(MediaType.OCTET_STREAM);

        // Some parsers
        ErrorParser pFail = new ErrorParser();
        DummyParser pContent =
                new DummyParser(onlyOct, new HashMap<>(), "Fell back!");
        EmptyParser pNothing = new EmptyParser();


        // With only one parser defined, works as normal
        p = new FallbackParser(null, MetadataPolicy.DISCARD_ALL, pContent);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back!", handler.toString());

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(1, usedParsers.length);
        assertEquals(DummyParser.class.getName(), usedParsers[0]);


        // With a failing parser, will go to the working one
        p = new FallbackParser(null, MetadataPolicy.DISCARD_ALL, pFail, pContent);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back!", handler.toString());

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(2, usedParsers.length);
        assertEquals(ErrorParser.class.getName(), usedParsers[0]);
        assertEquals(DummyParser.class.getName(), usedParsers[1]);

        // Check we got an exception
        assertNotNull(metadata.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
        assertNotNull(metadata.get(ParserUtils.EMBEDDED_PARSER));
        assertEquals(ErrorParser.class.getName(), metadata.get(ParserUtils.EMBEDDED_PARSER));


        // Won't go past a working parser to a second one, stops after one works
        p = new FallbackParser(null, MetadataPolicy.DISCARD_ALL, pFail, pContent, pNothing);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back!", handler.toString());

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(2, usedParsers.length);
        assertEquals(ErrorParser.class.getName(), usedParsers[0]);
        assertEquals(DummyParser.class.getName(), usedParsers[1]);


        // TODO Check merge policies - First vs Discard
    }

    /**
     * Test for {@link SupplementingParser}
     */
    @Test
    public void testSupplemental() throws Exception {
        ParseContext context = new ParseContext();
        BodyContentHandler handler;
        Metadata metadata;
        Parser p;
        String[] usedParsers;

        // Some media types
        Set<MediaType> onlyOct = Collections.singleton(MediaType.OCTET_STREAM);

        // Some test metadata
        Map<String, String> m1 = new HashMap<>();
        m1.put("T1", "Test1");
        m1.put("TBoth", "Test1");
        Map<String, String> m2 = new HashMap<>();
        m2.put("T2", "Test2");
        m2.put("TBoth", "Test2");

        // Some parsers
        ErrorParser pFail = new ErrorParser();
        DummyParser pContent1 = new DummyParser(onlyOct, m1, "Fell back 1!");
        DummyParser pContent2 = new DummyParser(onlyOct, m2, "Fell back 2!");
        EmptyParser pNothing = new EmptyParser();


        // Supplemental doesn't support DISCARD
        try {
            new SupplementingParser(null, MetadataPolicy.DISCARD_ALL);
            fail("Discard shouldn't be supported");
        } catch (IllegalArgumentException e) {
            //swallow
        }


        // With only one parser defined, works as normal
        p = new SupplementingParser(null, MetadataPolicy.FIRST_WINS, pContent1);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back 1!", handler.toString());

        assertEquals("Test1", metadata.get("T1"));
        assertEquals("Test1", metadata.get("TBoth"));

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(1, usedParsers.length);
        assertEquals(DummyParser.class.getName(), usedParsers[0]);


        // Check the First, Last and All policies:
        // First Wins
        p = new SupplementingParser(null, MetadataPolicy.FIRST_WINS, pFail, pContent1, pContent2,
                pNothing);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back 1!Fell back 2!", handler.toString());

        assertEquals("Test1", metadata.get("T1"));
        assertEquals("Test2", metadata.get("T2"));
        assertEquals("Test1", metadata.get("TBoth"));

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(3, usedParsers.length);
        assertEquals(ErrorParser.class.getName(), usedParsers[0]);
        assertEquals(DummyParser.class.getName(), usedParsers[1]);
        assertEquals(EmptyParser.class.getName(), usedParsers[2]);


        // Last Wins
        p = new SupplementingParser(null, MetadataPolicy.LAST_WINS, pFail, pContent1, pContent2,
                pNothing);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back 1!Fell back 2!", handler.toString());

        assertEquals("Test1", metadata.get("T1"));
        assertEquals("Test2", metadata.get("T2"));
        assertEquals("Test2", metadata.get("TBoth"));

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(3, usedParsers.length);
        assertEquals(ErrorParser.class.getName(), usedParsers[0]);
        assertEquals(DummyParser.class.getName(), usedParsers[1]);
        assertEquals(EmptyParser.class.getName(), usedParsers[2]);


        // Merge
        p = new SupplementingParser(null, MetadataPolicy.KEEP_ALL, pFail, pContent1, pContent2,
                pNothing);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}), handler, metadata, context);
        assertEquals("Fell back 1!Fell back 2!", handler.toString());

        assertEquals("Test1", metadata.get("T1"));
        assertEquals("Test2", metadata.get("T2"));
        assertEquals(2, metadata.getValues("TBoth").length);
        assertEquals("Test1", metadata.getValues("TBoth")[0]);
        assertEquals("Test2", metadata.getValues("TBoth")[1]);

        usedParsers = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        assertEquals(3, usedParsers.length);
        assertEquals(ErrorParser.class.getName(), usedParsers[0]);
        assertEquals(DummyParser.class.getName(), usedParsers[1]);
        assertEquals(EmptyParser.class.getName(), usedParsers[2]);


        // Check the error details always come through, no matter the policy
        // TODO


        // Check that each parser gets its own ContentHandler if a factory was given
        // TODO
    }
}
