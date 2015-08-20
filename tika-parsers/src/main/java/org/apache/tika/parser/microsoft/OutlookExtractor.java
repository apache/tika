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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.Chunks;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.util.CodePageUtil;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.mbox.MboxParser;
import org.apache.tika.parser.rtf.RTFParser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Outlook Message Parser.
 */
public class OutlookExtractor extends AbstractPOIFSExtractor {
    private static final Metadata EMPTY_METADATA = new Metadata();
    HtmlEncodingDetector detector = new HtmlEncodingDetector();

    private final MAPIMessage msg;

    public OutlookExtractor(NPOIFSFileSystem filesystem, ParseContext context) throws TikaException {
        this(filesystem.getRoot(), context);
    }

    public OutlookExtractor(DirectoryNode root, ParseContext context) throws TikaException {
        super(context);

        try {
            this.msg = new MAPIMessage(root);
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
            if (msg.has7BitEncodingStrings()) {
                guess7BitEncoding(msg);
            }

            // Start with the metadata
            String subject = msg.getSubject();
            String from = msg.getDisplayFrom();

            metadata.set(TikaCoreProperties.CREATOR, from);
            metadata.set(Metadata.MESSAGE_FROM, from);
            metadata.set(Metadata.MESSAGE_TO, msg.getDisplayTo());
            metadata.set(Metadata.MESSAGE_CC, msg.getDisplayCC());
            metadata.set(Metadata.MESSAGE_BCC, msg.getDisplayBCC());

            metadata.set(TikaCoreProperties.TITLE, subject);
            // TODO: Move to description in Tika 2.0
            metadata.set(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_DESCRIPTION,
                    msg.getConversationTopic());

            try {
                for (String recipientAddress : msg.getRecipientEmailAddressList()) {
                    if (recipientAddress != null)
                        metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, recipientAddress);
                }
            } catch (ChunkNotFoundException he) {
            } // Will be fixed in POI 3.7 Final

            // Date - try two ways to find it
            // First try via the proper chunk
            if (msg.getMessageDate() != null) {
                metadata.set(TikaCoreProperties.CREATED, msg.getMessageDate().getTime());
                metadata.set(TikaCoreProperties.MODIFIED, msg.getMessageDate().getTime());
            } else {
                try {
                    // Failing that try via the raw headers
                    String[] headers = msg.getHeaders();
                    if (headers != null && headers.length > 0) {
                        for (String header : headers) {
                            if (header.toLowerCase(Locale.ROOT).startsWith("date:")) {
                                String date = header.substring(header.indexOf(':') + 1).trim();

                                // See if we can parse it as a normal mail date
                                try {
                                    Date d = MboxParser.parseDate(date);
                                    metadata.set(TikaCoreProperties.CREATED, d);
                                    metadata.set(TikaCoreProperties.MODIFIED, d);
                                } catch (ParseException e) {
                                    // Store it as-is, and hope for the best...
                                    metadata.set(TikaCoreProperties.CREATED, date);
                                    metadata.set(TikaCoreProperties.MODIFIED, date);
                                }
                                break;
                            }
                        }
                    }
                } catch (ChunkNotFoundException he) {
                    // We can't find the date, sorry...
                }
            }


            xhtml.element("h1", subject);

            // Output the from and to details in text, as you
            //  often want them in text form for searching
            xhtml.startElement("dl");
            if (from != null) {
                header(xhtml, "From", from);
            }
            header(xhtml, "To", msg.getDisplayTo());
            header(xhtml, "Cc", msg.getDisplayCC());
            header(xhtml, "Bcc", msg.getDisplayBCC());
            try {
                header(xhtml, "Recipients", msg.getRecipientEmailAddress());
            } catch (ChunkNotFoundException e) {
            }
            xhtml.endElement("dl");

            // Get the message body. Preference order is: html, rtf, text
            Chunk htmlChunk = null;
            Chunk rtfChunk = null;
            Chunk textChunk = null;
            for (Chunk chunk : msg.getMainChunks().getChunks()) {
                if (chunk.getChunkId() == MAPIProperty.BODY_HTML.id) {
                    htmlChunk = chunk;
                }
                if (chunk.getChunkId() == MAPIProperty.RTF_COMPRESSED.id) {
                    rtfChunk = chunk;
                }
                if (chunk.getChunkId() == MAPIProperty.BODY.id) {
                    textChunk = chunk;
                }
            }

            boolean doneBody = false;
            xhtml.startElement("div", "class", "message-body");
            if (htmlChunk != null) {
                byte[] data = null;
                if (htmlChunk instanceof ByteChunk) {
                    data = ((ByteChunk) htmlChunk).getValue();
                } else if (htmlChunk instanceof StringChunk) {
                    data = ((StringChunk) htmlChunk).getRawValue();
                }
                if (data != null) {
                    HtmlParser htmlParser = new HtmlParser();
                    htmlParser.parse(
                            new ByteArrayInputStream(data),
                            new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                            new Metadata(), new ParseContext()
                    );
                    doneBody = true;
                }
            }
            if (rtfChunk != null && !doneBody) {
                ByteChunk chunk = (ByteChunk) rtfChunk;
                MAPIRtfAttribute rtf = new MAPIRtfAttribute(
                        MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(), chunk.getValue()
                );
                RTFParser rtfParser = new RTFParser();
                rtfParser.parse(
                        new ByteArrayInputStream(rtf.getData()),
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                        new Metadata(), new ParseContext());
                doneBody = true;
            }
            if (textChunk != null && !doneBody) {
                xhtml.element("p", ((StringChunk) textChunk).getValue());
            }
            xhtml.endElement("div");

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

                if (attachment.attachData != null) {
                    handleEmbeddedResource(
                            TikaInputStream.get(attachment.attachData.getValue()),
                            filename, null,
                            null, xhtml, true
                    );
                }
                if (attachment.attachmentDirectory != null) {
                    handleEmbeddedOfficeDoc(
                            attachment.attachmentDirectory.getDirectory(),
                            xhtml
                    );
                }

                xhtml.endElement("div");
            }
        } catch (ChunkNotFoundException e) {
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

    /**
     * Tries to identify the correct encoding for 7-bit (non-unicode)
     *  strings in the file.
     * <p>Many messages store their strings as unicode, which is
     *  nice and easy. Some use one-byte encodings for their
     *  strings, but don't always store the encoding anywhere
     *  helpful in the file.</p>
     * <p>This method checks for codepage properties, and failing that
     *  looks at the headers for the message, and uses these to
     *  guess the correct encoding for your file.</p>
     * <p>Bug #49441 has more on why this is needed</p>
     * <p>This is taken verbatim from POI (TIKA-1238)
     * as a temporary workaround to prevent unsupported encoding exceptions</p>
     */
    private void guess7BitEncoding(MAPIMessage msg) {
        Chunks mainChunks = msg.getMainChunks();
        //sanity check
        if (mainChunks == null) {
            return;
        }

        Map<MAPIProperty, List<PropertyValue>> props = mainChunks.getProperties();
        if (props != null) {
            // First choice is a codepage property
            for (MAPIProperty prop : new MAPIProperty[]{
                    MAPIProperty.MESSAGE_CODEPAGE,
                    MAPIProperty.INTERNET_CPID
            }) {
                List<PropertyValue> val = props.get(prop);
                if (val != null && val.size() > 0) {
                    int codepage = ((PropertyValue.LongPropertyValue) val.get(0)).getValue();
                    String encoding = null;
                    try {
                        encoding = CodePageUtil.codepageToEncoding(codepage, true);
                    } catch (UnsupportedEncodingException e) {
                        //swallow
                    }
                    if (tryToSet7BitEncoding(msg, encoding)) {
                        return;
                    }
                }
            }
        }

        // Second choice is a charset on a content type header
        try {
            String[] headers = msg.getHeaders();
            if(headers != null && headers.length > 0) {
                // Look for a content type with a charset
                Pattern p = Pattern.compile("Content-Type:.*?charset=[\"']?([^;'\"]+)[\"']?", Pattern.CASE_INSENSITIVE);

                for(String header : headers) {
                    if(header.startsWith("Content-Type")) {
                        Matcher m = p.matcher(header);
                        if(m.matches()) {
                            // Found it! Tell all the string chunks
                            String charset = m.group(1);
                            if (tryToSet7BitEncoding(msg, charset)) {
                                return;
                            }
                        }
                    }
                }
            }
        } catch(ChunkNotFoundException e) {}

        // Nothing suitable in the headers, try HTML
        // TODO: do we need to replicate this in Tika? If we wind up
        // parsing the html version of the email, this is duplicative??
        // Or do we need to reset the header strings based on the html
        // meta header if there is no other information?
        try {
            String html = msg.getHtmlBody();
            if(html != null && html.length() > 0) {
                Charset charset = null;
                try {
                    charset = detector.detect(new ByteArrayInputStream(
                            html.getBytes(UTF_8)), EMPTY_METADATA);
                } catch (IOException e) {
                    //swallow
                }
                if (charset != null && tryToSet7BitEncoding(msg, charset.name())) {
                    return;
                }
            }
        } catch(ChunkNotFoundException e) {}

        //absolute last resort, try charset detector
        StringChunk text = mainChunks.textBodyChunk;
        if (text != null) {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(text.getRawValue());
            CharsetMatch match = detector.detect();
            if (match != null && match.getConfidence() > 35 &&
                    tryToSet7BitEncoding(msg, match.getName())) {
                return;
            }
        }
    }

    private boolean tryToSet7BitEncoding(MAPIMessage msg, String charsetName) {
        if (charsetName == null) {
            return false;
        }

        if (charsetName.equalsIgnoreCase("utf-8")) {
            return false;
        }
        try {
            if (Charset.isSupported(charsetName)) {
                msg.set7BitEncoding(charsetName);
                return true;
            }
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            //swallow
        }
        return false;
    }
}
