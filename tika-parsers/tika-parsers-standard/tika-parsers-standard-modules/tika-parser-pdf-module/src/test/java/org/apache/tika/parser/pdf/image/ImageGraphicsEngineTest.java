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
package org.apache.tika.parser.pdf.image;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.XHTMLContentHandler;

public class ImageGraphicsEngineTest {

    // TIKA-4848: record on the parent, not the per-image metadata that is discarded here
    @Test
    public void testIOExceptionRecordedOnParentMetadata() throws Exception {
        Metadata parentMetadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new DefaultHandler(), parentMetadata, parseContext);
        xhtml.startDocument();

        ImageGraphicsEngine engine = new ImageGraphicsEngine(new PDPage(), 1,
                new NoOpEmbeddedDocumentExtractor(), new PDFParserConfig(), new HashMap<>(),
                new AtomicInteger(0), xhtml, parentMetadata, parseContext) {
            //the only PDImage dereference before writeToBuffer, so a null image suffices
            @Override
            protected String getSuffix(PDImage pdImage, Metadata metadata) {
                return "png";
            }

            @Override
            protected BufferedImage writeToBuffer(PDImage pdImage, String suffix,
                                                  boolean directJPEG, OutputStream out)
                    throws IOException {
                throw new IOException("simulated broken image stream");
            }
        };

        engine.processImage(null, 0);

        String recorded =
                parentMetadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
        assertNotNull(recorded, "image write failure must be recorded on the parent metadata");
        assertTrue(recorded.contains("simulated broken image stream"), recorded);
    }

    private static class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
            return true;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext parseContext,
                                  boolean outputHtml) {
        }
    }
}
