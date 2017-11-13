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
package org.apache.tika.parser.mbox;

import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTRecipient;
import org.apache.poi.ss.formula.functions.T;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OutlookExtractor;
import org.apache.tika.parser.rtf.RTFParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Parser for MS Outlook PST email storage files
 */
public class OutlookPSTParser extends AbstractParser {

    private static final long serialVersionUID = 620998217748364063L;

    public static final MediaType MS_OUTLOOK_PST_MIMETYPE = MediaType.application("vnd.ms-outlook-pst");
    private static final Set<MediaType> SUPPORTED_TYPES = singleton(MS_OUTLOOK_PST_MIMETYPE);

    private static AttributesImpl createAttribute(String attName, String attValue) {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", attName, attName, "CDATA", attValue);
        return attributes;
    }


    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor embeddedExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        metadata.set(Metadata.CONTENT_TYPE, MS_OUTLOOK_PST_MIMETYPE.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TikaInputStream in = TikaInputStream.get(stream);
        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(in.getFile().getPath());
            metadata.set(Metadata.CONTENT_LENGTH, valueOf(pstFile.getFileHandle().length()));
            boolean isValid = pstFile.getFileHandle().getFD().valid();
            metadata.set("isValid", valueOf(isValid));
            if (isValid) {
                parseFolder(xhtml, pstFile.getRootFolder(), embeddedExtractor);
            }
        } catch (Exception e) {
            throw new TikaException(e.getMessage(), e);
        } finally {
            if (pstFile != null && pstFile.getFileHandle() != null) {
                try {
                    pstFile.getFileHandle().close();
                } catch (IOException e) {
                    //swallow closing exception
                }
            }
        }

