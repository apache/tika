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
package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.hmef.Attachment;
import org.apache.poi.hmef.HMEFMessage;
import org.apache.poi.hmef.attribute.MAPIAttribute;
import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A POI-powered Tika Parser for TNEF (Transport Neutral
 *  Encoding Format) messages, aka winmail.dat
 */
public class TNEFParser extends AbstractParser {
   private static final long serialVersionUID = 4611820730372823452L;
   
   private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
              MediaType.application("vnd.ms-tnef"),
              MediaType.application("ms-tnef"),
              MediaType.application("x-tnef")
         )));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
       
       // We work by recursing, so get the appropriate bits 
       EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);
       EmbeddedDocumentExtractor embeddedExtractor;
       if (ex==null) {
           embeddedExtractor = new ParsingEmbeddedDocumentExtractor(context);
       } else {
           embeddedExtractor = ex;
       }
       
       // Ask POI to process the file for us
       HMEFMessage msg = new HMEFMessage(stream);
       
       // Set the message subject if known
       String subject = msg.getSubject();
       if(subject != null && subject.length() > 0) {
          // TODO: Move to title in Tika 2.0
          metadata.set(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE, subject);
       }
       
       // Recurse into the message body RTF
       MAPIAttribute attr = msg.getMessageMAPIAttribute(MAPIProperty.RTF_COMPRESSED);
       if(attr != null && attr instanceof MAPIRtfAttribute) {
          MAPIRtfAttribute rtf = (MAPIRtfAttribute)attr;
          handleEmbedded(
                "message.rtf", "application/rtf",
                rtf.getData(),
                embeddedExtractor, handler
          );
       }
       
       // Recurse into each attachment in turn
       for(Attachment attachment : msg.getAttachments()) {
          String name = attachment.getLongFilename();
          if(name == null || name.length() == 0) {
             name = attachment.getFilename();
          }
          if(name == null || name.length() == 0) {
             String ext = attachment.getExtension();
             if(ext != null) {
                name = "unknown" + ext;
             }
          }
          handleEmbedded(
                name, null, attachment.getContents(),
                embeddedExtractor, handler
          );
       }
    }
    
    private void handleEmbedded(String name, String type, byte[] contents,
          EmbeddedDocumentExtractor embeddedExtractor, ContentHandler handler)
          throws IOException, SAXException, TikaException {
       Metadata metadata = new Metadata();
       if(name != null)
          metadata.set(Metadata.RESOURCE_NAME_KEY, name);
       if(type != null)
          metadata.set(Metadata.CONTENT_TYPE, type);

       if (embeddedExtractor.shouldParseEmbedded(metadata)) {
         embeddedExtractor.parseEmbedded(
                 TikaInputStream.get(contents),
                 new EmbeddedContentHandler(handler),
                 metadata, false);
       }
    }
}
