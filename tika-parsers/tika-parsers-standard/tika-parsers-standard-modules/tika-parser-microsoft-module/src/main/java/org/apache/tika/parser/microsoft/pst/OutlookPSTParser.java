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
import static java.util.Collections.singleton;

import java.io.IOException;
import java.util.Set;

import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PST;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for MS Outlook PST email storage files
 */
@TikaComponent
public class OutlookPSTParser implements Parser {

    public static final MediaType MS_OUTLOOK_PST_MIMETYPE =
            MediaType.application("vnd.ms-outlook-pst");
    private static final long serialVersionUID = 620998217748364063L;
    private static final Set<MediaType> SUPPORTED_TYPES = singleton(MS_OUTLOOK_PST_MIMETYPE);

    private static AttributesImpl createAttribute(String attName, String attValue) {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", attName, attName, "CDATA", attValue);
        return attributes;
    }


    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor embeddedExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        metadata.set(Metadata.CONTENT_TYPE, MS_OUTLOOK_PST_MIMETYPE.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        PSTFile pstFile = null;
        try {
            tis.setCloseShield();
            pstFile = new PSTFile(tis.getFile());
            metadata.set(Metadata.CONTENT_LENGTH, valueOf(pstFile.getFileHandle().length()));
            boolean isValid = pstFile.getFileHandle().getFD().valid();
            metadata.set(PST.IS_VALID, isValid);
            if (pstFile.getPSTFileType() == PSTFile.PST_TYPE_2013_UNICODE) {
                throw new TikaException(
                        "OST 2013 support not added yet. It will be when https://github.com/rjohnsondev/java-libpst/issues/60 is fixed.");
            }
            if (isValid) {
                parseFolder(xhtml, pstFile.getRootFolder(), "/", embeddedExtractor, context);
            }
        } catch (TikaException e) {
            throw e;
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
            tis.removeCloseShield();
        }

        xhtml.endDocument();
    }

    private void parseFolder(XHTMLContentHandler handler, PSTFolder pstFolder, String folderPath,
                             EmbeddedDocumentExtractor embeddedExtractor, ParseContext context) throws Exception {
        if (pstFolder.getContentCount() > 0) {
            PSTMessage pstMail = (PSTMessage) pstFolder.getNextChild();
            while (pstMail != null) {
                Metadata metadata = Metadata.newInstance(context);
                metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, PSTMailItemParser.PST_MAIL_ITEM_STRING);
                String resourceName = pstMail.getSubject() + ".msg";
                String internalPath = folderPath.endsWith("/") ?
                        folderPath + resourceName : folderPath + "/" + resourceName;
                metadata.set(TikaCoreProperties.INTERNAL_PATH, internalPath);
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName);
                long length = estimateSize(pstMail);
                try (TikaInputStream tis = TikaInputStream.getFromContainer(pstMail, length, metadata)) {
                    embeddedExtractor.parseEmbedded(tis, handler, metadata, context, true);
                }
                pstMail = (PSTMessage) pstFolder.getNextChild();
            }
        }

        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                handler.startElement("div", createAttribute("class", "email-folder"));
                handler.element("h1", pstSubFolder.getDisplayName());
                String subFolderPath = folderPath.endsWith("/") ? folderPath + pstSubFolder.getDisplayName() :
                        folderPath + "/" + pstFolder.getDisplayName();
                parseFolder(handler, pstSubFolder, subFolderPath, embeddedExtractor, context);
                handler.endElement("div");
            }
        }
    }

    static protected long estimateSize(PSTMessage attachedEmail) {
        //we do this for a rough estimate of email body size
        //so that we don't get a zip bomb exception on exceedingly large msgs.
        long sz = 0;
        sz += getStringLength(attachedEmail.getBody());
        try {
            sz += getStringLength(attachedEmail.getRTFBody());
        } catch (PSTException | IOException e) {
            //swallow
        }
        sz += getStringLength(attachedEmail.getBodyHTML());
        sz += getStringLength(attachedEmail.getSubject());
        //complete heuristic to account for from, to, etc...
        sz += 100_000;
        return sz;
    }

    private static long getStringLength(String s) {
        if (s == null) {
            return 0;
        }
        return s.length();
    }
}
