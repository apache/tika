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
package org.apache.tika.parser.inference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.extractor.EmbeddedMetadataLookup;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.hook.ParseHooks;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class InferenceDispatcherTest {

    private static final MediaType PNG = MediaType.image("png");
    private static final MediaType CONTAINER = MediaType.application("x-test-container");
    private static final MediaType INNER = MediaType.image("x-test-inner");

    static final class RecordingEngine implements Engine {
    }

    /** Records units and their bytes; bytes are only readable during the run. */
    static final class RecordingTask implements InferenceTask {
        final List<List<InferenceUnit>> runs = new ArrayList<>();
        final List<List<byte[]>> bytes = new ArrayList<>();

        @Override
        public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                        ParseContext context) throws IOException {
            runs.add(List.copyOf(units));
            List<byte[]> read = new ArrayList<>();
            for (InferenceUnit unit : units) {
                read.add(unit.getBytes());
            }
            bytes.add(read);
        }
    }

    /** A stub parser for a type: emits an empty document. */
    static final class TypedParser implements Parser {
        private final Set<MediaType> types;

        TypedParser(MediaType type) {
            this.types = Collections.singleton(type);
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return types;
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            xhtml.endDocument();
        }
    }

    /**
     * A container parsed through the embedded extractor: "CONTAINER" holds one inline PNG;
     * "NESTED" holds a "CONTAINER" of the {@link #INNER} type as an attachment, like a zip
     * holding a docx with a picture. INNER is an image type so the attachment is offered too.
     */
    static final class ContainerParser implements Parser {
        private final MediaType type;

        ContainerParser() {
            this(CONTAINER);
        }

        ContainerParser(MediaType type) {
            this.type = type;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(type);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            boolean nested = "NESTED".equals(new String(tis.readAllBytes(), UTF_8));
            Metadata child = new Metadata();
            child.set(HttpHeaders.CONTENT_TYPE, nested ? INNER.toString() : PNG.toString());
            child.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, nested ? "ATTACHMENT" : "INLINE");
            child.set(TikaCoreProperties.RESOURCE_NAME_KEY, nested ? "inner.docx" : "image1.png");
            byte[] bytes = (nested ? "CONTAINER" : "PNG-BYTES").getBytes(UTF_8);
            try (TikaInputStream childStream = TikaInputStream.get(bytes)) {
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                        .parseEmbedded(childStream, xhtml, child, context, false);
            }
            xhtml.endDocument();
        }
    }

    private static InferenceBinding binding(String id, InputKind kind, Set<MediaType> include,
                                            int maxChunks) {
        return new InferenceBinding(id, "engine", kind, List.of("embed"), include, null,
                maxChunks, -1, true);
    }

    @TempDir
    Path tmp;

    private Path file(String bytes) throws IOException {
        Path p = Files.createTempFile(tmp, "unit", ".bin");
        Files.write(p, bytes.getBytes(UTF_8));
        return p;
    }

    /** Offers as an embedded document: a parent, so the dispatcher copies the bytes. */
    private Path offer(InferenceDispatcher dispatcher, MediaType type, String bytes,
                       ParseContext context) throws Exception {
        Path source = file(bytes);
        dispatcher.offer(InputKind.IMAGES, type, new Metadata(), new Metadata(), source, context);
        return source;
    }

    @Test
    public void testBufferPerBindingAndBudget() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        RecordingTask pngTask = new RecordingTask();
        RecordingTask jpegTask = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, Set.of(PNG), 1),
                        engine, List.of(pngTask)),
                new InferenceDispatcher.Bound(
                        binding("jpeg", InputKind.IMAGES, Set.of(MediaType.image("jpeg")), -1),
                        engine, List.of(jpegTask))));
        ParseContext context = new ParseContext();
        assertTrue(dispatcher.wants(InputKind.IMAGES, PNG, context));
        assertTrue(!dispatcher.wants(InputKind.PAGES, PNG, context));
        assertTrue(!dispatcher.wants(InputKind.IMAGES, MediaType.image("gif"), context));

        Path source = offer(dispatcher, PNG, "a", context);
        offer(dispatcher, PNG, "b", context);
        offer(dispatcher, MediaType.image("jpeg"), "c", context);
        offer(dispatcher, MediaType.image("gif"), "d", context);
        assertTrue(pngTask.runs.isEmpty(), "nothing runs before the flush");
        Path held = context.get(InferenceDispatcher.State.class).byBinding.get("png").get(0).getPath();
        assertTrue(Files.exists(held), "the dispatcher owns a copy until the flush");
        assertNotEquals(source, held, "an embedded document's bytes are copied");

        Metadata root = new Metadata();
        dispatcher.flush(root, context);
        assertEquals(1, pngTask.runs.size());
        assertEquals(1, pngTask.runs.get(0).size(), "maxChunks 1 keeps the first unit");
        assertArrayEquals("a".getBytes(UTF_8), pngTask.bytes.get(0).get(0));
        assertEquals(1, jpegTask.runs.get(0).size());
        assertTrue(root.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("skipped 1 units"));
        assertNull(context.get(InferenceDispatcher.State.class), "the buffer is cleared");
        assertFalse(Files.exists(held), "and its files are gone");
        assertTrue(Files.exists(source), "the source was never the dispatcher's to delete");

        dispatcher.flush(root, context);
        assertEquals(1, pngTask.runs.size(), "a second flush has nothing to run");
    }

    /** A spacer is not a picture: an image recorded under the binding's minimum is not offered. */
    @Test
    public void testTinyImagesAreNotOffered() throws Exception {
        RecordingTask task = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, Set.of(PNG), -1),
                        new RecordingEngine(), List.of(task))));
        ParseContext context = new ParseContext();
        Metadata spacer = new Metadata();
        spacer.set(TIFF.IMAGE_WIDTH, 600);
        spacer.set(TIFF.IMAGE_LENGTH, 1);
        dispatcher.offer(InputKind.IMAGES, PNG, spacer, new Metadata(), file("a"), context);
        Metadata picture = new Metadata();
        picture.set(TIFF.IMAGE_WIDTH, 2);
        picture.set(TIFF.IMAGE_LENGTH, 2);
        dispatcher.offer(InputKind.IMAGES, PNG, picture, new Metadata(), file("b"), context);
        dispatcher.offer(InputKind.IMAGES, PNG, new Metadata(), new Metadata(), file("c"), context);
        dispatcher.flush(new Metadata(), context);
        assertEquals(2, task.runs.get(0).size(), "the 2 x 2 picture and the one of unknown size");

        InferenceBinding tall = new InferenceBinding("tall", "engine", InputKind.IMAGES,
                List.of("embed"), Set.of(PNG), null, -1, -1, true, null, 0, 100);
        assertFalse(tall.accepts(InputKind.IMAGES, PNG, picture), "shorter than 100");
        assertTrue(tall.accepts(InputKind.IMAGES, PNG, new Metadata()), "unknown size passes");
    }

    /** The top-level document's own file outlives the flush, so it is referenced, not copied. */
    @Test
    public void testTopLevelBytesAreReferencedNotCopied() throws Exception {
        RecordingTask task = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task)),
                new InferenceDispatcher.Bound(binding("pages", InputKind.PAGES, null, -1),
                        new RecordingEngine(), List.of(task))));
        ParseContext context = new ParseContext();
        Path source = file("top");
        dispatcher.offer(InputKind.IMAGES, PNG, new Metadata(), null, source, context);
        InferenceUnit unit = context.get(InferenceDispatcher.State.class).byBinding.get("png").get(0);
        assertEquals(source, unit.getPath());

        Path render = file("page");
        dispatcher.offerPage(PNG, new Metadata(), null, 1, render, context);
        InferenceUnit page = context.get(InferenceDispatcher.State.class).byBinding.get("pages").get(0);
        assertNotEquals(render, page.getPath(), "a page render is the renderer's file: copied");

        dispatcher.flush(new Metadata(), context);
        assertArrayEquals("top".getBytes(UTF_8), task.bytes.get(0).get(0));
        assertTrue(Files.exists(source), "referenced, so not deleted with the buffer");
        assertFalse(Files.exists(page.getPath()));
    }

    @Test
    public void testSelectionAndMaxBytes() throws Exception {
        RecordingTask pngTask = new RecordingTask();
        RecordingTask smallTask = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(pngTask)),
                new InferenceDispatcher.Bound(new InferenceBinding("small", "engine",
                        InputKind.IMAGES, List.of("embed"), null, null, -1, 2, true),
                        new RecordingEngine(), List.of(smallTask))));

        ParseContext off = new ParseContext();
        InferenceSelection selection = new InferenceSelection();
        selection.setEnabled(false);
        off.set(InferenceSelection.class, selection);
        assertFalse(dispatcher.wants(InputKind.IMAGES, PNG, off));

        ParseContext some = new ParseContext();
        selection = new InferenceSelection();
        selection.setBindings(List.of("small"));
        some.set(InferenceSelection.class, selection);
        offer(dispatcher, PNG, "abc", some);
        offer(dispatcher, PNG, "ab", some);
        dispatcher.flush(new Metadata(), some);
        assertTrue(pngTask.runs.isEmpty(), "not selected for this request");
        assertEquals(1, smallTask.runs.get(0).size(), "maxBytes 2 drops the 3-byte unit");

        ParseContext unknown = new ParseContext();
        selection = new InferenceSelection();
        selection.setBindings(List.of("nope"));
        unknown.set(InferenceSelection.class, selection);
        assertThrows(TikaException.class, () -> dispatcher.wants(InputKind.IMAGES, PNG, unknown));
    }

    /** A handler that kept a copy of one embedded document, the way the wrapper does. */
    static final class KeepingHandler extends AbstractRecursiveParserWrapperHandler {
        final Metadata kept = new Metadata();

        KeepingHandler(String idPath) {
            super(new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
            kept.set(TikaCoreProperties.EMBEDDED_ID_PATH, idPath);
        }

        @Override
        public Metadata getEmbeddedMetadata(String idPath) {
            return idPath.equals(kept.get(TikaCoreProperties.EMBEDDED_ID_PATH)) ? kept : null;
        }
    }

    @Test
    public void testFlushWritesToTheKeptMetadata() throws Exception {
        RecordingTask task = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task))));
        KeepingHandler handler = new KeepingHandler("/1");
        ParseContext context = new ParseContext();
        context.set(EmbeddedMetadataLookup.class, new EmbeddedMetadataLookup(handler));

        Metadata liveParent = new Metadata();
        liveParent.set(TikaCoreProperties.EMBEDDED_ID_PATH, "/1");
        Metadata liveChild = new Metadata();
        liveChild.set(TikaCoreProperties.EMBEDDED_ID_PATH, "/1/2");
        dispatcher.offer(InputKind.IMAGES, PNG, liveChild, liveParent, file("x"), context);
        dispatcher.flush(new Metadata(), context);

        InferenceUnit unit = task.runs.get(0).get(0);
        assertSame(handler.kept, unit.getParent(), "the parent the wrapper kept, not the live one");
        assertSame(liveChild, unit.getTarget(), "nothing kept for the child: the live object");
        assertSame(liveChild, unit.getDestination(), "untyped: not lifted");
        assertEquals("/1/2", unit.getTargetIdPath());
    }

    /** Detection by the declared type: these tests are about the hook, not the detector. */
    private static final Detector DECLARED = (tis, metadata, ctx) ->
            MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));

    private static AutoDetectParser hooked(InferenceDispatcher dispatcher, Parser... parsers) {
        AutoDetectParser adp = new AutoDetectParser(DECLARED, parsers);
        adp.setParseHooks(new ParseHooks(List.of(dispatcher)));
        return adp;
    }

    private static InferenceDispatcher pngDispatcher(RecordingTask task) {
        return new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task))));
    }

    @Test
    public void testUnknownSelectionFailsTheTopLevelParse() throws Exception {
        AutoDetectParser parser = hooked(pngDispatcher(new RecordingTask()), new TypedParser(PNG));
        ParseContext context = new ParseContext();
        InferenceSelection selection = new InferenceSelection();
        selection.setBindings(List.of("typo"));
        context.set(InferenceSelection.class, selection);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
        try (TikaInputStream tis = TikaInputStream.get("not an image".getBytes(UTF_8))) {
            assertThrows(TikaException.class,
                    () -> parser.parse(tis, new DefaultHandler(), metadata, context),
                    "fails at the top of the parse, before any document is offered");
        }
    }

    @Test
    public void testNestedChildOutsideTheWrapperGetsItsParent() throws Exception {
        RecordingTask task = new RecordingTask();
        AutoDetectParser parser = hooked(pngDispatcher(task), new ContainerParser(),
                new TypedParser(PNG));
        Metadata root = new Metadata();
        root.set(HttpHeaders.CONTENT_TYPE, CONTAINER.toString());
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
            parser.parse(tis, new DefaultHandler(), root, context);
        }
        assertEquals(1, task.runs.size());
        InferenceUnit unit = task.runs.get(0).get(0);
        assertSame(root, unit.getParent(),
                "the parent is named in every mode, not only under the wrapper");
        assertSame(root, unit.getDestination(), "one output object: results land on it");
        assertEquals("/1", unit.getTargetIdPath(), "numbered as the wrapper would have");
        assertNull(unit.getParentIdPath());
        assertNull(context.get(ParseHooks.class), "seeding is undone at the top level");
    }

    /** Outside the wrapper the embedded documents' metadata is discarded: every unit lands on the root. */
    @Test
    public void testDeepChildOutsideTheWrapperLandsOnTheRoot() throws Exception {
        RecordingTask task = new RecordingTask();
        AutoDetectParser parser = hooked(pngDispatcher(task), new ContainerParser(),
                new ContainerParser(INNER), new TypedParser(PNG));
        Metadata root = new Metadata();
        root.set(HttpHeaders.CONTENT_TYPE, CONTAINER.toString());
        try (TikaInputStream tis = TikaInputStream.get("NESTED".getBytes(UTF_8))) {
            parser.parse(tis, new DefaultHandler(), root, new ParseContext());
        }
        assertEquals(2, task.runs.get(0).size(), "the picture and the attachment");
        InferenceUnit unit = task.runs.get(0).get(0);
        assertEquals("image1.png", unit.getTarget().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("inner.docx", unit.getParent().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertSame(root, unit.getDestination());
        assertTrue(unit.isLifted());
        assertEquals("/1/2", unit.getTargetIdPath());
        assertEquals("/1", unit.getParentIdPath());
        InferenceUnit attachment = task.runs.get(0).get(1);
        assertSame(root, attachment.getDestination(), "an attachment too: there is nowhere else");
        assertTrue(attachment.isLifted());
        assertEquals("/1", attachment.getTargetIdPath());
    }

    /** Under the wrapper an attachment keeps its own results, on the copy the wrapper kept. */
    @Test
    public void testAttachmentUnderTheWrapperKeepsItsOwn() throws Exception {
        RecordingTask task = new RecordingTask();
        AutoDetectParser parser = hooked(pngDispatcher(task), new ContainerParser(),
                new ContainerParser(INNER), new TypedParser(PNG));
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        Metadata root = new Metadata();
        root.set(HttpHeaders.CONTENT_TYPE, CONTAINER.toString());
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        try (TikaInputStream tis = TikaInputStream.get("NESTED".getBytes(UTF_8))) {
            wrapper.parse(tis, handler, root, new ParseContext());
        }
        assertEquals(3, handler.getMetadataList().size());
        Metadata keptDocx = handler.getEmbeddedMetadata("/1");
        Map<String, InferenceUnit> byName = new HashMap<>();
        for (InferenceUnit unit : task.runs.get(0)) {
            byName.put(unit.getTarget().get(TikaCoreProperties.RESOURCE_NAME_KEY), unit);
        }
        InferenceUnit picture = byName.get("image1.png");
        InferenceUnit attachment = byName.get("inner.docx");
        assertSame(keptDocx, attachment.getDestination(), "the attachment's kept copy");
        assertFalse(attachment.isLifted());
        assertSame(keptDocx, picture.getDestination(), "lifted onto the docx it is part of");
        assertTrue(picture.isLifted());
        assertEquals("/1/2", picture.getTargetIdPath());
    }

    @Test
    public void testOffersAndFlushesAtTopLevel() throws Exception {
        RecordingTask task = new RecordingTask();
        AutoDetectParser parser = hooked(pngDispatcher(task), new TypedParser(PNG));
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            parser.parse(tis, new DefaultHandler(), metadata, context);
        }
        assertEquals(1, task.runs.size());
        InferenceUnit unit = task.runs.get(0).get(0);
        assertArrayEquals("PNG-BYTES".getBytes(UTF_8), task.bytes.get(0).get(0));
        assertSame(metadata, unit.getTarget());
        assertNull(unit.getParent());
    }

    @Test
    public void testNestedChildCarriesItsParent() throws Exception {
        RecordingTask task = new RecordingTask();
        AutoDetectParser parser = hooked(pngDispatcher(task), new ContainerParser(),
                new TypedParser(PNG));
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        Metadata root = new Metadata();
        root.set(HttpHeaders.CONTENT_TYPE, CONTAINER.toString());
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
            wrapper.parse(tis, handler, root, new ParseContext());
        }
        assertEquals(1, task.runs.size(), "flushed once, at the end of the top-level parse");
        InferenceUnit unit = task.runs.get(0).get(0);
        assertArrayEquals("PNG-BYTES".getBytes(UTF_8), task.bytes.get(0).get(0));
        assertEquals("image1.png", unit.getTarget().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertSame(root, unit.getParent());
        assertSame(root, unit.getDestination(), "inline: lifted onto its parent");
        assertEquals(2, handler.getMetadataList().size());
    }

    @Test
    public void testPagesComeFromTheRendererOnly() throws Exception {
        RecordingTask pageTask = new RecordingTask();
        RecordingTask imageTask = new RecordingTask();
        RecordingEngine engine = new RecordingEngine();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("pages", InputKind.PAGES, null, 1),
                        engine, List.of(pageTask)),
                new InferenceDispatcher.Bound(binding("images", InputKind.IMAGES, null, -1),
                        engine, List.of(imageTask))));
        ParseContext context = new ParseContext();
        Metadata pdf = new Metadata();
        assertTrue(dispatcher.wantsPages(PNG, pdf, context));
        Metadata rendering = new Metadata();
        rendering.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "RENDERING");
        assertFalse(dispatcher.wants(PNG, rendering, context),
                "a render emitted as an embedded document is a page, not an image");
        Metadata inline = new Metadata();
        inline.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "INLINE");
        assertTrue(dispatcher.wants(PNG, inline, context));

        dispatcher.offerPage(PNG, pdf, null, 1, file("p1"), context);
        dispatcher.offerPage(PNG, pdf, null, 2, file("p2"), context);
        dispatcher.offer(PNG, rendering, pdf, file("r"), context);
        Metadata root = new Metadata();
        dispatcher.flush(root, context);

        assertEquals(1, pageTask.runs.size());
        List<InferenceUnit> units = pageTask.runs.get(0);
        assertEquals(1, units.size(), "maxChunks 1 is one page");
        assertEquals(InputKind.PAGES, units.get(0).getKind());
        assertEquals(1, units.get(0).getPage());
        assertSame(pdf, units.get(0).getTarget());
        assertEquals("p1", new String(pageTask.bytes.get(0).get(0), UTF_8));
        assertTrue(imageTask.runs.isEmpty(), "the rendering child reached no IMAGES binding");
        assertTrue(root.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("pages over maxChunks: skipped 1 units"));
    }

    @Test
    public void testNoPagesBindingWantsNoPages() throws Exception {
        InferenceDispatcher dispatcher = pngDispatcher(new RecordingTask());
        assertFalse(dispatcher.wantsPages(PNG, new Metadata(), new ParseContext()));
    }

    @Test
    public void testFailedParseRunsNothingAndCleansUp() throws Exception {
        RecordingTask task = new RecordingTask();
        InferenceDispatcher dispatcher = pngDispatcher(task);
        ParseContext context = new ParseContext();
        offer(dispatcher, PNG, "a", context);
        Path held = context.get(InferenceDispatcher.State.class).byBinding.get("png").get(0).getPath();
        dispatcher.end(new Metadata(), true, context);
        assertTrue(task.runs.isEmpty(), "a failed document costs no engine call");
        assertFalse(Files.exists(held));
        assertNull(context.get(InferenceDispatcher.State.class));
    }
}
