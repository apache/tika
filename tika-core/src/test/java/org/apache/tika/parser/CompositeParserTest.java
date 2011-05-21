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
package org.apache.tika.parser;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class CompositeParserTest extends TestCase {

    public void testFindDuplicateParsers() {
        Parser a = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser b = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser c = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.OCTET_STREAM);
            }
        };

        CompositeParser composite = new CompositeParser(
                MediaTypeRegistry.getDefaultRegistry(), a, b, c);
        Map<MediaType, List<Parser>> duplicates =
            composite.findDuplicateParsers(new ParseContext());
        assertEquals(1, duplicates.size());
        List<Parser> parsers = duplicates.get(MediaType.TEXT_PLAIN);
        assertNotNull(parsers);
        assertEquals(2, parsers.size());
        assertEquals(a, parsers.get(0));
        assertEquals(b, parsers.get(1));
    }

    public void testDefaultParser() throws Exception {
       TikaConfig config = TikaConfig.getDefaultConfig();

       CompositeParser parser = (CompositeParser) config.getParser();

       // Check it has the full registry
       assertEquals(config.getMediaTypeRegistry(), parser.getMediaTypeRegistry());
    }

    public void testMimeTypeAliases() throws Exception {
       MediaType bmpCanonical = MediaType.image("x-ms-bmp");
       Map<String,String> bmpCanonicalMetadata = new HashMap<String, String>();
       bmpCanonicalMetadata.put("BMP", "True");
       bmpCanonicalMetadata.put("Canonical", "True");
       Parser bmpCanonicalParser = new DummyParser(
             new HashSet<MediaType>(Arrays.asList(bmpCanonical)),
             bmpCanonicalMetadata, null
       );
       
       MediaType bmpAlias = MediaType.image("bmp");
       Map<String,String> bmpAliasMetadata = new HashMap<String, String>();
       bmpAliasMetadata.put("BMP", "True");
       bmpAliasMetadata.put("Alias", "True");
       Parser bmpAliasParser = new DummyParser(
             new HashSet<MediaType>(Arrays.asList(bmpAlias)),
             bmpAliasMetadata, null
       );
       
       TikaConfig config = TikaConfig.getDefaultConfig();
       CompositeParser canonical = new CompositeParser(
             config.getMediaTypeRegistry(), bmpCanonicalParser
       );
       CompositeParser alias = new CompositeParser(
             config.getMediaTypeRegistry(), bmpAliasParser
       );
       CompositeParser both = new CompositeParser(
             config.getMediaTypeRegistry(), bmpCanonicalParser, bmpAliasParser
       );
       
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata;
       
       // Canonical and Canonical
       metadata = new Metadata();
       metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
       canonical.parse(new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
       assertEquals("True", metadata.get("BMP"));
       assertEquals("True", metadata.get("Canonical"));
       
       
       // Alias and Alias
       metadata = new Metadata();
       metadata.add(Metadata.CONTENT_TYPE, bmpAlias.toString());
       alias.parse(new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
       assertEquals("True", metadata.get("BMP"));
       assertEquals("True", metadata.get("Alias"));
       
       
       // Alias type and Canonical parser
       metadata = new Metadata();
       metadata.add(Metadata.CONTENT_TYPE, bmpAlias.toString());
       canonical.parse(new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
       assertEquals("True", metadata.get("BMP"));
       assertEquals("True", metadata.get("Canonical"));
       
       
       // Canonical type and Alias parser
       metadata = new Metadata();
       metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
       alias.parse(new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
       assertEquals("True", metadata.get("BMP"));
       assertEquals("True", metadata.get("Alias"));
       
       
       // And when both are there, will go for the last one
       //  to be registered (which is the alias one)
       metadata = new Metadata();
       metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
       both.parse(new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
       assertEquals("True", metadata.get("BMP"));
       assertEquals("True", metadata.get("Alias"));
    }
}