        xhtml.endDocument();
    }

    private void parseFolder(XHTMLContentHandler handler, PSTFolder pstFolder, EmbeddedDocumentExtractor embeddedExtractor)
            throws Exception {
        if (pstFolder.getContentCount() > 0) {
            PSTMessage pstMail = (PSTMessage) pstFolder.getNextChild();
            while (pstMail != null) {
                AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                attributes.addAttribute("", "id", "id", "CDATA", pstMail.getInternetMessageId());
                handler.startElement("div", attributes);
                handler.element("h1", pstMail.getSubject());

                final Metadata mailMetadata = new Metadata();
                //parse attachments first so that stream exceptions
                //in attachments can make it into mailMetadata.
                //RecursiveParserWrapper copies the metadata and thereby prevents
                //modifications to mailMetadata from making it into the
                //metadata objects cached by the RecursiveParserWrapper
                parseMailAttachments(handler, pstMail, mailMetadata, embeddedExtractor);
                parserMailItem(handler, pstMail, mailMetadata, embeddedExtractor);

                handler.endElement("div");

                pstMail = (PSTMessage) pstFolder.getNextChild();
            }
        }

        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                handler.startElement("div", createAttribute("class", "email-folder"));
                handler.element("h1", pstSubFolder.getDisplayName());
                parseFolder(handler, pstSubFolder, embeddedExtractor);
                handler.endElement("div");
            }
        }
    }

    private void parserMailItem(XHTMLContentHandler handler, PSTMessage pstMail, Metadata mailMetadata,
                                EmbeddedDocumentExtractor embeddedExtractor) throws SAXException, IOException {
        mailMetadata.set(Metadata.RESOURCE_NAME_KEY, pstMail.getInternetMessageId());
        mailMetadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, pstMail.getInternetMessageId());
        mailMetadata.set(TikaCoreProperties.IDENTIFIER, pstMail.getInternetMessageId());
        mailMetadata.set(TikaCoreProperties.TITLE, pstMail.getSubject());
        mailMetadata.set(Metadata.MESSAGE_FROM, pstMail.getSenderName());
        mailMetadata.set(TikaCoreProperties.CREATOR, pstMail.getSenderName());
        mailMetadata.set(TikaCoreProperties.CREATED, pstMail.getCreationTime());
        mailMetadata.set(TikaCoreProperties.MODIFIED, pstMail.getLastModificationTime());
        mailMetadata.set(TikaCoreProperties.COMMENTS, pstMail.getComment());
        mailMetadata.set("descriptorNodeId", valueOf(pstMail.getDescriptorNodeId()));
        mailMetadata.set("senderEmailAddress", pstMail.getSenderEmailAddress());
        mailMetadata.set("recipients", pstMail.getRecipientsString());
        mailMetadata.set("displayTo", pstMail.getDisplayTo());
        mailMetadata.set("displayCC", pstMail.getDisplayCC());
        mailMetadata.set("displayBCC", pstMail.getDisplayBCC());
        mailMetadata.set("importance", valueOf(pstMail.getImportance()));
        mailMetadata.set("priority", valueOf(pstMail.getPriority()));
        mailMetadata.set("flagged", valueOf(pstMail.isFlagged()));
        mailMetadata.set(Office.MAPI_MESSAGE_CLASS,
                OutlookExtractor.getMessageClass(pstMail.getMessageClass()));

        mailMetadata.set(Message.MESSAGE_FROM_EMAIL, pstMail.getSenderEmailAddress());

        mailMetadata.set(Office.MAPI_FROM_REPRESENTING_EMAIL,
                pstMail.getSentRepresentingEmailAddress());

        mailMetadata.set(Message.MESSAGE_FROM_NAME, pstMail.getSenderName());
        mailMetadata.set(Office.MAPI_FROM_REPRESENTING_NAME, pstMail.getSentRepresentingName());

        //add recipient details
        try {
            for (int i = 0; i < pstMail.getNumberOfRecipients(); i++) {
                PSTRecipient recipient = pstMail.getRecipient(i);
                switch (OutlookExtractor.RECIPIENT_TYPE.getTypeFromVal(recipient.getRecipientType())) {
                    case TO:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_TO_DISPLAY_NAME,
                                recipient.getDisplayName(), mailMetadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_TO_EMAIL,
                                recipient.getEmailAddress(), mailMetadata);
                        break;
                    case CC:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_CC_DISPLAY_NAME,
                                recipient.getDisplayName(), mailMetadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_CC_EMAIL,
                                recipient.getEmailAddress(), mailMetadata);
                        break;
                    case BCC:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_BCC_DISPLAY_NAME,
                                recipient.getDisplayName(), mailMetadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_BCC_EMAIL,
                                recipient.getEmailAddress(), mailMetadata);
                        break;
                    default:
                        //do we want to handle unspecified or unknown?
                        break;
                }
            }
        } catch (PSTException e) {
            //swallow
        }
        //we may want to experiment with working with the bodyHTML.
        //However, because we can't get the raw bytes, we _could_ wind up sending
        //a UTF-8 byte representation of the html that has a conflicting metaheader
        //that causes the HTMLParser to get the encoding wrong.  Better if we could get
        //the underlying bytes from the pstMail object...

        byte[] mailContent = pstMail.getBody().getBytes(UTF_8);
        mailMetadata.set(TikaCoreProperties.CONTENT_TYPE_OVERRIDE, MediaType.TEXT_PLAIN.toString());
        embeddedExtractor.parseEmbedded(new ByteArrayInputStream(mailContent), handler, mailMetadata, true);
    }

    private void parseMailAttachments(XHTMLContentHandler xhtml, PSTMessage email,
                                     final Metadata mailMetadata,
                                      EmbeddedDocumentExtractor embeddedExtractor)
            throws TikaException {
        int numberOfAttachments = email.getNumberOfAttachments();
        for (int i = 0; i < numberOfAttachments; i++) {
            try {
                PSTAttachment attach = email.getAttachment(i);

                // Get the filename; both long and short filenames can be used for attachments
                String filename = attach.getLongFilename();
                if (filename.isEmpty()) {
                    filename = attach.getFilename();
                }

                xhtml.element("p", filename);

                Metadata attachMeta = new Metadata();
                attachMeta.set(Metadata.RESOURCE_NAME_KEY, filename);
                attachMeta.set(Metadata.EMBEDDED_RELATIONSHIP_ID, filename);
                AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                attributes.addAttribute("", "id", "id", "CDATA", filename);
                xhtml.startElement("div", attributes);
                if (embeddedExtractor.shouldParseEmbedded(attachMeta)) {
                    TikaInputStream tis = null;
                    try {
                        tis = TikaInputStream.get(attach.getFileInputStream());
                    } catch (NullPointerException e) {//TIKA-2488
                        EmbeddedDocumentUtil.recordEmbeddedStreamException(e, mailMetadata);
                        continue;
                    }

                    try {
                        embeddedExtractor.parseEmbedded(tis, xhtml, attachMeta, true);
                    } finally {

                        tis.close();
                    }
                }
                xhtml.endElement("div");

            } catch (Exception e) {
                throw new TikaException("Unable to unpack document stream", e);
            }
        }
    }

}
