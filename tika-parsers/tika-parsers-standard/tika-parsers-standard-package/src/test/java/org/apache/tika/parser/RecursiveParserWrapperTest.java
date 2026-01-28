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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ClosedInputStream;
import org.apache.commons.io.input.ProxyInputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class RecursiveParserWrapperTest extends TikaTest {

    @Test
    public void testBasicXML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.contains("<p class=\"header\" />"));
    }

    @Test
    public void testBasicHTML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.contains("<p class=\"header\"></p>"));
    }

    @Test
    public void testBasicText() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertFalse(content.contains("<p "));
        assertTrue(content.contains("embed_0"));
    }

    @Test
    public void testIgnoreContent() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertNull(content);
    }

    @Test
    public void testCharLimit() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        70));
        try (TikaInputStream tis =
                    getResourceAsStream("/test-documents/test_recursive_embedded.docx")) {
            wrapper.parse(tis, handler, metadata, context);
        }
        List<Metadata> list = handler.getMetadataList();

        assertEquals(2, list.size());

        int wlr = 0;
        for (Metadata m : list) {
            String limitReached = m.get(TikaCoreProperties.WRITE_LIMIT_REACHED);
            if (limitReached != null && limitReached.equals("true")) {
                wlr++;
            }
        }
        assertEquals(2, wlr);
    }

    @Test
    public void testOne() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        int writeLimit = 100;
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        writeLimit, false, context));
        try (TikaInputStream tis = getResourceAsStream(
                "/test-documents/test_recursive_embedded" + ".docx")) {
            wrapper.parse(tis, handler, metadata, context);
        }
        List<Metadata> list = handler.getMetadataList();
        assertEquals(12, list.size());
    }

    @Test
    public void testTarball() throws Exception {
        List<Metadata> list = getRecursiveMetadata("test-documents.tgz");
        List<String> actualInternalPaths =
                list.stream()
                        .map(m -> m.get(TikaCoreProperties.INTERNAL_PATH))
                        .collect(Collectors.toList());

        List<String> expectedInternalPaths = Arrays.asList(null,
                "test-documents/testEXCEL.xls",
                "test-documents/testHTML.html",
                "Thumbnails/thumbnail.png",
                "Thumbnails/thumbnail.pdf",
                "test-documents/testOpenOffice2.odt",
                "test-documents/testPDF.pdf",
                "test-documents/testPPT.ppt",
                "test-documents/testRTF.rtf",
                "test-documents/testTXT.txt",
                "test-documents/testWORD.doc",
                "test-documents/testXML.xml",
                "test-documents.tar");
        assertEquals(expectedInternalPaths, actualInternalPaths);

        List<String> actualEmbeddedPaths =
                list.stream()
                    .map(m -> m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH))
                    .collect(Collectors.toList());
        assertEquals(Arrays.asList(null,
                "/test-documents.tar/testEXCEL.xls",
                "/test-documents.tar/testHTML.html",
                "/test-documents.tar/testOpenOffice2.odt/thumbnail.png",
                "/test-documents.tar/testOpenOffice2.odt/thumbnail.pdf",
                "/test-documents.tar/testOpenOffice2.odt",
                "/test-documents.tar/testPDF.pdf",
                "/test-documents.tar/testPPT.ppt",
                "/test-documents.tar/testRTF.rtf",
                "/test-documents.tar/testTXT.txt",
                "/test-documents.tar/testWORD.doc",
                "/test-documents.tar/testXML.xml",
                "/test-documents.tar"), actualEmbeddedPaths);
    }

    @Test
    public void testCharLimitNoThrowOnWriteLimit() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        int writeLimit = 510;
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        writeLimit, false, context));
        try (TikaInputStream tis = getResourceAsStream("/test-documents/test_recursive_embedded" +
                ".docx")) {
            wrapper.parse(tis, handler, metadata, context);
        }
        List<Metadata> list = handler.getMetadataList();

        assertEquals(12, list.size());

        assertEquals("true", list.get(0).get(TikaCoreProperties.WRITE_LIMIT_REACHED));

        assertContains("necessary for one people",
                list.get(6).get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("dissolve the political",
                list.get(6).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testSpecificLimit() throws Exception {
        int writeLimit = 60;

        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        writeLimit, false, context));
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testRTFEmbeddedFiles.rtf")) {
            wrapper.parse(tis, handler, metadata, context);
        }
        List<Metadata> list = handler.getMetadataList();
        assertTrue(writeLimit >= getContentLength(list),
                "writeLimit=" + writeLimit + " contentLength=" + getContentLength(list));
    }

    private int getContentLength(List<Metadata> metadataList) {
        int sz = 0;
        for (Metadata metadata : metadataList) {
            String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
            if (content != null) {
                sz += content.length();
            }
        }
        return sz;
    }

    @Test
    public void testMaxEmbedded() throws Exception {
        int maxEmbedded = 4;
        int totalNoLimit = 12;//including outer container file
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String limitReached = null;

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);

        //test default
        try (TikaInputStream tis = getResourceAsStream("/test-documents/test_recursive_embedded.docx")) {
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
            wrapper.parse(tis, handler, metadata, context);
            List<Metadata> list = handler.getMetadataList();
            assertEquals(totalNoLimit, list.size());

            limitReached = list.get(0)
                    .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
            assertNull(limitReached);
        }

        //test setting value
        metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/test_recursive_embedded.docx")) {
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                    maxEmbedded);
            wrapper.parse(tis, handler, metadata, context);
            List<Metadata> list = handler.getMetadataList();
            //add 1 for outer container file
            assertEquals(maxEmbedded + 1, list.size());

            limitReached = list.get(0)
                    .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
            assertEquals("true", limitReached);
        }

        //test setting value < 0
        metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/test_recursive_embedded.docx")) {
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                    -2);
            wrapper.parse(tis, handler, metadata, context);
            List<Metadata> list = handler.getMetadataList();
            assertEquals(totalNoLimit, list.size());
            limitReached = list.get(0)
                    .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
            assertNull(limitReached);
        }
    }


    @Test
    public void testEmbeddedResourcePath() throws Exception {

        Set<String> targets = new HashSet<>();
        targets.add("/embed1.zip");
        targets.add("/embed1.zip/embed2.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip/embed4.txt");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed3.txt");
        targets.add("/embed1.zip/embed2.zip/embed2a.txt");
        targets.add("/embed1.zip/embed2.zip/embed2b.txt");
        targets.add("/embed1.zip/embed1b.txt");
        targets.add("/embed1.zip/embed1a.txt");
        targets.add("/image1.emf");

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertTrue(content.contains("<p class=\"header\" />"));

        Set<String> seen = new HashSet<>();
        for (Metadata m : list) {
            String path = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (path != null) {
                seen.add(path);
            }
        }
        assertEquals(targets, seen);
    }

    @Test
    public void testEmbeddedNPE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        //default behavior (user doesn't specify whether or not to catch embedded exceptions
        //is to catch the exception
        assertEquals(13, list.size());
        Metadata mockNPEMetadata = list.get(10);
        assertContains("java.lang.NullPointerException",
                mockNPEMetadata.get(TikaCoreProperties.EMBEDDED_EXCEPTION));

        metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                false, false);

        //Composite parser swallows caught TikaExceptions, IOExceptions and SAXExceptions
        //and just doesn't bother to report that there was an exception.
        assertEquals(13, list.size());
    }

    @Test
    public void testPrimaryExcWEmbedded() throws Exception {
        //if embedded content is handled and then
        //the parser hits an exception in the container document,
        //that the first element of the returned list is the container document
        //and the second is the embedded content
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "embedded_then_npe.xml");

        ParseContext context = new ParseContext();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER, true);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));

        String path = "/test-documents/mock/embedded_then_npe.xml";

        TikaInputStream tis = null;
        boolean npe = false;
        try {
            tis = getResourceAsStream(path);
            wrapper.parse(tis, handler, metadata, context);
        } catch (TikaException e) {
            if (e.getCause().getClass().equals(NullPointerException.class)) {
                npe = true;
            }
        } finally {
            IOUtils.closeQuietly(tis);
        }
        assertTrue(npe, "npe");

        List<Metadata> metadataList = handler.getMetadataList();
        assertEquals(2, metadataList.size());
        Metadata outerMetadata = metadataList.get(0);
        Metadata embeddedMetadata = metadataList.get(1);
        assertContains("main_content", outerMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("embedded_then_npe.xml",
                outerMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Nikolai Lobachevsky", outerMetadata.get("author"));

        assertContains("some_embedded_content",
                embeddedMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("embed1.xml", embeddedMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("embeddedAuthor", embeddedMetadata.get("author"));
    }

    @Test
    public void testDigesters() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                true, true);

        String md5Key = "X-TIKA:digest:MD5";
        assertEquals("59f626e09a8c16ab6dbc2800c685f772", list.get(0).get(md5Key));
        assertEquals("ccdf3882e7e4c2454e28884db9b0a54d", list.get(6).get(md5Key));
        assertEquals("a869bf6432ebd14e19fc79416274e0c9", list.get(7).get(md5Key));

        //while we're at it, also test the embedded path id
        assertEquals("/2/5/8/9", list.get(6).get(TikaCoreProperties.EMBEDDED_ID_PATH));
        assertEquals("/embed1.zip/embed2.zip/embed3.zip/embed3.txt",
                list.get(6).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals(9, list.get(6).getInt(TikaCoreProperties.EMBEDDED_ID));
        assertEquals(4, list.get(6).getInt(TikaCoreProperties.EMBEDDED_DEPTH));
    }

    @Test
    public void testStreamClosedAfterSpill() throws Exception {
        // When TikaInputStream spills to a temp file (via getPath()/getFile()),
        // the source stream should be closed promptly since all bytes have been
        // consumed and cached - there's no reason to keep it open.
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER, true);
        String path = "/test-documents/test_recursive_embedded.docx";
        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);

        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(contentHandlerFactory);
        try (CloseCountingInputStream stream =
                     new CloseCountingInputStream(getResourceAsStream(path))) {
            TikaInputStream tis = TikaInputStream.get(stream);
            tis.setCloseShield();
            wrapper.parse(tis, handler, metadata, context);
            // Source stream should not be closed after spilling to file
            assertEquals(0, stream.counter);
            tis.removeCloseShield();
            tis.close();
        }

    }
    
    private List<Metadata> getMetadata(Metadata metadata,
                                       ContentHandlerFactory contentHandlerFactory,
                                       boolean catchEmbeddedExceptions,
                                       boolean digest) throws Exception {
        ParseContext context;
        Parser wrapped;
        if (digest) {
            TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-md5-digest.json");
            wrapped = loader.loadAutoDetectParser();
            context = loader.loadParseContext();
        } else {
            wrapped = AUTO_DETECT_PARSER;
            context = new ParseContext();
        }
        RecursiveParserWrapper wrapper =
                new RecursiveParserWrapper(wrapped, catchEmbeddedExceptions);
        String path = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (path == null) {
            path = "/test-documents/test_recursive_embedded.docx";
        } else {
            path = "/test-documents/" + path;
        }
        TikaInputStream tis = null;
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(contentHandlerFactory);
        try {
            tis = TikaInputStream.get(getResourceAsUri(path));
            wrapper.parse(tis, handler, metadata, context);
        } finally {
            IOUtils.closeQuietly(tis);
        }
        return handler.getMetadataList();
    }

    private List<Metadata> getMetadata(Metadata metadata,
                                       ContentHandlerFactory contentHandlerFactory)
            throws Exception {
        return getMetadata(metadata, contentHandlerFactory, true, false);
    }

    private static class CloseCountingInputStream extends ProxyInputStream {
        int counter = 0;

        public CloseCountingInputStream(InputStream in) {
            super(in);
        }

        /**
         * Replaces the underlying input stream with a {@link ClosedInputStream}
         * sentinel. The original input stream will remain open, but this proxy
         * will appear closed.
         */
        @Override
        public void close() throws IOException {
            in.close();
            counter++;
        }
    }
}
