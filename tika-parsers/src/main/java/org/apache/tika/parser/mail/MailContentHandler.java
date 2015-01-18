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
package org.apache.tika.parser.mail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.dom.field.MailboxListField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Bridge between mime4j's content handler and the generic Sax content handler
 * used by Tika. See
 * http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/ContentHandler.html
 */
class MailContentHandler implements ContentHandler {

    private boolean strictParsing = false;

    private XHTMLContentHandler handler;
    private Metadata metadata;
    private EmbeddedDocumentExtractor extractor;

    private boolean inPart = false;
    
    MailContentHandler(XHTMLContentHandler xhtml, Metadata metadata, ParseContext context, boolean strictParsing) {
        this.handler = xhtml;
        this.metadata = metadata;
        this.strictParsing = strictParsing;
        
        // Fetch / Build an EmbeddedDocumentExtractor with which
        //  to handle/process the parts/attachments
        
        // Was an EmbeddedDocumentExtractor explicitly supplied?
        this.extractor = context.get(EmbeddedDocumentExtractor.class);
        
        // If there's no EmbeddedDocumentExtractor, then try using a normal parser
        // This will ensure that the contents are made available to the user, so
        //  the see the text, but without fine-grained control/extraction
        // (This also maintains backward compatibility with older versions!)
        if (this.extractor == null) {
            // If the user gave a parser, use that, if not the default
            Parser parser = context.get(AutoDetectParser.class);
            if (parser == null) {
               parser = context.get(Parser.class);
            }
            if (parser == null) {
                TikaConfig tikaConfig = context.get(TikaConfig.class);
                if (tikaConfig == null) {
                    tikaConfig = TikaConfig.getDefaultConfig();
                }
                parser = new AutoDetectParser(tikaConfig.getParser());
            }
            ParseContext ctx = new ParseContext();
            ctx.set(Parser.class, parser);
            extractor = new ParsingEmbeddedDocumentExtractor(ctx);
        }
    }

    public void body(BodyDescriptor body, InputStream is) throws MimeException,
            IOException {
        // use a different metadata object
        // in order to specify the mime type of the
        // sub part without damaging the main metadata

        Metadata submd = new Metadata();
        submd.set(Metadata.CONTENT_TYPE, body.getMimeType());
        submd.set(Metadata.CONTENT_ENCODING, body.getCharset());

        try {
            if (extractor.shouldParseEmbedded(submd)) {
                extractor.parseEmbedded(is, handler, submd, false);
            }
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endBodyPart() throws MimeException {
        try {
            handler.endElement("p");
            handler.endElement("div");
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endHeader() throws MimeException {
    }

    public void startMessage() throws MimeException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endMessage() throws MimeException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endMultipart() throws MimeException {
        inPart = false;
    }

    public void epilogue(InputStream is) throws MimeException, IOException {
    }

    /**
     * Header for the whole message or its parts
     * 
     * @see http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/
     *      Field.html
     **/
    public void field(Field field) throws MimeException {
        // inPart indicates whether these metadata correspond to the
        // whole message or its parts
        if (inPart) {
            return;
        }

        try {
            String fieldname = field.getName();
            ParsedField parsedField = LenientFieldParser.getParser().parse(
                    field, DecodeMonitor.SILENT);
            if (fieldname.equalsIgnoreCase("From")) {
                MailboxListField fromField = (MailboxListField) parsedField;
                MailboxList mailboxList = fromField.getMailboxList();
                if (fromField.isValidField() && mailboxList != null) {
                    for (Address address : mailboxList) {
                        String from = getDisplayString(address);
                        metadata.add(Metadata.MESSAGE_FROM, from);
                        metadata.add(TikaCoreProperties.CREATOR, from);
                    }
                } else {
                    String from = stripOutFieldPrefix(field, "From:");
                    if (from.startsWith("<")) {
                        from = from.substring(1);
                    }
                    if (from.endsWith(">")) {
                        from = from.substring(0, from.length() - 1);
                    }
                    metadata.add(Metadata.MESSAGE_FROM, from);
                    metadata.add(TikaCoreProperties.CREATOR, from);
                }
            } else if (fieldname.equalsIgnoreCase("Subject")) {
                metadata.add(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE,
                        ((UnstructuredField) parsedField).getValue());
            } else if (fieldname.equalsIgnoreCase("To")) {
                processAddressList(parsedField, "To:", Metadata.MESSAGE_TO);
            } else if (fieldname.equalsIgnoreCase("CC")) {
                processAddressList(parsedField, "Cc:", Metadata.MESSAGE_CC);
            } else if (fieldname.equalsIgnoreCase("BCC")) {
                processAddressList(parsedField, "Bcc:", Metadata.MESSAGE_BCC);
            } else if (fieldname.equalsIgnoreCase("Date")) {
                DateTimeField dateField = (DateTimeField) parsedField;
                metadata.set(TikaCoreProperties.CREATED, dateField.getDate());
            }
        } catch (RuntimeException me) {
            if (strictParsing) {
                throw me;
            }
        }
    }

    private void processAddressList(ParsedField field, String addressListType,
            String metadataField) throws MimeException {
        AddressListField toField = (AddressListField) field;
        if (toField.isValidField()) {
            AddressList addressList = toField.getAddressList();
            for (Address address : addressList) {
                metadata.add(metadataField, getDisplayString(address));
            }
        } else {
            String to = stripOutFieldPrefix(field,
                    addressListType);
            for (String eachTo : to.split(",")) {
                metadata.add(metadataField, eachTo.trim());
            }
        }
    }

    private String getDisplayString(Address address) {
        if (address instanceof Mailbox) {
            Mailbox mailbox = (Mailbox) address;
            String name = mailbox.getName();
            if (name != null && name.length() > 0) {
                name = DecoderUtil.decodeEncodedWords(name, DecodeMonitor.SILENT);
                return name + " <" + mailbox.getAddress() + ">";
            } else {
                return mailbox.getAddress();
            }
        } else {
            return address.toString();
        }
    }

    public void preamble(InputStream is) throws MimeException, IOException {
    }

    public void raw(InputStream is) throws MimeException, IOException {
    }

    public void startBodyPart() throws MimeException {
        try {
            handler.startElement("div", "class", "email-entry");
            handler.startElement("p");
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void startHeader() throws MimeException {
        // TODO Auto-generated method stub

    }

    public void startMultipart(BodyDescriptor descr) throws MimeException {
        inPart = true;
    }

    private String stripOutFieldPrefix(Field field, String fieldname) {
        String temp = field.getRaw().toString();
        int loc = fieldname.length();
        while (temp.charAt(loc) ==' ') {
            loc++;
        }
        return temp.substring(loc);
    }

}
