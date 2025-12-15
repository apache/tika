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


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DefaultEmbeddedStreamTranslator;
import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * A parser decorator that computes digests of the parsed content.
 *
 * @deprecated Since 4.x. Use {@link AutoDetectParserConfig#setDigesterFactory(org.apache.tika.digest.DigesterFactory)}
 * to configure digesting. The AutoDetectParser now calls digesting directly in its parse method.
 * The interfaces {@link org.apache.tika.digest.Digester},
 * {@link org.apache.tika.digest.DigesterFactory}, and
 * {@link org.apache.tika.digest.Encoder} have moved to the
 * {@code org.apache.tika.digest} package.
 */
@Deprecated
public class DigestingParser extends ParserDecorator {

    private final EmbeddedStreamTranslator embeddedStreamTranslator = new DefaultEmbeddedStreamTranslator();
    private final org.apache.tika.digest.Digester digester;
    private final boolean skipContainerDocument;
    /**
     * Creates a decorator for the given parser.
     *
     * @param parser the parser instance to be decorated
     * @param digester the digester to use
     * @param skipContainerDocument if true, skip digesting top-level documents
     */
    public DigestingParser(Parser parser, org.apache.tika.digest.Digester digester,
                           boolean skipContainerDocument) {
        super(parser);
        this.digester = digester;
        this.skipContainerDocument = skipContainerDocument;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {


        if (! shouldDigest(metadata)) {
            super.parse(tis, handler, metadata, context);
            return;
        }
        TemporaryResources tmp = new TemporaryResources();
        try {

            if (embeddedStreamTranslator.shouldTranslate(tis, metadata)) {
                Path tmpBytes = tmp.createTempFile();
                try (OutputStream os = Files.newOutputStream(tmpBytes)) {
                    embeddedStreamTranslator.translate(tis, metadata, os);
                }
                try (TikaInputStream translated = TikaInputStream.get(tmpBytes)) {
                    digester.digest(translated, metadata, context);
                }
            } else {
                digester.digest(tis, metadata, context);
            }
            super.parse(tis, handler, metadata, context);
        } finally {
            tmp.dispose();
        }
    }

    private boolean shouldDigest(Metadata metadata) {
        if (digester == null) {
            return false;
        }
        if (! skipContainerDocument) {
            return true;
        }
        Integer parseDepth = metadata.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
        if (parseDepth == null || parseDepth == 0) {
            return false;
        }
        return true;
    }

}
