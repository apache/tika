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
package org.apache.tika.parser.microsoft.pst;

import static java.lang.String.valueOf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTMessage;
import com.pff.PSTRecipient;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PST;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.parser.microsoft.OutlookExtractor;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

public class PSTMailItemParser implements Parser {

    //this is a synthetic file type to represent a notional "pst item"
    public static final MediaType PST_MAIL_ITEM = MediaType.application("x-tika-pst-mail-item");
    public static final String PST_MAIL_ITEM_STRING = PST_MAIL_ITEM.toString();
    public static final Set<MediaType> SUPPORTED_ITEMS = Set.of(PST_MAIL_ITEM);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_ITEMS;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        TikaInputStream tis = TikaInputStream.cast(stream);
        if (tis == null) {
            throw new TikaException("Stream must be a TikaInputStream");
        }
        Object openContainerObj = tis.getOpenContainer();
        if (openContainerObj == null) {
            throw new TikaException("Open container must not be null.");
        }
        if (! (openContainerObj instanceof PSTMessage)) {
            throw new TikaException("Open container must be a PSTMessage");
        }
        PSTMessage pstMsg = (PSTMessage) openContainerObj;
        EmbeddedDocumentExtractor ex = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        parseMailAndAttachments(pstMsg, xhtml, metadata, context, ex);
        xhtml.endDocument();
    }

    private void parseMailAndAttachments(PSTMessage pstMsg, XHTMLContentHandler handler, Metadata metadata, ParseContext context,
                                         EmbeddedDocumentExtractor embeddedExtractor)
            throws SAXException, IOException, TikaException {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        attributes.addAttribute("", "id", "id", "CDATA", pstMsg.getInternetMessageId());
        handler.startElement("div", attributes);
        handler.element("h1", pstMsg.getSubject());

        parseMailItem(pstMsg, handler, metadata, context);
        parseMailAttachments(pstMsg, handler, metadata, context, embeddedExtractor);
        handler.endElement("div");
    }

    private void parseMailItem(PSTMessage pstMail, XHTMLContentHandler xhtml,
                                Metadata metadata, ParseContext context) throws SAXException, IOException, TikaException {
        extractMetadata(pstMail, metadata);
        //try the html first. It preserves logical paragraph markers
        String htmlChunk = pstMail.getBodyHTML();
        if (! StringUtils.isBlank(htmlChunk)) {
            Parser htmlParser = EmbeddedDocumentUtil
                    .tryToFindExistingLeafParser(JSoupParser.class, context);
            if (htmlParser == null) {
                htmlParser = new JSoupParser();
            }
            if (htmlParser instanceof JSoupParser) {
                ((JSoupParser)htmlParser).parseString(htmlChunk,
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                        metadata, context);
            } else {
                byte[] data = htmlChunk.getBytes(StandardCharsets.UTF_8);
                htmlParser.parse(new UnsynchronizedByteArrayInputStream(data),
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)), new Metadata(), context);
            }
            return;
        }
        //if there's no html, back off to straight text -- TODO maybe add RTF parsing?
        //splitting on "\r\n|\n" doesn't work because the new lines in the
        //body are not logical new lines...they are presentation new lines.
        String mailContent = pstMail.getBody();
        xhtml.startElement("p");
        xhtml.characters(mailContent);
        xhtml.endElement("p");
    }

    private void extractMetadata(PSTMessage pstMail, Metadata metadata) {
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, pstMail.getSubject() + ".msg");
        metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, pstMail.getInternetMessageId());
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name());
        metadata.set(TikaCoreProperties.IDENTIFIER, pstMail.getInternetMessageId());
        metadata.set(TikaCoreProperties.TITLE, pstMail.getSubject());
        metadata.set(TikaCoreProperties.SUBJECT, pstMail.getSubject());
        metadata.set(Metadata.MESSAGE_FROM, pstMail.getSenderName());
        metadata.set(TikaCoreProperties.CREATOR, pstMail.getSenderName());
        metadata.set(TikaCoreProperties.CREATED, pstMail.getCreationTime());
        metadata.set(Office.MAPI_MESSAGE_CLIENT_SUBMIT_TIME, pstMail.getClientSubmitTime());
        metadata.set(TikaCoreProperties.MODIFIED, pstMail.getLastModificationTime());
        metadata.set(TikaCoreProperties.COMMENTS, pstMail.getComment());
        metadata.set(PST.DESCRIPTOR_NODE_ID, valueOf(pstMail.getDescriptorNodeId()));
        metadata.set(Message.MESSAGE_FROM_EMAIL, pstMail.getSenderEmailAddress());
        if (! StringUtils.isBlank(pstMail.getRecipientsString()) &&
                ! pstMail.getRecipientsString().equals("No recipients table!")) {
            metadata.set(Office.MAPI_RECIPIENTS_STRING, pstMail.getRecipientsString());
        }
        metadata.set(Message.MESSAGE_TO_DISPLAY_NAME, pstMail.getDisplayTo());
        metadata.set(Message.MESSAGE_CC_DISPLAY_NAME, pstMail.getDisplayCC());
        metadata.set(Message.MESSAGE_BCC_DISPLAY_NAME, pstMail.getDisplayBCC());
        metadata.set(Office.MAPI_IMPORTANCE, pstMail.getImportance());
        metadata.set(Office.MAPI_PRIORTY, pstMail.getPriority());
        metadata.set(Office.MAPI_IS_FLAGGED, pstMail.isFlagged());
        metadata.set(Office.MAPI_MESSAGE_CLASS,
                OutlookExtractor.getMessageClass(pstMail.getMessageClass()));

        metadata.set(Message.MESSAGE_FROM_EMAIL, pstMail.getSenderEmailAddress());

        metadata.set(Office.MAPI_FROM_REPRESENTING_EMAIL,
                pstMail.getSentRepresentingEmailAddress());

        metadata.set(Message.MESSAGE_FROM_NAME, pstMail.getSenderName());
        metadata.set(Office.MAPI_FROM_REPRESENTING_NAME, pstMail.getSentRepresentingName());

        //add recipient details
        try {
            for (int i = 0; i < pstMail.getNumberOfRecipients(); i++) {
                PSTRecipient recipient = pstMail.getRecipient(i);
                switch (OutlookExtractor.RECIPIENT_TYPE
                        .getTypeFromVal(recipient.getRecipientType())) {
                    case TO:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_TO_DISPLAY_NAME,
                                recipient.getDisplayName(), metadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_TO_EMAIL,
                                recipient.getEmailAddress(), metadata);
                        break;
                    case CC:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_CC_DISPLAY_NAME,
                                recipient.getDisplayName(), metadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_CC_EMAIL,
                                recipient.getEmailAddress(), metadata);
                        break;
                    case BCC:
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_BCC_DISPLAY_NAME,
                                recipient.getDisplayName(), metadata);
                        OutlookExtractor.addEvenIfNull(Message.MESSAGE_BCC_EMAIL,
                                recipient.getEmailAddress(), metadata);
                        break;
                    default:
                        //do we want to handle unspecified or unknown?
                        break;
                }
            }
        } catch (IOException | PSTException e) {
            //swallow
        }

    }

    private void parseMailAttachments(PSTMessage email, XHTMLContentHandler xhtml,
                                      Metadata metadata, ParseContext context,
                                      EmbeddedDocumentExtractor embeddedExtractor)
            throws TikaException {
        int numberOfAttachments = email.getNumberOfAttachments();
        for (int i = 0; i < numberOfAttachments; i++) {
            try {
                PSTAttachment attachment = email.getAttachment(i);
                parseMailAttachment(xhtml, attachment, metadata, embeddedExtractor);
            } catch (Exception e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            }
        }
    }

    private void parseMailAttachment(XHTMLContentHandler xhtml, PSTAttachment attachment, Metadata metadata,
                                     EmbeddedDocumentExtractor embeddedExtractor) throws PSTException, IOException,
            TikaException, SAXException {

        PSTMessage attachedEmail = attachment.getEmbeddedPSTMessage();
        //check for whether this is a binary attachment or an embedded pst msg
        if (attachedEmail != null) {
            try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
                tis.setOpenContainer(attachedEmail);
                Metadata attachMetadata = new Metadata();
                attachMetadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, PSTMailItemParser.PST_MAIL_ITEM_STRING);
                attachMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, attachedEmail.getSubject() + ".msg");
                attachMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name());
                embeddedExtractor.parseEmbedded(tis, xhtml, attachMetadata, true);
            }
            return;
        }

        // Get the filename; both long and short filenames can be used for attachments
        String filename = attachment.getLongFilename();
        if (filename.isEmpty()) {
            filename = attachment.getFilename();
        }

        xhtml.element("p", filename);

        Metadata attachMeta = new Metadata();
        attachMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        attachMeta.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, filename);
        attachMeta.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        attributes.addAttribute("", "id", "id", "CDATA", filename);
        xhtml.startElement("div", attributes);
        if (embeddedExtractor.shouldParseEmbedded(attachMeta)) {
            TikaInputStream tis = null;
            try {
                tis = TikaInputStream.get(attachment.getFileInputStream());
            } catch (NullPointerException e) { //TIKA-2488
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                return;
            }

            try {
                embeddedExtractor.parseEmbedded(tis, xhtml, attachMeta, false);
            } finally {
                tis.close();
            }
        }
        xhtml.endElement("div");
    }
}
