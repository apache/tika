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
package org.apache.tika.parser.hook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class ParseHooksTest {

    private static final MediaType PNG = MediaType.image("png");
    private static final MediaType CONTAINER = MediaType.application("x-test-container");
    private static final MediaType PAGED = MediaType.application("x-test-paged");

    /** Detection by the declared type: these tests are about the seam, not the detector. */
    private static final Detector DECLARED = (tis, metadata, ctx) ->
            MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));

    static final class RecordingHook implements ParseHook {
        final List<String> events = new ArrayList<>();
        final List<Metadata> offeredParents = new ArrayList<>();
        final List<byte[]> offeredBytes = new ArrayList<>();
        final List<Metadata> pageDocuments = new ArrayList<>();
        final List<Metadata> pageParents = new ArrayList<>();
        final List<String> pageBytes = new ArrayList<>();
        Set<MediaType> wanted = Set.of(PNG);
        boolean wantsPages;
        boolean failOffer;
        boolean failStart;

        @Override
        public void start(Metadata root, ParseContext context) throws TikaException {
            if (failStart) {
                throw new TikaException("bad selection");
            }
            events.add("start");
        }

        @Override
        public boolean wants(MediaType type, Metadata metadata, ParseContext context) {
            return wanted.contains(type);
        }

        @Override
        public void offer(MediaType type, Metadata metadata, Metadata parent, Path bytes,
                          ParseContext context) throws IOException {
            if (failOffer) {
                throw new IOException("no");
            }
            events.add("offer " + type);
            offeredParents.add(parent);
            offeredBytes.add(Files.readAllBytes(bytes));
        }

        @Override
        public boolean wantsPages(MediaType renderType, Metadata document, ParseContext context) {
            events.add("wantsPages " + renderType);
            return wantsPages;
        }

        @Override
        public void offerPage(MediaType type, Metadata document, Metadata parent, int page,
                              Path bytes, ParseContext context) throws IOException {
            if (failOffer) {
                throw new IOException("no page");
            }
            events.add("page " + page);
            pageDocuments.add(document);
            pageParents.add(parent);
            pageBytes.add(Files.readString(bytes));
        }

        @Override
        public void end(Metadata root, boolean failed, ParseContext context) {
            events.add(failed ? "end failed" : "end");
        }
    }

    /** A parser that renders two pages for the hooks, the way the PDF parser does. */
    static final class PagedParser implements Parser {
        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(PAGED);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            ParseHooks hooks = context.get(ParseHooks.class);
            if (hooks != null && hooks.wantsPages(PNG, context)) {
                for (int page = 1; page <= 2; page++) {
                    Path render = Files.createTempFile("page", ".png");
                    try {
                        Files.writeString(render, "PAGE-" + page);
                        hooks.offerPage(PNG, page, render, context);
                    } finally {
                        Files.delete(render);
                    }
                }
            }
            xhtml.endDocument();
        }
    }

    static final class TypedParser implements Parser {
        private final MediaType type;
        private final boolean fail;

        TypedParser(MediaType type, boolean fail) {
            this.type = type;
            this.fail = fail;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(type);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            if (fail) {
                throw new TikaException("broken");
            }
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            xhtml.endDocument();
        }
    }

    /** A container whose one child is a PNG, parsed through the embedded extractor. */
    static final class ContainerParser implements Parser {
        private final MediaType childType;

        ContainerParser() {
            this(PNG);
        }

        ContainerParser(MediaType childType) {
            this.childType = childType;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(CONTAINER);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            Metadata child = new Metadata();
            child.set(HttpHeaders.CONTENT_TYPE, childType.toString());
            child.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "INLINE");
            try (TikaInputStream childStream = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                        .parseEmbedded(childStream, xhtml, child, context, false);
            }
            xhtml.endDocument();
        }
    }

    private static AutoDetectParser parser(RecordingHook hook, Parser... parsers) {
        AutoDetectParser adp = new AutoDetectParser(DECLARED, parsers);
        adp.setParseHooks(new ParseHooks(List.of(hook)));
        return adp;
    }

    private static Metadata typed(MediaType type) {
        Metadata m = new Metadata();
        m.set(HttpHeaders.CONTENT_TYPE, type.toString());
        return m;
    }

    @Test
    public void testTopLevelDocumentIsOfferedBetweenStartAndEnd() throws Exception {
        RecordingHook hook = new RecordingHook();
        Metadata metadata = typed(PNG);
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            parser(hook, new TypedParser(PNG, false)).parse(tis, new DefaultHandler(), metadata, context);
        }
        assertEquals(List.of("start", "offer image/png", "end"), hook.events);
        assertArrayEquals("PNG-BYTES".getBytes(UTF_8), hook.offeredBytes.get(0));
        assertNull(hook.offeredParents.get(0), "top level has no parent");
        assertNull(context.get(ParseHooks.class), "hooks are unseeded after the parse");
        assertNull(context.get(ParseHooks.Run.class));
    }

    @Test
    public void testEmbeddedDocumentIsOfferedOnceWithItsParent() throws Exception {
        for (boolean recursive : new boolean[]{false, true}) {
            RecordingHook hook = new RecordingHook();
            AutoDetectParser adp = parser(hook, new ContainerParser(), new TypedParser(PNG, false));
            Metadata root = typed(CONTAINER);
            ParseContext context = new ParseContext();
            try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
                if (recursive) {
                    new RecursiveParserWrapper(adp).parse(tis, new RecursiveParserWrapperHandler(
                            new BasicContentHandlerFactory(
                                    BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1)),
                            root, context);
                } else {
                    adp.parse(tis, new DefaultHandler(), root, context);
                }
            }
            assertEquals(List.of("start", "offer image/png", "end"), hook.events,
                    "recursive=" + recursive + ": one offer for the child, none for the container");
            assertSame(root, hook.offeredParents.get(0),
                    "recursive=" + recursive + ": the child's parent is the root");
        }
    }

    @Test
    public void testUnwantedTypeIsNotPinned() throws Exception {
        RecordingHook hook = new RecordingHook();
        hook.wanted = Set.of();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            parser(hook, new TypedParser(PNG, false)).parse(tis, new DefaultHandler(), typed(PNG),
                    new ParseContext());
        }
        assertEquals(List.of("start", "end"), hook.events);
    }

    @Test
    public void testFailedParseEndsAsFailedAndOffersNothing() throws Exception {
        RecordingHook hook = new RecordingHook();
        Metadata metadata = typed(PNG);
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            assertThrows(TikaException.class, () -> parser(hook, new TypedParser(PNG, true))
                    .parse(tis, new DefaultHandler(), metadata, new ParseContext()));
        }
        assertEquals(List.of("start", "end failed"), hook.events);
    }

    @Test
    public void testOfferFailureIsAWarningNotAFailure() throws Exception {
        RecordingHook hook = new RecordingHook();
        hook.failOffer = true;
        Metadata metadata = typed(PNG);
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            parser(hook, new TypedParser(PNG, false)).parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }
        assertEquals(List.of("start", "end"), hook.events);
        assertTrue(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).contains("no"));
    }

    @Test
    public void testStartFailureFailsTheParseAndRestoresTheContext() throws Exception {
        RecordingHook hook = new RecordingHook();
        hook.failStart = true;
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            assertThrows(TikaException.class, () -> parser(hook, new TypedParser(PNG, false))
                    .parse(tis, new DefaultHandler(), typed(PNG), context));
        }
        assertEquals(List.of("end failed"), hook.events);
        assertNull(context.get(ParseHooks.class));
        assertNull(context.get(ParseHooks.Run.class));
    }

    @Test
    public void testPagesAreOfferedWithTheirDocumentAndItsParent() throws Exception {
        RecordingHook hook = new RecordingHook();
        hook.wantsPages = true;
        hook.wanted = Set.of();
        Metadata root = typed(CONTAINER);
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
            parser(hook, new ContainerParser(PAGED), new PagedParser())
                    .parse(tis, new DefaultHandler(), root, context);
        }
        assertEquals(List.of("start", "wantsPages image/png", "page 1", "page 2", "end"),
                hook.events);
        assertEquals(List.of("PAGE-1", "PAGE-2"), hook.pageBytes);
        assertEquals(PAGED.toString(), hook.pageDocuments.get(0).get(HttpHeaders.CONTENT_TYPE),
                "the page belongs to the paged child");
        assertSame(hook.pageDocuments.get(0), hook.pageDocuments.get(1));
        assertSame(root, hook.pageParents.get(0), "and the child's parent rides along");
    }

    @Test
    public void testNoPagesWhenNoHookWantsThem() throws Exception {
        RecordingHook hook = new RecordingHook();
        try (TikaInputStream tis = TikaInputStream.get("PAGED".getBytes(UTF_8))) {
            parser(hook, new PagedParser()).parse(tis, new DefaultHandler(), typed(PAGED),
                    new ParseContext());
        }
        assertEquals(List.of("start", "wantsPages image/png", "end"), hook.events);
    }

    @Test
    public void testPageOfferFailureIsAWarningOnTheDocument() throws Exception {
        RecordingHook hook = new RecordingHook();
        hook.wantsPages = true;
        hook.failOffer = true;
        Metadata metadata = typed(PAGED);
        try (TikaInputStream tis = TikaInputStream.get("PAGED".getBytes(UTF_8))) {
            parser(hook, new PagedParser()).parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }
        assertEquals(List.of("start", "wantsPages image/png", "end"), hook.events);
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(2, warnings.length);
        assertTrue(warnings[0].contains("on page 1: no page"), warnings[0]);
    }

    @Test
    public void testNoHooksNoTrace() throws Exception {
        AutoDetectParser adp = new AutoDetectParser(DECLARED, new TypedParser(PNG, false));
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            adp.parse(tis, new DefaultHandler(), typed(PNG), context);
        }
        assertNull(context.get(ParseHooks.class));
    }

    /** The chunk lift reads ParentMetadata during a child's parse: the seam must not shadow it. */
    @Test
    public void testParentMetadataIsLeftToTheWrapper() throws Exception {
        RecordingHook hook = new RecordingHook();
        List<Metadata> seenByImageParser = new ArrayList<>();
        Parser png = new Parser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws IOException, SAXException {
                ParentMetadata parent = context.get(ParentMetadata.class);
                seenByImageParser.add(parent == null ? null : parent.getMetadata());
                new XHTMLContentHandler(handler, metadata, context).startDocument();
            }
        };
        AutoDetectParser adp = parser(hook, new ContainerParser(), png);
        Metadata root = typed(CONTAINER);
        try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
            new RecursiveParserWrapper(adp).parse(tis, new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(
                            BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1)), root,
                    new ParseContext());
        }
        assertEquals(1, seenByImageParser.size());
        assertSame(root, seenByImageParser.get(0), "the picture's parent, not the picture");
    }
}
