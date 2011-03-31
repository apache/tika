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
package org.apache.tika.parser.pkg;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parent class for all Package based Test cases
 */
public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext trackingContext;
   protected ParseContext recursingContext;
   
   protected Parser autoDetectParser;
   protected EmbeddedTrackingParser tracker;

   protected void setUp() throws Exception {
      super.setUp();
      
      tracker = new EmbeddedTrackingParser();
      trackingContext = new ParseContext();
      trackingContext.set(Parser.class, tracker);
      
      autoDetectParser = new AutoDetectParser();
      recursingContext = new ParseContext();
      recursingContext.set(Parser.class, autoDetectParser);
   }


   @SuppressWarnings("serial")
   protected static class EmbeddedTrackingParser extends AbstractParser {
      protected List<String> filenames = new ArrayList<String>();
      protected List<String> mediatypes = new ArrayList<String>();
      protected byte[] lastSeenStart;
      
      public void reset() {
         filenames.clear();
         mediatypes.clear();
      }
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
         // Cheat!
         return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
         filenames.add(metadata.get(Metadata.RESOURCE_NAME_KEY));
         mediatypes.add(metadata.get(Metadata.CONTENT_TYPE));
         
         lastSeenStart = new byte[32];
         stream.read(lastSeenStart);
      }

   }
}
