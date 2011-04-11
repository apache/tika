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

import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.rtf.RTFParser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Outlook Message Parser.
 */
public class OutlookExtractor extends AbstractPOIFSExtractor {
    private final MAPIMessage msg;

    public OutlookExtractor(NPOIFSFileSystem filesystem, ParseContext context) throws TikaException {
        super(context);
        
        try {
            this.msg = new MAPIMessage(filesystem);
        } catch (IOException e) {
            throw new TikaException("Failed to parse Outlook message", e);
        }
    }

    public void parse(XHTMLContentHandler xhtml, Metadata metadata)
            throws TikaException, SAXException, IOException {
        try {
           msg.setReturnNullOnMissingChunk(true);
           
           // If the message contains strings that aren't stored
           //  as Unicode, try to sort out an encoding for them
           // TODO Use new method
           boolean hasNonUnicodeStrings = false;
           for(Chunk chunk : msg.getMainChunks().getAll()) {
              if(chunk instanceof StringChunk) {
                 StringChunk sc = (StringChunk)chunk;
                 if(sc.getType() == Types.ASCII_STRING) {
                    hasNonUnicodeStrings = true;
                    break;
                 }
              }
           }
           
           if(hasNonUnicodeStrings) {
              if(msg.getHeaders() != null) {
                 // There's normally something in the headers
                 msg.guess7BitEncoding();
              } else {
                 // Nothing in the header, try encoding detection
                 //  on the message body
                 CharsetDetector detector = new CharsetDetector();
                 // TODO detect and use this
              }
           }
           
           // Start with the metadata
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
           if (from!=null) {
               header(xhtml, "From", from);
           }
           header(xhtml, "To", msg.getDisplayTo());
           header(xhtml, "Cc", msg.getDisplayCC());
           header(xhtml, "Bcc", msg.getDisplayBCC());
           try {
               header(xhtml, "Recipients", msg.getRecipientEmailAddress());
           } catch(ChunkNotFoundException e) {}
           xhtml.endElement("dl");
   
           // Get the message body. Preference order is: html, rtf, text
           Chunk htmlChunk = null;
           Chunk rtfChunk = null;
           Chunk textChunk = null;
           for(Chunk chunk : msg.getMainChunks().getAll()) {
              if(chunk.getChunkId() == MAPIProperty.BODY_HTML.id) {
                 htmlChunk = chunk;
              }
              if(chunk.getChunkId() == MAPIProperty.RTF_COMPRESSED.id) {
                 rtfChunk = chunk;
              }
              if(chunk.getChunkId() == MAPIProperty.BODY.id) {
                 textChunk = chunk;
              }
           }
           
           boolean doneBody = false;
           if(htmlChunk != null) {
              byte[] data = null;
              if(htmlChunk instanceof ByteChunk) {
                 data = ((ByteChunk)htmlChunk).getValue();
              } else if(htmlChunk instanceof StringChunk) {
                 // TODO Needs POI 3.8 beta 3
              }
              if(data != null) {
                 HtmlParser htmlParser = new HtmlParser();
                 htmlParser.parse(
                       new ByteArrayInputStream(data),
                       xhtml, new Metadata(), new ParseContext()
                 );
                 doneBody = true;
              }
           }
           if(rtfChunk != null && !doneBody) {
              ByteChunk chunk = (ByteChunk)rtfChunk;
              MAPIRtfAttribute rtf = new MAPIRtfAttribute(
                    MAPIProperty.RTF_COMPRESSED, Types.BINARY, chunk.getValue()
              );
              RTFParser rtfParser = new RTFParser();
              // Disabled pending a fix to TIKA-632
//              rtfParser.parse(
//                    new ByteArrayInputStream(rtf.getData()),
//                    xhtml, new Metadata(), new ParseContext()
//              );
//              doneBody = true;
           }
           if(textChunk != null && !doneBody) {
              xhtml.element("p", ((StringChunk)textChunk).getValue());
           }
           
           // Process the attachments
           for (AttachmentChunks attachment : msg.getAttachmentFiles()) {
               xhtml.startElement("div", "class", "attachment-entry");
               
               String filename = null;
               if (attachment.attachLongFileName != null) {
                  filename = attachment.attachLongFileName.getValue();
               } else if (attachment.attachFileName != null) {
                  filename = attachment.attachFileName.getValue();
               }
               if (filename != null && filename.length() > 0) {
                   xhtml.element("h1", filename);
               }
               
               if(attachment.attachData != null) {
                  handleEmbeddedResource(
                        TikaInputStream.get(attachment.attachData.getValue()),
                        filename,
                        null, xhtml, true
                  );
               }
               if(attachment.attachmentDirectory != null) {
                  handleEmbededOfficeDoc(
                        attachment.attachmentDirectory.getDirectory(),
                        xhtml
                  );
               }

               xhtml.endElement("div");
               
           }
        } catch(ChunkNotFoundException e) {
           throw new TikaException("POI MAPIMessage broken - didn't return null on missing chunk", e);
        }
    }

    private void header(XHTMLContentHandler xhtml, String key, String value)
            throws SAXException {
        if (value != null && value.length() > 0) {
            xhtml.element("dt", key);
            xhtml.element("dd", value);
        }
    }
}
