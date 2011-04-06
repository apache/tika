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
package org.apache.tika.parser.external;

import java.io.IOException;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

/**
 * A Composite Parser that wraps up all the available External Parsers,
 *  and provides an easy way to access them.
 * Parser that uses an external program (like catdoc or pdf2txt) to extract
 *  text content and metadata from a given document.
 */
public class CompositeExternalParser extends CompositeParser {
   private static final long serialVersionUID = 6962436916649024024L;

   public CompositeExternalParser() throws IOException, TikaException {
      this(new MediaTypeRegistry());
   }
   
   @SuppressWarnings("unchecked")
   public CompositeExternalParser(MediaTypeRegistry registry)  throws IOException, TikaException {
      super(
            registry, 
            (List<Parser>)(List<? extends Parser>)ExternalParsersFactory.create()
      );
   }
}
