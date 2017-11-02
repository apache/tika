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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parent class of Tika tests
 */
public abstract class TikaTest {
   /**
    * This method will give you back the filename incl. the absolute path name
    * to the resource. If the resource does not exist it will give you back the
    * resource name incl. the path.
    *
    * @param name
    *            The named resource to search for.
    * @return an absolute path incl. the name which is in the same directory as
    *         the the class you've called it from.
    */
   public File getResourceAsFile(String name) throws URISyntaxException {
       URL url = this.getClass().getResource(name);
       if (url != null) {
           return new File(url.toURI());
       } else {
           // We have a file which does not exists
           // We got the path
           url = this.getClass().getResource(".");
           File file = new File(new File(url.toURI()), name);
           if (file == null) {
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

    public static void assertContainsCount(String needle, String haystack, int targetCount) {
        int i = haystack.indexOf(needle);
        int count = 0;
        while (i > -1) {
            count++;
            i = haystack.indexOf(needle, i+1);
        }
        assertEquals("found "+count +" but should have found: "+targetCount,
                targetCount, count);
    }


    public static void assertContains(String needle, String haystack) {
        assertTrue(needle + " not found in:\n" + haystack, haystack.contains(needle));
    }

    public static <T> void assertContains(T needle, Collection<? extends T> haystack) {
        assertTrue(needle + " not found in:\n" + haystack, haystack.contains(needle));
    }

    public static void assertNotContained(String needle, String haystack) {
        assertFalse(needle + " unexpectedly found in:\n" + haystack, haystack.contains(needle));
    }
    public static <T> void assertNotContained(T needle, Collection<? extends T> haystack) {
        assertFalse(needle + " unexpectedly found in:\n" + haystack, haystack.contains(needle));
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
    protected static class XMLResult {
        public final String xml;
        public final Metadata metadata;

        public XMLResult(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
        }
    }

    protected XMLResult getXML(String filePath, Parser parser, ParseContext context) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), parser, new Metadata(), context);
    }

    protected XMLResult getXML(String filePath, Parser parser, Metadata metadata) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), parser, metadata, null);
    }

    protected XMLResult getXML(String filePath, ParseContext parseContext) throws Exception {
        return getXML(filePath, new AutoDetectParser(), parseContext);
    }

    protected XMLResult getXML(String filePath, Metadata metadata, ParseContext parseContext) throws Exception {
        return getXML(getResourceAsStream("/test-documents/"+filePath), new AutoDetectParser(), metadata, parseContext);
    }

    protected XMLResult getXML(String filePath, Metadata metadata) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), new AutoDetectParser(), metadata, null);
    }

    protected XMLResult getXML(String filePath, Parser parser) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, filePath);
        return getXML(filePath, parser, metadata);
    }

    protected XMLResult getXML(String filePath) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), new AutoDetectParser(), new Metadata(), null);
    }

    protected XMLResult getXML(InputStream input, Parser parser, Metadata metadata) throws Exception {
        return getXML(input, parser, metadata, null);
    }

    protected XMLResult getXML(InputStream input, Parser parser, Metadata metadata, ParseContext context) throws Exception {
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

    protected List<Metadata> getRecursiveMetadata(String filePath) throws Exception {
        return getRecursiveMetadata(filePath, new ParseContext());
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, ParseContext context) throws Exception {
        Parser p = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, new DefaultHandler(), new Metadata(), context);
        }
        return wrapper.getMetadata();
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parserToWrap,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
        }
        return wrapper.getMetadata();
    }

    protected List<Metadata> getRecursiveMetadata(String filePath, Parser parserToWrap, ParseContext parseContext) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parserToWrap,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        try (InputStream is = getResourceAsStream("/test-documents/" + filePath)) {
            wrapper.parse(is, new DefaultHandler(), new Metadata(), parseContext);
        }
        return wrapper.getMetadata();
    }


    /**
     * Basic text extraction.
     * <p>
     * Tries to close input stream after processing.
     */
    public String getText(InputStream is, Parser parser, ParseContext context, Metadata metadata) throws Exception{
        ContentHandler handler = new BodyContentHandler(1000000);
        try {
            parser.parse(is, handler, metadata, context);
        } finally {
            is.close();
        }
        return handler.toString();
    }

    public String getText(InputStream is, Parser parser, Metadata metadata) throws Exception{
        return getText(is, parser, new ParseContext(), metadata);
    }

    public String getText(InputStream is, Parser parser, ParseContext context) throws Exception{
        return getText(is, parser, context, new Metadata());
    }

    public String getText(InputStream is, Parser parser) throws Exception{
        return getText(is, parser, new ParseContext(), new Metadata());
    }

    /**
     * Keeps track of media types and file names recursively.
     *
     */
    public static class TrackingHandler implements EmbeddedResourceHandler {
        public List<String> filenames = new ArrayList<String>();
        public List<MediaType> mediaTypes = new ArrayList<MediaType>();
        
        private final Set<MediaType> skipTypes;
        
        public TrackingHandler() {
            skipTypes = new HashSet<MediaType>();
        }
     
        public TrackingHandler(Set<MediaType> skipTypes) {
            this.skipTypes = skipTypes;
        }

        @Override
        public void handle(String filename, MediaType mediaType,
                InputStream stream) {
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

        public List<byte[]> bytes = new ArrayList<byte[]>();

        @Override
        public void handle(String filename, MediaType mediaType,
                InputStream stream) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (! stream.markSupported()) {
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

    public static void debug(List<Metadata> list) {
        int i = 0;
        for (Metadata m : list) {
            for (String n : m.names()) {
                for (String v : m.getValues(n)) {
                    System.out.println(i + ": "+n + " : "+v);
                }
            }
            i++;
        }
    }

    public static void debug(Metadata metadata) {
        for (String n : metadata.names()) {
            for (String v : metadata.getValues(n)) {
                System.out.println(n + " : "+v);
            }
        }
    }
}
