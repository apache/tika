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
package org.apache.tika.parser.enricher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mock.MockEnricher;

public class GatedEnricherTest {

    private static final byte[] IMAGE = {1, 2, 3, 4, 5};

    private static ImageCheck admitting(List<String> calls, String name) {
        return (image, metadata, context) -> {
            calls.add(name);
            return ImageCheck.Verdict.admit();
        };
    }

    private static ImageCheck refusing(List<String> calls, String name, String reason) {
        return (image, metadata, context) -> {
            calls.add(name);
            return ImageCheck.Verdict.skip(reason);
        };
    }

    private static Metadata run(GatedEnricher gate, Metadata metadata, ParseContext context)
            throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(IMAGE)) {
            gate.parse(tis, new DefaultHandler(), metadata, context);
        }
        return metadata;
    }

    @Test
    public void testAllAdmitRunsTheEngine() throws Exception {
        List<String> calls = new ArrayList<>();
        GatedEnricher gate = new GatedEnricher(new MockEnricher(),
                List.of(admitting(calls, "a"), admitting(calls, "b")));
        Metadata m = run(gate, new Metadata(), new ParseContext());
        assertEquals(List.of("a", "b"), calls);
        assertEquals("ENRICHED", m.get(MockEnricher.MARKER_KEY));
        assertNull(m.get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testFirstRefusalStopsTheChainAndTheEngine() throws Exception {
        List<String> calls = new ArrayList<>();
        GatedEnricher gate = new GatedEnricher(new MockEnricher(), List.of(
                admitting(calls, "a"), refusing(calls, "b", "blank"), admitting(calls, "c")));
        Metadata m = run(gate, new Metadata(), new ParseContext());
        assertEquals(List.of("a", "b"), calls);
        assertNull(m.get(MockEnricher.MARKER_KEY));
        assertEquals("skipped:blank", m.get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testACheckThatFailsAdmits() throws Exception {
        ImageCheck broken = (image, metadata, context) -> {
            throw new IOException("unreadable");
        };
        ImageCheck crashing = (image, metadata, context) -> {
            throw new IllegalStateException("bug");
        };
        GatedEnricher gate = new GatedEnricher(new MockEnricher(), List.of(broken, crashing));
        Metadata m = run(gate, new Metadata(), new ParseContext());
        assertEquals("ENRICHED", m.get(MockEnricher.MARKER_KEY));
    }

    @Test
    public void testChecksShareTheBytesAndTheEngineStillReadsThem() throws Exception {
        byte[][] seen = new byte[2][];
        ImageCheck first = (image, metadata, context) -> {
            seen[0] = image.bytes();
            return ImageCheck.Verdict.admit();
        };
        ImageCheck second = (image, metadata, context) -> {
            seen[1] = image.bytes();
            return ImageCheck.Verdict.admit();
        };
        byte[][] engineRead = new byte[1][];
        org.apache.tika.parser.Parser engine = new MockEnricher() {
            @Override
            public void parse(TikaInputStream tis, org.xml.sax.ContentHandler handler,
                              Metadata metadata, ParseContext context)
                    throws org.xml.sax.SAXException {
                try {
                    engineRead[0] = tis.readAllBytes();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        run(new GatedEnricher(engine, List.of(first, second)), new Metadata(), new ParseContext());
        assertArrayEquals(IMAGE, seen[0]);
        assertArrayEquals(IMAGE, seen[1]);
        assertArrayEquals(IMAGE, engineRead[0]);
    }

    @Test
    public void testMemoComputesOnce() throws Exception {
        int[] computed = {0};
        ImageCheck check = (image, metadata, context) -> {
            image.memo("k", () -> ++computed[0]);
            image.memo("k", () -> ++computed[0]);
            return ImageCheck.Verdict.admit();
        };
        run(new GatedEnricher(new MockEnricher(), List.of(check, check)), new Metadata(),
                new ParseContext());
        assertEquals(1, computed[0]);
    }

    @Test
    public void testInlineImageFlagsItsParent() throws Exception {
        Metadata parent = new Metadata();
        ParseContext context = new ParseContext();
        context.set(ParentMetadata.class, new ParentMetadata(parent));
        Metadata child = new Metadata();
        child.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.INLINE.name());
        run(new GatedEnricher(new MockEnricher(),
                List.of(refusing(new ArrayList<>(), "x", "blank"))), child, context);
        assertEquals("skipped:blank", child.get(TikaCoreProperties.OCR_WARNINGS));
        assertEquals("skipped:blank", parent.get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testAnImageThatIsNotInlineLeavesTheParentAlone() throws Exception {
        Metadata parent = new Metadata();
        ParseContext context = new ParseContext();
        context.set(ParentMetadata.class, new ParentMetadata(parent));
        run(new GatedEnricher(new MockEnricher(),
                List.of(refusing(new ArrayList<>(), "x", "blank"))), new Metadata(), context);
        assertNull(parent.get(TikaCoreProperties.OCR_WARNINGS));
    }
}
