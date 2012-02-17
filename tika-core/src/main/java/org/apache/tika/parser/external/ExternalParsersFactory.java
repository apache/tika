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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

/**
 * Creates instances of ExternalParser based on XML 
 *  configuration files.
 *  
 * @see ExternalParsersConfigReader
 */
public class ExternalParsersFactory {
   
   public static List<ExternalParser> create() throws IOException, TikaException {
      return create(new ServiceLoader());
   }
   
   public static List<ExternalParser> create(ServiceLoader loader) 
           throws IOException, TikaException {
      return create("tika-external-parsers.xml", loader);
   }
   
   public static List<ExternalParser> create(String filename, ServiceLoader loader) 
           throws IOException, TikaException {
      String filepath = ExternalParsersFactory.class.getPackage().getName().replace('.', '/') +
                     "/" + filename;
      Enumeration<URL> files = loader.findServiceResources(filepath);
      ArrayList<URL> list = Collections.list(files);
      URL[] urls = list.toArray(new URL[list.size()]);
      return create(urls);
   }
   
   public static List<ExternalParser> create(URL... urls) throws IOException, TikaException {
      List<ExternalParser> parsers = new ArrayList<ExternalParser>();
      for(URL url : urls) {
         InputStream stream = url.openStream();
         try {
            parsers.addAll(
                  ExternalParsersConfigReader.read(stream)
            );
         } finally {
            stream.close();
         }
      }
      return parsers;
   }
   
   public static void attachExternalParsers(TikaConfig config) throws IOException, TikaException {
      attachExternalParsers( create(), config );
   }
   
   public static void attachExternalParsers(List<ExternalParser> parsers, TikaConfig config) {
      Parser parser = config.getParser();
      if (parser instanceof CompositeParser) {
         CompositeParser cParser = (CompositeParser)parser;
         Map<MediaType,Parser> parserMap = cParser.getParsers();
      }
      // TODO
   }
}
