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

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.parser.DummyParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;

public class MultipleParserTest {
    /**
     * Tests how {@link AbstractMultipleParser} works out which
     *  mime types to offer, based on the types of the parsers
     */
    @Test
    public void testMimeTypeSupported() {
        // TODO
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
        Set<MediaType> octAndText = new HashSet<MediaType>(Arrays.asList(
                MediaType.OCTET_STREAM, MediaType.TEXT_PLAIN));
        
        // Some parsers
        ErrorParser pFail = new ErrorParser();
        DummyParser pContent = new DummyParser(onlyOct, new HashMap<String,String>(),
                                               "Fell back!");
        EmptyParser pNothing = new EmptyParser();
        
        
        // With only one parser defined, works as normal
        p = new FallbackParser(null, MetadataPolicy.DISCARD_ALL, pContent);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[] {0,1,2,3,4}), handler, metadata, context);
        assertEquals("Fell back!", handler.toString());
       
        usedParsers = metadata.getValues("X-Parsed-By");
        assertEquals(1, usedParsers.length);
        assertEquals(DummyParser.class.getName(), usedParsers[0]);
        
        
        // With a failing parser, will go to the working one
        p = new FallbackParser(null, MetadataPolicy.DISCARD_ALL, pFail, pContent);

        metadata = new Metadata();
        handler = new BodyContentHandler();
        p.parse(new ByteArrayInputStream(new byte[] {0,1,2,3,4}), handler, metadata, context);
        assertEquals("Fell back!", handler.toString());
       
        usedParsers = metadata.getValues("X-Parsed-By");
        assertEquals(2, usedParsers.length);
        assertEquals(DummyParser.class.getName(), usedParsers[0]);
        
        // TODO Check we got an exception
        
        
        // Won't go past the working one
        // TODO
    }
    
    /**
     * Test for {@link SupplementingParser}
     */
    @Test
    public void testSupplemental() throws Exception {
        // TODO 
    }
}
