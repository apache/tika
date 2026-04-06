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
package org.apache.tika.parser.microsoft.rtf.jflex;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Tests for {@link RTFEmbeddedHandler} driven by the JFlex tokenizer,
 * both standalone and integrated into the decapsulator.
 */
public class RTFEmbeddedHandlerTest {

    private static ParseContext buildContext(List<Metadata> extracted) {
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext parseContext,
                                      boolean outputHtml) {
                Metadata copy = new Metadata();
                for (String name : metadata.names()) {
                    for (String val : metadata.getValues(name)) {
                        copy.add(name, val);
                    }
                }
                extracted.add(copy);
            }
        });
        return context;
    }

    /**
     * Process an RTF file through the tokenizer + state + embedded handler directly.
     */
    private List<Metadata> extractEmbeddedDirect(String resourceName)
            throws IOException, SAXException, TikaException {
        List<Metadata> extracted = new ArrayList<>();
        ParseContext context = buildContext(extracted);
        ContentHandler handler = new DefaultHandler();
        RTFEmbeddedHandler embHandler = new RTFEmbeddedHandler(handler, context, 20 * 1024);
        RTFState state = new RTFState();

        try (InputStream is = getClass().getResourceAsStream("/test-documents/" + resourceName);
             Reader reader = new InputStreamReader(is, StandardCharsets.US_ASCII)) {

            RTFTokenizer tokenizer = new RTFTokenizer(reader);
            RTFToken tok;

            while ((tok = tokenizer.yylex()) != null) {
                if (tok.getType() == RTFTokenType.EOF) {
                    break;
                }
                boolean consumed = state.processToken(tok);
                if (!consumed) {
                    RTFGroupState closingGroup =
                            (tok.getType() == RTFTokenType.GROUP_CLOSE)
                                    ? state.getLastClosedGroup() : null;
                    embHandler.processToken(tok, state, closingGroup);
                }
            }
        }
        return extracted;
    }

    @Test
    public void testEmbeddedFiles() throws Exception {
        List<Metadata> embedded = extractEmbeddedDirect("testRTFEmbeddedFiles.rtf");
        assertTrue(embedded.size() > 0,
                "should extract at least one embedded object from testRTFEmbeddedFiles.rtf");
    }

    @Test
    public void testPictExtraction() throws Exception {
        // Verifies the handler doesn't crash on a typical RTF file
        extractEmbeddedDirect("testRTF.rtf");
    }

    @Test
    public void testEmbeddedObjectMetadata() throws Exception {
        List<Metadata> embedded = extractEmbeddedDirect("testRTFEmbeddedFiles.rtf");
        if (embedded.size() > 0) {
            boolean hasName = false;
            for (Metadata m : embedded) {
                String name = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                if (name != null && !name.isEmpty()) {
                    hasName = true;
                    break;
                }
            }
            assertTrue(hasName, "at least one embedded should have a resource name");
        }
    }
}
