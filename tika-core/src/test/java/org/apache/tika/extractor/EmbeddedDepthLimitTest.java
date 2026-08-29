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
package org.apache.tika.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

/**
 * {@code maxDepth} of {@link EmbeddedLimits} counts embedding levels, not the
 * parsers a parse passes through (TIKA-4857): with two composite layers per
 * document, as {@code AutoDetectParser} over {@code DefaultParser} has, the
 * limit used to stop one level early for every value above 1.
 */
public class EmbeddedDepthLimitTest {

    private static final MediaType NESTED = MediaType.application("x-nested");

    /**
     * Every document of this type contains one document of the same type,
     * five levels deep.
     */
    private static class NestingParser implements Parser {
        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(NESTED);
        }

        @Override
        public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            int level = Integer.parseInt(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (level >= 5) {
                return;
            }
            Metadata child = new Metadata();
            child.set(HttpHeaders.CONTENT_TYPE, NESTED.toString());
            EmbeddedDocumentExtractor extractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            if (extractor.shouldParseEmbedded(child, context)) {
                try (TikaInputStream tis = TikaInputStream.get(
                        String.valueOf(level + 1).getBytes(StandardCharsets.UTF_8))) {
                    extractor.parseEmbedded(tis, handler, child, context, false);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"-1, 6, false", "0, 1, true", "1, 2, true", "2, 3, true", "3, 4, true",
            "5, 6, false"})
    public void testMaxDepthCountsEmbeddingLevels(int maxDepth, int expectedDocuments,
                                                  boolean limitReached) throws Exception {
        //two composite layers, as AutoDetectParser over DefaultParser
        Parser parser = new CompositeParser(new org.apache.tika.mime.MediaTypeRegistry(),
                new CompositeParser(new org.apache.tika.mime.MediaTypeRegistry(),
                        new NestingParser()));
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE,
                        -1));
        ParseContext context = new ParseContext();
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxDepth(maxDepth);
        context.set(EmbeddedLimits.class, limits);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, NESTED.toString());

        try (TikaInputStream tis = TikaInputStream.get("0".getBytes(StandardCharsets.UTF_8))) {
            wrapper.parse(tis, handler, metadata, context);
        }
        List<Metadata> documents = handler.getMetadataList();
        assertEquals(expectedDocuments, documents.size(), documents.toString());
        //the wrapper lists a document once it is finished, so the deepest comes first
        Set<Integer> depths = new HashSet<>();
        for (Metadata document : documents) {
            depths.add(document.getInt(TikaCoreProperties.EMBEDDED_DEPTH));
        }
        for (int depth = 0; depth < expectedDocuments; depth++) {
            assertTrue(depths.contains(depth), "missing depth " + depth + " in " + depths);
        }
        if (limitReached) {
            assertEquals("true",
                    documents.get(0).get(TikaCoreProperties.EMBEDDED_DEPTH_LIMIT_REACHED));
        } else {
            assertNull(documents.get(0).get(TikaCoreProperties.EMBEDDED_DEPTH_LIMIT_REACHED));
        }
    }
}
