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
package org.apache.tika;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.ToXMLContentHandler;

/**
 * Parent class of Tika tests
 */
public abstract class TikaTest {

    protected static TikaConfig DEFAULT_TIKA_CONFIG;
    protected static Parser AUTO_DETECT_PARSER = new AutoDetectParser();

    static {
        try {
            DEFAULT_TIKA_CONFIG = new TikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static void assertContainsCount(String needle, String haystack, int targetCount) {
        int i = haystack.indexOf(needle);
        int count = 0;
        while (i > -1) {
            count++;
            i = haystack.indexOf(needle, i + 1);
        }
        assertEquals(targetCount, count,
                "found " + count + " but should have found: " + targetCount);
    }

    public static void assertContains(String needle, String haystack) {
        assertTrue(haystack.contains(needle), needle + " not found in:\n" + haystack);
    }

    public static <T> void assertContains(T needle, Collection<? extends T> haystack) {
        assertTrue(haystack.contains(needle), needle + " not found in:\n" + haystack);
    }

    public static void assertNotContained(String needle, String haystack) {
        assertFalse(haystack.contains(needle), needle + " unexpectedly found in:\n" + haystack);
    }

    public static <T> void assertNotContained(T needle, Collection<? extends T> haystack) {
        assertFalse(haystack.contains(needle), needle + " unexpectedly found in:\n" + haystack);
    }

    public static void assertMetadataListEquals(List<Metadata> metadataListA,
                                          List<Metadata> metadataListB,
                                    Set<String> fieldsToIgnore) {
        assertEquals(metadataListA.size(), metadataListB.size(), "different sizes");
        for (int i = 0; i < metadataListA.size(); i++) {
            Metadata mA = metadataListA.get(i);
            Metadata mB = metadataListB.get(i);
            Set<String> mAFields = new HashSet<>();
            for (String n : mA.names()) {
                if (fieldsToIgnore.contains(n)) {
                    continue;
                }
                mAFields.add(n);
                assertArrayEquals(mA.getValues(n), mB.getValues(n), "problem with " + n +
                        " in metadata index=" + i);
            }
            Set<String> mBFields = new HashSet<>();
            for (String n : mB.names()) {
                if (fieldsToIgnore.contains(n)) {
                    continue;
                }
                mBFields.add(n);
            }
            assertEquals(mAFields, mBFields);
        }
    }

    /**
     * Test that in at least one item in metadataList, all keys and values
     * in minExpected are contained.
     * <p>
     * The values in minExpected are tested for whether they are contained
     * within a value in the target.  If minExpected=&dquot;text/vbasic&dquot;  and
     * what was actually found in the target within metadatalist is
     * &dquot;text/vbasic; charset=windows-1252&dquot;,
     * that is counted as a hit.
     *
     * @param minExpected
     * @param metadataList
     */
    public static void assertContainsAtLeast(Metadata minExpected, List<Metadata> metadataList) {

        for (Metadata m : metadataList) {
            int foundPropertyCount = 0;
            for (String n : minExpected.names()) {
                int foundValCount = 0;
                for (String foundVal : m.getValues(n)) {
                    for (String expectedVal : minExpected.getValues(n)) {
                        if (foundVal.contains(expectedVal)) {
                            foundValCount++;
                        }
                    }
                }
                if (foundValCount == minExpected.getValues(n).length) {
                    foundPropertyCount++;
                }
            }
            if (foundPropertyCount == minExpected.names().length) {
                //found everything!
                return;
            }
        }
        //TODO: figure out how to have more informative error message
        fail("Couldn't find everything within a single metadata item");
    }

    public static void debug(List<Metadata> list) {
        int i = 0;
        for (Metadata m : list) {
            List<String> names = Arrays.asList(m.names());
            Collections.sort(names);
            for (String n : names) {
                for (String v : m.getValues(n)) {
                    System.out.println(i + ": " + n + " : " + v);
                }
            }
            i++;
        }
    }

    public static void debug(Metadata metadata) {
        List<String> names = Arrays.asList(metadata.names());
        Collections.sort(names);
        for (String n : names) {
            for (String v : metadata.getValues(n)) {
                System.out.println(n + " : " + v);
            }
        }
    }

    public static Parser findParser(Parser parser, Class clazz) {
        if (parser instanceof CompositeParser) {
            for (Parser child : ((CompositeParser) parser).getAllComponentParsers()) {
                Parser found = findParser(child, clazz);
                if (found != null) {
                    return found;
                }
            }
        } else if (clazz.isInstance(parser)) {
            return parser;
        }
        return null;
    }

    /**
     * Finds a resource with a given name.
     *
     * @param name name of the desired resource
     * @return A {@link java.net.URL} object or null
     */
    public URL getResourceAsUrl(String name) {
        return this.getClass().getResource(name);
    }

    /**
     * Finds a resource with a given name.
     *
     * @param name name of the desired resource
     * @return A {@link java.net.URI} object or null
     * @throws URISyntaxException if this URL is not formatted strictly according to
     *                            RFC2396 and cannot be converted to a URI.
     */
    public URI getResourceAsUri(String name) throws URISyntaxException {
        URL url = getResourceAsUrl(name);
        if (url == null) {
            return null;
        }
        return url.toURI();
    }

    /**
     * This method will give you back the filename incl. the absolute path name
     * to the resource. If the resource does not exist it will give you back the
     * resource name incl. the path.
     *
     * @param name The named resource to search for.
     * @return an absolute path incl. the name which is in the same directory as
     * the the class you've called it from.
     */
    public File getResourceAsFile(String name) throws URISyntaxException {
        URI uri = getResourceAsUri(name);
        if (uri != null) {
            return new File(uri);
        } else {
            // We have a file which does not exists
            // We got the path
            uri = getResourceAsUri(".");
            File file = new File(new File(uri), name);
            if (!file.exists()) {
                fail("Unable to find requested file " + name);
            }
            return file;
        }
    }

    public InputStream getResourceAsStream(String name) {
        InputStream stream = this.getClass().getResourceAsStream(name);
        if (stream == null) {
            fail("Unable to find requested resource " + name);
        }
        return stream;
    }

    protected XMLResult getXML(String filePath, Parser parser, ParseContext context)
            throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), parser, new Metadata(),
                context);
    }

    protected XMLResult getXML(String filePath, Parser parser, Metadata metadata) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), parser, metadata, null);
    }

    protected XMLResult getXML(String filePath, ParseContext parseContext) throws Exception {
        return getXML(filePath, AUTO_DETECT_PARSER, parseContext);
    }

    protected XMLResult getXML(String filePath, Parser parser, Metadata metadata,
                               ParseContext parseContext)
            throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), parser,
                metadata, parseContext);
    }

    protected XMLResult getXML(String filePath, Metadata metadata, ParseContext parseContext)
            throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), AUTO_DETECT_PARSER,
                metadata, parseContext);
    }

    protected XMLResult getXML(String filePath, Metadata metadata) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), AUTO_DETECT_PARSER,
                metadata, null);
    }

    protected XMLResult getXML(String filePath, Parser parser) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filePath);
        return getXML(filePath, parser, metadata);
    }

    protected XMLResult getXML(String filePath) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), AUTO_DETECT_PARSER,
                new Metadata(), null);
    }

    protected XMLResult getXML(InputStream input, Parser parser, Metadata metadata)
            throws Exception {
        return getXML(input, parser, metadata, null);
    }

    protected XMLResult getXML(InputStream input, Parser parser, Metadata metadata,
                               ParseContext context) throws Exception {
        if (context == null) {
            context = new ParseContext();
        }

        try {
            ContentHandler handler = new ToXMLContentHandler();
            parser.parse(input, handler, metadata, context);
            return new XMLResult(handler.toString(), metadata);
        } finally {
            input.close();
        }
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, boolean suppressException)
            throws Exception {
        return getRecursiveMetadata(filePath, new Metadata(), new ParseContext(),
                suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, ParseContext parseContext,
                                                  boolean suppressException) throws Exception {
        return getRecursiveMetadata(filePath, new Metadata(), parseContext, suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath) throws Exception {
        return getRecursiveMetadata(filePath, new ParseContext());
    }

    protected List<Metadata> getRecursiveMetadata(String filePath,
                                                  BasicContentHandlerFactory.HANDLER_TYPE handlerType)
            throws Exception {
        return getRecursiveMetadata(filePath, TikaTest.AUTO_DETECT_PARSER, new Metadata(),
                new ParseContext(), true,
                handlerType);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Metadata metadata)
            throws Exception {
        return getRecursiveMetadata(filePath, metadata, new ParseContext());
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Metadata metadata,
                                                  ParseContext context) throws Exception {
        return getRecursiveMetadata(filePath, metadata, context, false);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Metadata metadata,
                                                  ParseContext context, boolean suppressException)
            throws Exception {
        return getRecursiveMetadata(filePath, AUTO_DETECT_PARSER, metadata, context,
                suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser wrapped,
                                                  Metadata metadata, ParseContext context,
                                                  boolean suppressException) throws Exception {
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            return getRecursiveMetadata(is, wrapped, metadata, context, suppressException);
        }
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser wrapped,
                                                  Metadata metadata, ParseContext context,
                                                  boolean suppressException,
                                                  BasicContentHandlerFactory.HANDLER_TYPE handlerType)
            throws Exception {
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            return getRecursiveMetadata(is, wrapped, metadata, context, suppressException, handlerType);
        }
    }

    protected List<Metadata> getRecursiveMetadata(Path path, ParseContext context,
                                                  boolean suppressException) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(path, metadata)) {
            return getRecursiveMetadata(tis, AUTO_DETECT_PARSER, metadata, context,
                    suppressException);
        }
    }

    protected List<Metadata> getRecursiveMetadata(Path path, Parser parser,
                                                  boolean suppressException) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(path, metadata)) {
            return getRecursiveMetadata(tis, parser, metadata, new ParseContext(),
                    suppressException);
        }
    }

    protected List<Metadata> getRecursiveMetadata(Path p, boolean suppressException)
            throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(p, metadata)) {
            return getRecursiveMetadata(tis, metadata, new ParseContext(), suppressException);
        }
    }

    protected List<Metadata> getRecursiveMetadata(Path filePath) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(filePath, metadata)) {
            return getRecursiveMetadata(tis, true);
        }
    }

    protected List<Metadata> getRecursiveMetadata(InputStream is, boolean suppressException)
            throws Exception {
        return getRecursiveMetadata(is, new Metadata(), new ParseContext(), suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(InputStream is, Parser parser,
                                                  boolean suppressException) throws Exception {
        return getRecursiveMetadata(is, parser, new Metadata(), new ParseContext(),
                suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(InputStream is, Metadata metadata,
                                                  ParseContext context, boolean suppressException)
            throws Exception {
        return getRecursiveMetadata(is, AUTO_DETECT_PARSER, metadata, context, suppressException);
    }

    protected List<Metadata> getRecursiveMetadata(InputStream is, Parser p, Metadata metadata,
                                                  ParseContext context, boolean suppressException)
            throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        try {
            wrapper.parse(is, handler, metadata, context);
        } catch (Exception e) {
            if (!suppressException) {
                throw e;
            }
        }
        return handler.getMetadataList();
    }

    protected List<Metadata> getRecursiveMetadata(InputStream is, Parser p, Metadata metadata,
                                                  ParseContext context, boolean suppressException,
                                                  BasicContentHandlerFactory.HANDLER_TYPE handlerType)
            throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerType, -1));
        try {
            wrapper.parse(is, handler, metadata, context);
        } catch (Exception e) {
            if (!suppressException) {
                throw e;
            }
        }
        return handler.getMetadataList();
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, ParseContext context)
            throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);

        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, handler, new Metadata(), context);
        }
        return handler.getMetadataList();
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap)
            throws Exception {
        return getRecursiveMetadata(filePath, parserToWrap,
                BasicContentHandlerFactory.HANDLER_TYPE.XML);
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap,
                                                  BasicContentHandlerFactory.HANDLER_TYPE
                                                          handlerType)
            throws Exception {
        return getRecursiveMetadata(filePath, parserToWrap, handlerType, new ParseContext());
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap,
                                                  BasicContentHandlerFactory.HANDLER_TYPE
                                                          handlerType,
                                                  ParseContext context) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parserToWrap);
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(new BasicContentHandlerFactory(handlerType, -1));
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, FilenameUtils.getName(filePath));
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, handler, metadata, context);
        }
        return handler.getMetadataList();
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap,
                                                  ParseContext parseContext) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parserToWrap);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));

        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, handler, new Metadata(), parseContext);
        }
        return handler.getMetadataList();
    }

    protected String getText(String filePath, Parser parser) throws Exception {
        return getText(filePath, parser, new Metadata(), new ParseContext());
    }

    protected String getText(String filePath, Parser parser, Metadata metadata) throws Exception {
        return getText(filePath, parser, metadata, new ParseContext());
    }

    protected String getText(String filePath) throws Exception {
        return getText(filePath, new Metadata(), new ParseContext());
    }

    protected String getText(String filePath, Metadata metadata) throws Exception {
        return getText(filePath, metadata, new ParseContext());
    }

    protected String getText(String filePath, Metadata metadata, ParseContext parseContext)
            throws Exception {
        return getText(filePath, AUTO_DETECT_PARSER, metadata, parseContext);
    }

    protected String getText(String filePath, Parser parser, Metadata metadata,
                             ParseContext parseContext) throws Exception {
        return getText(getResourceAsStream("/test-documents/" + filePath), parser, parseContext,
                metadata);
    }

    /**
     * Basic text extraction.
     * <p>
     * Tries to close input stream after processing.
     */
    public String getText(InputStream is, Parser parser, ParseContext context, Metadata metadata)
            throws Exception {
        ContentHandler handler = new BodyContentHandler(1000000);
        try {
            parser.parse(is, handler, metadata, context);
        } finally {
            is.close();
        }
        return handler.toString();
    }

    public String getText(InputStream is, Parser parser, Metadata metadata) throws Exception {
        return getText(is, parser, new ParseContext(), metadata);
    }

    public String getText(InputStream is, Parser parser, ParseContext context) throws Exception {
        return getText(is, parser, context, new Metadata());
    }

    public String getText(InputStream is, Parser parser) throws Exception {
        return getText(is, parser, new ParseContext(), new Metadata());
    }

    public InputStream truncate(String testFileName, int truncatedLength) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getResourceAsStream("/test-documents/" + testFileName)) {
            IOUtils.copy(is, bos);
        }
        if (truncatedLength > bos.toByteArray().length) {
            throw new EOFException(
                    "Can't truncate beyond file length: " + bos.toByteArray().length);
        }
        byte[] truncated = new byte[truncatedLength];
        System.arraycopy(bos.toByteArray(), 0, truncated, 0, truncatedLength);
        return TikaInputStream.get(truncated);
    }

    public List<Path> getAllTestFiles() {
        //for now, just get main files
        //TODO: fix this to be recursive
        try {
            File[] pathArray = Paths.get(getResourceAsUri("/test-documents")).toFile().listFiles();
            List<Path> paths = new ArrayList<>();
            for (File f : pathArray) {
                paths.add(f.toPath());
            }
            return paths;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class XMLResult {
        public final String xml;
        public final Metadata metadata;

        public XMLResult(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
        }
    }

    /**
     * Keeps track of media types and file names recursively.
     */
    public static class TrackingHandler implements EmbeddedResourceHandler {
        private final Set<MediaType> skipTypes;
        public List<String> filenames = new ArrayList<>();
        public List<MediaType> mediaTypes = new ArrayList<>();

        public TrackingHandler() {
            skipTypes = new HashSet<>();
        }

        public TrackingHandler(Set<MediaType> skipTypes) {
            this.skipTypes = skipTypes;
        }

        @Override
        public void handle(String filename, MediaType mediaType, InputStream stream) {
            if (skipTypes.contains(mediaType)) {
                return;
            }
            mediaTypes.add(mediaType);
            filenames.add(filename);
        }
    }

    /**
     * Copies byte[] of embedded documents into a List.
     */
    public static class ByteCopyingHandler implements EmbeddedResourceHandler {

        public List<byte[]> bytes = new ArrayList<>();

        @Override
        public void handle(String filename, MediaType mediaType, InputStream stream) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (!stream.markSupported()) {
                stream = TikaInputStream.get(stream);
            }
            stream.mark(0);
            try {
                IOUtils.copy(stream, os);
                bytes.add(os.toByteArray());
                stream.reset();
            } catch (IOException e) {
                //swallow
            }
        }
    }
}
