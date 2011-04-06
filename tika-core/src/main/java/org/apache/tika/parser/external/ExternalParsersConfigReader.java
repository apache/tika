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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Builds up ExternalParser instances based on XML file(s)
 *  which define what to run, for what, and how to process
 *  any output metadata.
 * Typically used to configure up a series of external programs 
 *  (like catdoc or pdf2txt) to extract text content from documents.
 *  
 * <pre>
 *  TODO XML DTD Here
 * </pre>
 */
public final class ExternalParsersConfigReader implements ExternalParsersConfigReaderMetKeys {
   
   public static List<ExternalParser> read(InputStream stream) throws TikaException, IOException {
      try {
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
          DocumentBuilder builder = factory.newDocumentBuilder();
          Document document = builder.parse(new InputSource(stream));
          return read(document);
      } catch (ParserConfigurationException e) {
          throw new TikaException("Unable to create an XML parser", e);
      } catch (SAXException e) {
          throw new TikaException("Invalid parser configuration", e);
      }
   }
   
   public static List<ExternalParser> read(Document document) throws TikaException, IOException {
      return read(document.getDocumentElement());
   }
   
   public static List<ExternalParser> read(Element element) throws TikaException, IOException {
      List<ExternalParser> parsers = new ArrayList<ExternalParser>();
      
      if (element != null && element.getTagName().equals(EXTERNAL_PARSERS_TAG)) {
         NodeList nodes = element.getChildNodes();
         for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
               Element child = (Element) node;
               if (child.getTagName().equals(PARSER_TAG)) {
                  ExternalParser p = readParser(child);
                  if(p != null) {
                     parsers.add( p );
                  }
               }
            }
         }
      } else {
         throw new MimeTypeException(
               "Not a <" + EXTERNAL_PARSERS_TAG + "/> configuration document: "
               + element.getTagName());
      }
      
      return parsers;
   }
   
   /**
    * Builds and Returns an ExternalParser, or null if a check
    *  command was given that didn't match.
    */
   private static ExternalParser readParser(Element parserDef) throws TikaException {
      ExternalParser parser = new ExternalParser();

      NodeList children = parserDef.getChildNodes();
      for(int i=0; i<children.getLength(); i++) {
         Node node = children.item(i);
         if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element child = (Element) node;
            if (child.getTagName().equals(CHECK_TAG)) {
               boolean present = readCheckTagAndCheck(child);
               if(! present) {
                  return null;
               }
            }
            else if (child.getTagName().equals(COMMAND_TAG)) {
               parser.setCommand( getString(child) );
            }
            else if (child.getTagName().equals(MIMETYPES_TAG)) {
               parser.setSupportedTypes(
                     readMimeTypes(child)
               );
            }
            else if (child.getTagName().equals(METADATA_TAG)) {
               parser.setMetadataExtractionPatterns(
                     readMetadataPatterns(child)
               );
            }
         }
      }
      
      return parser;
   }
   
   private static Set<MediaType> readMimeTypes(Element mimeTypes) {
      Set<MediaType> types = new HashSet<MediaType>();
      
      NodeList children = mimeTypes.getChildNodes();
      for(int i=0; i<children.getLength(); i++) {
         Node node = children.item(i);
         if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element child = (Element) node;
            if (child.getTagName().equals(MIMETYPE_TAG)) {
               types.add( MediaType.parse( getString(child) ) );
            }
         }
      }
      
      return types;
   }
   
   private static Map<Pattern,String> readMetadataPatterns(Element metadataDef) {
      Map<Pattern, String> metadata = new HashMap<Pattern, String>();
      
      NodeList children = metadataDef.getChildNodes();
      for(int i=0; i<children.getLength(); i++) {
         Node node = children.item(i);
         if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element child = (Element) node;
            if (child.getTagName().equals(METADATA_MATCH_TAG)) {
               String metadataKey = child.getAttribute(METADATA_KEY_ATTR);
               Pattern pattern = Pattern.compile( getString(child) );
               metadata.put(pattern, metadataKey);
            }
         }
      }
      
      return metadata;
   }
   
   private static boolean readCheckTagAndCheck(Element checkDef) {
      String command = null;
      List<Integer> errorVals = new ArrayList<Integer>(); 
      
      NodeList children = checkDef.getChildNodes();
      for(int i=0; i<children.getLength(); i++) {
         Node node = children.item(i);
         if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element child = (Element) node;
            if (child.getTagName().equals(COMMAND_TAG)) {
               command = getString(child);
            }
            if (child.getTagName().equals(ERROR_CODES_TAG)) {
               String errs = getString(child);
               StringTokenizer st = new StringTokenizer(errs);
               while(st.hasMoreElements()) {
                  try {
                     String s = st.nextToken();
                     errorVals.add(Integer.parseInt(s));
                  } catch(NumberFormatException e) {}
               }
            }
         }
      }
      
      if(command != null) {
         int[] errVals = new int[errorVals.size()];
         for(int i=0; i<errVals.length; i++) {
            errVals[i] = errorVals.get(i);
         }
         
         return ExternalParser.check(command, errVals);
      }
      
      // No check command, so assume it's there
      return true;
   }
   
   private static String getString(Element element) {
      StringBuffer s = new StringBuffer();
      
      NodeList children = element.getChildNodes();
      for(int i=0; i<children.getLength(); i++) {
         Node node = children.item(i);
         if (node.getNodeType() == Node.TEXT_NODE) {
            s.append( node.getNodeValue() );
         }
      }
      
      return s.toString();
   }
}
