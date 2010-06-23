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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Outlook Message Parser.
 */
public class OutlookExtractor {
    private final MAPIMessage msg;
    private final ParseContext context;

    public OutlookExtractor(POIFSFileSystem filesystem, ParseContext context) throws TikaException {
        try {
            this.msg = new MAPIMessage(filesystem);
            this.context = context;
        } catch (IOException e) {
            throw new TikaException("Failed to parse Outlook message", e);
        }
    }

    public void parse(XHTMLContentHandler xhtml, Metadata metadata)
            throws TikaException, SAXException {
        try {
           msg.setReturnNullOnMissingChunk(true);
          
           String subject = msg.getSubject();
           String from = msg.getDisplayFrom();
   
           metadata.set(Metadata.AUTHOR, from);
           metadata.set(Metadata.MESSAGE_FROM, from);
           metadata.set(Metadata.MESSAGE_TO, msg.getDisplayTo());
           metadata.set(Metadata.MESSAGE_CC, msg.getDisplayCC());
           metadata.set(Metadata.MESSAGE_BCC, msg.getDisplayBCC());
           
           metadata.set(Metadata.TITLE, subject);
           metadata.set(Metadata.SUBJECT, msg.getConversationTopic());
           
           try {
           for(String recipientAddress : msg.getRecipientEmailAddressList()) {
               if(recipientAddress != null)
        	   metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, recipientAddress);
           }
           } catch(ChunkNotFoundException he) {} // Will be fixed in POI 3.7 Final
           
           // Date - try two ways to find it
           // First try via the proper chunk
           if(msg.getMessageDate() != null) {
              metadata.set(Metadata.EDIT_TIME, msg.getMessageDate().getTime().toString());
              metadata.set(Metadata.LAST_SAVED, msg.getMessageDate().getTime().toString());
           } else {
              try {
                 // Failing that try via the raw headers 
                 String[] headers = msg.getHeaders();
                 if(headers != null && headers.length > 0) {
                     for(String header: headers) {
                        if(header.toLowerCase().startsWith("date:")) {
                    	String date = header.substring(header.indexOf(':')+1);
                            metadata.set(Metadata.EDIT_TIME, date);
                            metadata.set(Metadata.LAST_SAVED, date);
                            break;
                        }
                     }
                 }
              } catch(ChunkNotFoundException he) {
                 // We can't find the date, sorry...
              }
           }
           
   
           xhtml.element("h1", subject);
   
           // Output the from and to details in text, as you
           //  often want them in text form for searching
           xhtml.startElement("dl");
           header(xhtml, "From", from);
           header(xhtml, "To", msg.getDisplayTo());
           header(xhtml, "Cc", msg.getDisplayCC());
           header(xhtml, "Bcc", msg.getDisplayBCC());
           try {
               header(xhtml, "Recipients", msg.getRecipientEmailAddress());
           } catch(ChunkNotFoundException e) {}
           xhtml.endElement("dl");
   
           xhtml.element("p", msg.getTextBody());
           
           for (AttachmentChunks attachment : msg.getAttachmentFiles()) {
               xhtml.startElement("div", "class", "attachment-entry");
               Metadata entrydata = new Metadata();
               
               String filename = null;
               if (attachment.attachLongFileName != null) {
        	   filename = attachment.attachLongFileName.getValue();
               } else if (attachment.attachFileName != null) {
        	   filename = attachment.attachFileName.getValue();
               }
               if (filename != null && filename.length() > 0) {
                   entrydata.set(Metadata.RESOURCE_NAME_KEY, filename);
                   xhtml.element("h1", filename);
               }
               
               try {
                   // Use the delegate parser to parse this entry
                   context.get(Parser.class, EmptyParser.INSTANCE).parse(
                	   new ByteArrayInputStream(attachment.attachData.getValue()),
                           new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                           entrydata,
                           context
                   );
               } catch (Exception e) {
                   // Could not parse the entry, just skip the content
               }
               xhtml.endElement("div");
               
           }
        } catch(ChunkNotFoundException e) {
           throw new TikaException("POI MAPIMessage broken - didn't return null on missing chunk", e);
        }
    }

    private void header(XHTMLContentHandler xhtml, String key, String value)
            throws SAXException {
        if (value.length() > 0) {
            xhtml.element("dt", key);
            xhtml.element("dd", value);
        }
    }
}
