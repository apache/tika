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
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.pff.PSTAttachment;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;

/**
 * @author Tran Nam Quang
 * @author hong-thai.nguyen
 *
 */
public class OutlookPSTParser extends AbstractParser {


    private static final long serialVersionUID = 620998217748364063L;

    private static final MediaType MS_OUTLOOK_PST_MIMETYPE = MediaType.application("vnd.ms-outlook-pst");
    private static final Set<MediaType> SUPPORTED_TYPES = singleton(MS_OUTLOOK_PST_MIMETYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor embeddedExtractor = context.get(
                EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        metadata.set(Metadata.CONTENT_TYPE, MS_OUTLOOK_PST_MIMETYPE.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TikaInputStream in = TikaInputStream.get(stream);
        try {
            PSTFile pstFile = new PSTFile(in.getFile().getPath());
            metadata.set(Metadata.CONTENT_LENGTH, valueOf(pstFile.getFileHandle().length()));
            boolean isValid = pstFile.getFileHandle().getFD().valid();
            metadata.set("isValid", valueOf(isValid));
            if (isValid) {
                parseFolder(xhtml, pstFile.getRootFolder(), embeddedExtractor);
            }
        } catch (Exception e) {
            throw new TikaException(e.getMessage());
        }

        xhtml.endDocument();
    }

    private static void parseFolder(XHTMLContentHandler xhtml, PSTFolder pstFolder, EmbeddedDocumentExtractor embeddedExtractor) throws Exception {
        if (pstFolder.getContentCount() > 0) {
            PSTMessage pstMail = (PSTMessage) pstFolder.getNextChild();
            while (pstMail != null) {
                xhtml.startElement("div", createAttribute("class", "email-entry"));
                xhtml.element("h1", pstMail.getInternetMessageId());
                createMetadata(xhtml, "subject", pstMail.getSubject());
                createMetadata(xhtml, "internetMessageId", valueOf(pstMail.getInternetMessageId()));
                createMetadata(xhtml, "descriptorNodeId", valueOf(pstMail.getDescriptorNodeId()));
                createMetadata(xhtml, "lastModificationTime", valueOf(pstMail.getLastModificationTime().getTime()));
                createMetadata(xhtml, "senderName", pstMail.getSenderName());
                createMetadata(xhtml, "senderEmailAddress", pstMail.getSenderEmailAddress());
                createMetadata(xhtml, "recipients", pstMail.getRecipientsString());

                xhtml.element("p", pstMail.getBody());
                parseAttachments(xhtml, pstMail, embeddedExtractor);

                xhtml.endElement("div");
                pstMail = (PSTMessage) pstFolder.getNextChild();
            }
        }

        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                xhtml.startElement("div", createAttribute("class", "email-folder"));
                xhtml.element("h1", pstSubFolder.getDisplayName());
                parseFolder(xhtml, pstSubFolder, embeddedExtractor);
                xhtml.endElement("div");
            }
        }
    }

    private static void createMetadata(XHTMLContentHandler xhtml, String metaName, String metaValue) throws SAXException {
        xhtml.startElement("meta", createAttribute(metaName, metaValue));
        xhtml.endElement("meta");
    }

    private static AttributesImpl createAttribute(String attName, String attValue) {
        AttributesImpl attributes  = new AttributesImpl();
        attributes.addAttribute("", attName, attName, "CDATA", attValue);
        return attributes;
    }

    private static void parseAttachments(XHTMLContentHandler xhtml, PSTMessage email, EmbeddedDocumentExtractor embeddedExtractor) throws TikaException {
        int numberOfAttachments = email.getNumberOfAttachments();
        for (int i = 0; i < numberOfAttachments; i++) {
            File tempFile = null;
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
                    TemporaryResources tmp = new TemporaryResources();
                    try {
                        TikaInputStream tis = TikaInputStream.get(attach.getFileInputStream(), tmp);
                        embeddedExtractor.parseEmbedded(tis, xhtml, attachMeta, true);
                    } finally {
                        tmp.dispose();
                    }
                }
                xhtml.endElement("div");

            } catch (Exception e) {
                throw new TikaException("Unable to unpack document stream", e);
            } finally {
                if (tempFile != null)
                    tempFile.delete();
            }
        }
    }

}
