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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.NoOpDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Pins the chosen default for a bare {@link ParseContext} (no {@link Parser} set) --
 * e.g. a concrete parser invoked directly, bypassing AutoDetectParser: embedded documents
 * are silently skipped, not parsed by an SPI-discovered AutoDetectParser.
 * <p>
 * That auto-construction (TIKA-2096) was the root cause of an incident where a parser
 * handed a fresh {@code ParseContext} to embedded extraction: it silently substituted an
 * SPI-loaded parser for the one the caller had actually configured, with none of that
 * caller's limits, selectors, or password providers. TIKA-2276 made a properly-configured
 * top-level parse (going through AutoDetectParser) immune to this by installing {@code this}
 * as {@code Parser.class} before any embedded extraction happens; this test pins the other
 * side -- that a context which never went through that path does not fall back to
 * conjuring a parser on its own.
 */
public class BareContextEmbeddedSkipTest {

    @Test
    public void bareContextGetsTheStatelessSingleton() {
        ParseContext context = new ParseContext();
        EmbeddedDocumentExtractor extractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        assertSame(ParsingEmbeddedDocumentExtractor.INSTANCE, extractor);
    }

    @Test
    public void bareContextSkipsEmbeddedContentWithoutInstallingAParser() throws Exception {
        ParseContext context = new ParseContext();
        EmbeddedDocumentExtractor extractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        try (TikaInputStream tis = TikaInputStream.get("this text must not appear in the output".getBytes(UTF_8))) {
            extractor.parseEmbedded(tis, handler, metadata, context, false);
        }

        assertEquals("", handler.toString().trim(),
                "a bare context has no Parser to delegate to, so embedded content must be "
                        + "skipped -- not parsed by a substitute");
        assertNull(context.get(Parser.class),
                "parsing embedded content on a bare context must not install a Parser as a "
                        + "side effect -- that mutation is exactly what let a fresh context "
                        + "masquerade as a configured one");
    }

    @Test
    public void bareContextGetsNoOpDetectorNotAnSpiDiscoveredOne() throws Exception {
        ParseContext context = new ParseContext();
        Detector detector = EmbeddedDocumentUtil.getDetector(context);
        assertSame(NoOpDetector.INSTANCE, detector);

        // real JPEG magic bytes -- a DefaultDetector would confidently report image/jpeg;
        // an unconfigured context must not get even a partially-informed guess.
        byte[] jpegMagic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        try (TikaInputStream tis = TikaInputStream.get(jpegMagic)) {
            assertEquals(MediaType.OCTET_STREAM, detector.detect(tis, new Metadata(), context));
        }
    }
}
