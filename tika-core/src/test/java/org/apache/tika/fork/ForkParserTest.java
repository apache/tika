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
package org.apache.tika.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.mock.MockParser;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class ForkParserTest extends TikaTest {

    @Test
    public void testHelloWorld() throws Exception {
        try (ForkParser parser = new ForkParser(ForkParserTest.class.getClassLoader(),
                new ForkTestParser())) {
            Metadata metadata = new Metadata();
            ContentHandler output = new BodyContentHandler();
            InputStream stream = new ByteArrayInputStream(new byte[0]);
            ParseContext context = new ParseContext();
            parser.parse(stream, output, metadata, context);
            assertEquals("Hello, World!", output.toString().trim());
            assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        }
    }

    @Test
    public void testSerialParsing() throws Exception {
        try (ForkParser parser = new ForkParser(ForkParserTest.class.getClassLoader(),
                new ForkTestParser())) {
            ParseContext context = new ParseContext();
            for (int i = 0; i < 10; i++) {
                ContentHandler output = new BodyContentHandler();
                InputStream stream = new ByteArrayInputStream(new byte[0]);
                parser.parse(stream, output, new Metadata(), context);
                assertEquals("Hello, World!", output.toString().trim());
            }
        }
    }

    @Test
    public void testParallelParsing() throws Exception {
        try (ForkParser parser = new ForkParser(ForkParserTest.class.getClassLoader(),
                new ForkTestParser())) {
            final ParseContext context = new ParseContext();

            Thread[] threads = new Thread[10];
            ContentHandler[] output = new ContentHandler[threads.length];
            for (int i = 0; i < threads.length; i++) {
                final ContentHandler o = new BodyContentHandler();
                output[i] = o;
                threads[i] = new Thread(() -> {
                    try {
                        InputStream stream = new ByteArrayInputStream(new byte[0]);
                        parser.parse(stream, o, new Metadata(), context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                threads[i].start();
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
                assertEquals("Hello, World!", output[i].toString().trim());
            }
        }
    }

    @Test
    public void testPoolSizeReached() throws Exception {
        try (ForkParser parser = new ForkParser(ForkParserTest.class.getClassLoader(),
                new ForkTestParser())) {
            final Semaphore barrier = new Semaphore(0);

            Thread[] threads = new Thread[parser.getPoolSize()];
            PipedOutputStream[] pipes = new PipedOutputStream[threads.length];
            final ParseContext context = new ParseContext();
            for (int i = 0; i < threads.length; i++) {
                final PipedInputStream input = new PipedInputStream() {
                    @Override
                    public synchronized int read() throws IOException {
                        barrier.release();
                        return super.read();
                    }
                };
                pipes[i] = new PipedOutputStream(input);
                threads[i] = new Thread(() -> {
                    try {
                        ContentHandler o = new DefaultHandler();
                        parser.parse(input, o, new Metadata(), context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                threads[i].start();
            }

            // Wait until all the background parsers have been started
            barrier.acquire(parser.getPoolSize());

            final ContentHandler o = new BodyContentHandler();
            Thread blocked = new Thread(() -> {
                try {
                    barrier.release();
                    InputStream stream = new ByteArrayInputStream(new byte[0]);
                    parser.parse(stream, o, new Metadata(), context);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            blocked.start();

            // Wait until the last thread is started, and then some to
            // make sure that it would have had a chance to start processing
            // data had it not been blocked.
            barrier.acquire();
            Thread.sleep(1000);

            assertEquals("", o.toString());

            for (int i = 0; i < threads.length; i++) {
                pipes[i].close();
                threads[i].join();
            }

            blocked.join();
            assertEquals("Hello, World!", o.toString().trim());
        }
    }

    @Test
    public void testPulseAndTimeouts() throws Exception {

        ForkParser forkParser =
                new ForkParser(ForkParserTest.class.getClassLoader(), new MockParser());
        forkParser.setServerPulseMillis(500);
        forkParser.setServerParseTimeoutMillis(5000);
        forkParser.setServerWaitTimeoutMillis(60000);
        String sleepCommand = "<mock>\n" + "    <write element=\"p\">Hello, World!</write>\n" +
                "    <hang millis=\"11000\" heavy=\"false\" interruptible=\"false\" />\n" +
                "</mock>";
        ContentHandler o = new BodyContentHandler(-1);
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        try {
            forkParser
                    .parse(new ByteArrayInputStream(sleepCommand.getBytes(StandardCharsets.UTF_8)),
                            o, m, c);
            fail("should have thrown IOException");
        } catch (TikaException e) {
            //failed to communicate with forked parser process"
        } finally {
            forkParser.close();
        }

        //test setting very short pulse (10 ms) and a parser that takes at least 1000 ms
        forkParser = new ForkParser(ForkParserTest.class.getClassLoader(), new MockParser());
        forkParser.setServerPulseMillis(10);
        forkParser.setServerParseTimeoutMillis(100);
        sleepCommand = "<mock>\n" + "    <write element=\"p\">Hello, World!</write>\n" +
                "    <hang millis=\"1000\" heavy=\"false\" interruptible=\"false\" />\n" +
                "</mock>";
        o = new BodyContentHandler(-1);
        m = new Metadata();
        c = new ParseContext();
        try {
            forkParser
                    .parse(new ByteArrayInputStream(sleepCommand.getBytes(StandardCharsets.UTF_8)),
                            o, m, c);
            fail("Should have thrown exception");
        } catch (IOException | TikaException e) {
            //"should have thrown IOException lost connection"
        } finally {
            forkParser.close();
        }
    }

    @Test
    public void testPackageCanBeAccessed() throws Exception {
        try (ForkParser parser = new ForkParser(ForkParserTest.class.getClassLoader(),
                new ForkTestParser.ForkTestParserAccessingPackage())) {
            Metadata metadata = new Metadata();
            ContentHandler output = new BodyContentHandler();
            InputStream stream = new ByteArrayInputStream(new byte[0]);
            ParseContext context = new ParseContext();
            parser.parse(stream, output, metadata, context);
            assertEquals("Hello, World!", output.toString().trim());
            assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        }
    }

    @Test
    public void testRecursiveParserWrapper() throws Exception {
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        20000));
        try (ForkParser fork = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                InputStream is = getResourceAsStream("/test-documents/basic_embedded.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            fork.parse(is, handler, metadata, context);
        }
        List<Metadata> metadataList = handler.getMetadataList();
        Metadata m0 = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", m0.get(TikaCoreProperties.CREATOR));
        assertContains("main_content", m0.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("embed1.xml", m0.get(TikaCoreProperties.TIKA_CONTENT));

        Metadata m1 = metadataList.get(1);
        assertEquals("embeddedAuthor", m1.get(TikaCoreProperties.CREATOR));
        assertContains("some_embedded_content", m1.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("/embed1.xml", m1.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testRPWWithEmbeddedNPE() throws Exception {
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        20000));
        try (ForkParser fork = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                InputStream is = getResourceAsStream("/test-documents/embedded_with_npe.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            fork.parse(is, handler, metadata, context);
        }
        List<Metadata> metadataList = handler.getMetadataList();
        Metadata m0 = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", m0.get(TikaCoreProperties.CREATOR));
        assertContains("main_content", m0.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("embed1.xml", m0.get(TikaCoreProperties.TIKA_CONTENT));

        Metadata m1 = metadataList.get(1);
        assertEquals("embeddedAuthor", m1.get(TikaCoreProperties.CREATOR));
        assertContains("some_embedded_content", m1.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("/embed1.xml", m1.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertContains("another null pointer exception",
                m1.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
    }

    @Test
    public void testRPWWithMainDocNPE() throws Exception {
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        20000));
        try (ForkParser fork = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                InputStream is = getResourceAsStream("/test-documents/embedded_then_npe.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            fork.parse(is, handler, metadata, context);
            fail();
        } catch (TikaException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
            assertContains("another", e.getCause().getMessage());
        }
        List<Metadata> metadataList = handler.getMetadataList();
        Metadata m0 = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", m0.get(TikaCoreProperties.CREATOR));
        assertContains("main_content", m0.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("embed1.xml", m0.get(TikaCoreProperties.TIKA_CONTENT));

        Metadata m1 = metadataList.get(1);
        assertEquals("embeddedAuthor", m1.get(TikaCoreProperties.CREATOR));
        assertContains("some_embedded_content", m1.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("/embed1.xml", m1.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testToFileHandler() throws Exception {
        //test that a server-side write-to-file works without proxying back the
        //AbstractContentHandlerFactory
        Path target = Files.createTempFile("fork-to-file-handler-", ".txt");
        try {
            ForkParser forkParser = null;
            try (InputStream is = getResourceAsStream("/test-documents/basic_embedded.xml")) {
                RecursiveParserWrapper wrapper = new RecursiveParserWrapper(new AutoDetectParser());
                ToFileHandler toFileHandler =
                        new ToFileHandler(new SBContentHandlerFactory(), target.toFile());
                forkParser = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                Metadata m = new Metadata();
                ParseContext context = new ParseContext();
                forkParser.parse(is, toFileHandler, m, context);
            } finally {
                if (forkParser != null) {
                    forkParser.close();
                }
            }

            String contents = null;
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                contents = IOUtils.toString(reader);
            }
            assertContainsCount(TikaCoreProperties.TIKA_PARSED_BY.getName() +
                    " : org.apache.tika.parser.DefaultParser", contents, 2);
            assertContainsCount(TikaCoreProperties.TIKA_PARSED_BY.getName() +
                    " : org.apache.tika.parser.mock.MockParser", contents, 2);
            assertContains("Nikolai Lobachevsky", contents);
            assertContains("embeddedAuthor", contents);
            assertContains("main_content", contents);
            assertContains("some_embedded_content", contents);
            assertContains("X-TIKA:embedded_resource_path : /embed1.xml", contents);
        } finally {
            Files.delete(target);
        }
    }

    @Test
    public void testRecursiveParserWrapperWithProxyingContentHandlersAndMetadata()
            throws Exception {
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        BufferingHandler handler = new BufferingHandler(new SBContentHandlerFactory());
        try (ForkParser fork = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                InputStream is = getResourceAsStream("/test-documents/basic_embedded.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            fork.parse(is, handler, metadata, context);
        }
        List<Metadata> metadataList = handler.getMetadataList();
        List<ContentHandler> contentHandlers = handler.getContentHandlers();
        Metadata m0 = metadataList.get(0);
        String content0 = contentHandlers.get(0).toString();
        assertEquals("Nikolai Lobachevsky", m0.get(TikaCoreProperties.CREATOR));
        assertContains("main_content", content0);
        assertContains("embed1.xml", content0);

        Metadata m1 = metadataList.get(1);
        String content1 = contentHandlers.get(1).toString();
        assertEquals("embeddedAuthor", m1.get(TikaCoreProperties.CREATOR));
        assertContains("some_embedded_content", content1);
        assertEquals("/embed1.xml", m1.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }


    @Test
    public void testRPWWithNonSerializableContentHandler() throws Exception {
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(new NonSerializableHandlerFactory());
        try (ForkParser fork = new ForkParser(ForkParserTest.class.getClassLoader(), wrapper);
                InputStream is = getResourceAsStream("/test-documents/embedded_then_npe.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            fork.parse(is, handler, metadata, context);
            fail();
        } catch (TikaException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
            assertContains("another", e.getCause().getMessage());
        }
        List<Metadata> metadataList = handler.getMetadataList();
        Metadata m0 = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", m0.get(TikaCoreProperties.CREATOR));
        assertContains("main_content", m0.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("embed1.xml", m0.get(TikaCoreProperties.TIKA_CONTENT));

        Metadata m1 = metadataList.get(1);
        assertEquals("embeddedAuthor", m1.get(TikaCoreProperties.CREATOR));
        assertContains("some_embedded_content", m1.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("/embed1.xml", m1.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testNoUTFDataFormatException() throws Exception {
        ContentHandlerProxy proxy = new ContentHandlerProxy(0);
        DataOutputStream output = new DataOutputStream(new ByteArrayOutputStream());
        proxy.init(null, output);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65536; i++) {
            sb.append(1);
        }
        proxy.skippedEntity(sb.toString());
    }


    //use this to test that the wrapper handler is acted upon by the server but not proxied back
    private static class ToFileHandler extends AbstractRecursiveParserWrapperHandler {

        //this needs to be a file because a File is serializable
        private final File file;
        private OutputStream os;

        public ToFileHandler(ContentHandlerFactory contentHandlerFactory, File file) {
            super(contentHandlerFactory);
            this.file = file;
        }

        @Override
        public void startDocument() throws SAXException {
            try {
                os = Files.newOutputStream(file.toPath());
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            try {
                os.flush();
                os.close();
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            try {
                byte[] bytes = toString(contentHandler, metadata);
                os.write(bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            try {
                byte[] bytes = toString(contentHandler, metadata);
                os.write(bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        private byte[] toString(ContentHandler contentHandler, Metadata metadata) {
            StringBuilder sb = new StringBuilder();
            for (String n : metadata.names()) {
                for (String v : metadata.getValues(n)) {
                    sb.append(n).append(" : ").append(v).append("\n");
                }
            }
            if (!contentHandler.getClass().equals(DefaultHandler.class)) {
                sb.append("\n");
                sb.append("CONTENT: ").append(contentHandler);
                sb.append("\n\n");
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static class SBContentHandler extends DefaultHandler implements Serializable {
        StringBuilder sb = new StringBuilder();

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            sb.append(ch, start, length);
            sb.append(" ");
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private static class SBContentHandlerFactory implements ContentHandlerFactory {

        @Override
        public ContentHandler getNewContentHandler() {
            return new SBContentHandler();
        }

        @Override
        public ContentHandler getNewContentHandler(OutputStream os, String encoding)
                throws UnsupportedEncodingException {
            throw new IllegalArgumentException("can't use this option in this test class");
        }

        @Override
        public ContentHandler getNewContentHandler(OutputStream os, Charset charset) {
            throw new IllegalArgumentException("can't use this option in this test class");
        }
    }

    private static class NonSerializableHandlerFactory implements ContentHandlerFactory {
        @Override
        public ContentHandler getNewContentHandler() {
            return new LyingNonSerializableContentHandler();
        }

        @Override
        public ContentHandler getNewContentHandler(OutputStream os, String encoding)
                throws UnsupportedEncodingException {
            throw new IllegalArgumentException("can't use this option in this test class");
        }

        @Override
        public ContentHandler getNewContentHandler(OutputStream os, Charset charset) {
            throw new IllegalArgumentException("can't use this option in this test class");
        }
    }

    private static class LyingNonSerializableContentHandler extends DefaultHandler
            implements Serializable {
        //StringWriter makes this class not actually Serializable
        //as is.
        StringWriter writer = new StringWriter();

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            writer.write(ch, start, length);
        }

        @Override
        public String toString() {
            return writer.toString();
        }
    }

    //use this to test that a handler that extends RecursiveParserWrapperHandler
    //does have both contenthandlers and metadata objects proxied back from the
    //server.
    private static class BufferingHandler extends RecursiveParserWrapperHandler {
        List<ContentHandler> contentHandlers = new ArrayList<>();

        public BufferingHandler(ContentHandlerFactory contentHandlerFactory) {
            super(contentHandlerFactory);
        }


        @Override
        public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            contentHandlers.add(contentHandler);
            metadataList.add(metadata);
        }

        @Override
        public void endDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            contentHandlers.add(0, contentHandler);
            metadataList.add(0, metadata);
        }

        public List<ContentHandler> getContentHandlers() {
            return contentHandlers;
        }

        @Override
        public List<Metadata> getMetadataList() {
            return metadataList;
        }

    }
}
