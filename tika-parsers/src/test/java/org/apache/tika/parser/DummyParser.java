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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A Dummy Parser for use with unit tests.
 */
public class DummyParser extends AbstractParser {
   private Set<MediaType> types;
   private Map<String,String> metadata;
   private String xmlText;

   public DummyParser(Set<MediaType> types, Map<String, String> metadata,
         String xmlText) {
      this.types = types;
      this.metadata = metadata;
      this.xmlText = xmlText;
   }

   public Set<MediaType> getSupportedTypes(ParseContext context) {
      return types;
   }

   public void parse(InputStream stream, ContentHandler handler,
         Metadata metadata, ParseContext context) throws IOException,
         SAXException, TikaException {
      for (Entry<String,String> m : this.metadata.entrySet()) {
         metadata.add(m.getKey(), m.getValue());
      }
      
      handler.startDocument();
      if (xmlText != null) {
         handler.characters(xmlText.toCharArray(), 0, xmlText.length());
      }
      handler.endDocument();
   }

}
