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
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.ContentHandler;

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

    protected static class XMLResult {
        public final String xml;
        public final Metadata metadata;

        public XMLResult(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
        }
    }

    protected XMLResult getXML(String filePath, Metadata metadata) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), new AutoDetectParser(), metadata);
    }

    protected XMLResult getXML(String filePath) throws Exception {
        return getXML(getResourceAsStream("/test-documents/" + filePath), new AutoDetectParser(), new Metadata());
    }

    protected XMLResult getXML(InputStream input, Parser parser, Metadata metadata) throws Exception {
      ParseContext context = new ParseContext();
      context.set(Parser.class, parser);

      try {
          ContentHandler handler = new ToXMLContentHandler();
          parser.parse(input, handler, metadata, context);
          return new XMLResult(handler.toString(), metadata);
      } finally {
          input.close();
      }
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
}
