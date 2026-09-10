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
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.extractor.EmbeddedMetadataLookup;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class InferenceDispatcherTest {

    private static final MediaType PNG = MediaType.image("png");
    private static final MediaType CONTAINER = MediaType.application("x-test-container");

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

    /** A container whose one child is a PNG, parsed through the embedded extractor. */
    static final class ContainerParser implements Parser {
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
            child.set(HttpHeaders.CONTENT_TYPE, PNG.toString());
            child.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "INLINE");
            child.set(TikaCoreProperties.RESOURCE_NAME_KEY, "image1.png");
            try (TikaInputStream childStream = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
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

    private void offer(InferenceDispatcher dispatcher, MediaType type, String bytes,
                       ParseContext context) throws Exception {
        dispatcher.offer(InputKind.IMAGES, type, new Metadata(), null, file(bytes), context);
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

        offer(dispatcher, PNG, "a", context);
        offer(dispatcher, PNG, "b", context);
        offer(dispatcher, MediaType.image("jpeg"), "c", context);
        offer(dispatcher, MediaType.image("gif"), "d", context);
        assertTrue(pngTask.runs.isEmpty(), "nothing runs before the flush");
        Path held = context.get(InferenceDispatcher.State.class).byBinding.get("png").get(0).getPath();
        assertTrue(Files.exists(held), "the dispatcher owns a copy until the flush");

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

        dispatcher.flush(root, context);
        assertEquals(1, pngTask.runs.size(), "a second flush has nothing to run");
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
        assertEquals("/1/2", unit.getTargetIdPath());
    }

    @Test
    public void testUnknownSelectionFailsTheTopLevelParse() throws Exception {
        CompositeParser composite = new CompositeParser(
                org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
                new TypedParser(PNG));
        composite.setInferenceDispatcher(new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(new RecordingTask())))));
        ParseContext context = new ParseContext();
        InferenceSelection selection = new InferenceSelection();
        selection.setBindings(List.of("typo"));
        context.set(InferenceSelection.class, selection);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
        try (TikaInputStream tis = TikaInputStream.get("not an image".getBytes(UTF_8))) {
            assertThrows(TikaException.class,
                    () -> composite.parse(tis, new DefaultHandler(), metadata, context),
                    "fails at the top of the parse, before any document is offered");
        }
    }

    @Test
    public void testNestedChildOutsideTheWrapperGetsItsParent() throws Exception {
        RecordingTask task = new RecordingTask();
        CompositeParser composite = new CompositeParser(
                org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
                new ContainerParser(), new TypedParser(PNG));
        composite.setInferenceDispatcher(new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task)))));
        Metadata root = new Metadata();
        root.set(HttpHeaders.CONTENT_TYPE, CONTAINER.toString());
        ParseContext context = new ParseContext();
        context.set(Parser.class, composite);
        try (TikaInputStream tis = TikaInputStream.get("CONTAINER".getBytes(UTF_8))) {
            composite.parse(tis, new DefaultHandler(), root, context);
        }
        assertEquals(1, task.runs.size());
        assertSame(root, task.runs.get(0).get(0).getParent(),
                "the composite names the parent in every mode, not only under the wrapper");
        assertNull(context.get(InferenceDispatcher.class), "seeding is undone at the top level");
    }

    @Test
    public void testCompositeOffersAndFlushesAtTopLevel() throws Exception {
        RecordingTask task = new RecordingTask();
        CompositeParser composite = new CompositeParser(
                org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
                new TypedParser(PNG));
        composite.setInferenceDispatcher(new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task)))));
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get("PNG-BYTES".getBytes(UTF_8))) {
            composite.parse(tis, new DefaultHandler(), metadata, context);
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
        CompositeParser composite = new CompositeParser(
                org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
                new ContainerParser(), new TypedParser(PNG));
        composite.setInferenceDispatcher(new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(binding("png", InputKind.IMAGES, null, -1),
                        new RecordingEngine(), List.of(task)))));
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(composite);
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
        assertEquals(2, handler.getMetadataList().size());
    }
}
