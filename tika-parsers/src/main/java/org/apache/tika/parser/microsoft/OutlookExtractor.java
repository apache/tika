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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.Chunks;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.RecipientChunks;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.util.CodePageUtil;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
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

/**
 * Outlook Message Parser.
 */
public class OutlookExtractor extends AbstractPOIFSExtractor {

    public enum RECIPIENT_TYPE {
        TO(1),
        CC(2),
        BCC(3),
        UNRECOGNIZED(-1),
        UNSPECIFIED(-1);

        private final int val;

        RECIPIENT_TYPE(int val) {
            this.val = val;
        }

        public static RECIPIENT_TYPE getTypeFromVal(int val) {
            //mild hackery, clean up
            if (val > 0 && val < 4) {
                return RECIPIENT_TYPE.values()[val - 1];
            }
            return UNRECOGNIZED;
        }
    };

    private enum ADDRESS_TYPE {
        EX,
        SMTP
    }


    private static Pattern HEADER_KEY_PAT =
            Pattern.compile("\\A([\\x21-\\x39\\x3B-\\x7E]+):(.*?)\\Z");
    //this according to the spec; in practice, it is probably more likely
    //that a "split field" fails to start with a space character than
    //that a real header contains anything but [-_A-Za-z0-9].
    //e.g.
    //header: this header goes onto the next line
    //<mailto:xyz@cnn.com...


    private static final Metadata EMPTY_METADATA = new Metadata();
    HtmlEncodingDetector detector = new HtmlEncodingDetector();

    private final MAPIMessage msg;
    private final ParseContext parseContext;

    private final boolean extractAllAlternatives;

    public OutlookExtractor(NPOIFSFileSystem filesystem, ParseContext context) throws TikaException {
        this(filesystem.getRoot(), context);
    }

    public OutlookExtractor(DirectoryNode root, ParseContext context) throws TikaException {
        super(context);
        this.parseContext = context;
        this.extractAllAlternatives = context.get(OfficeParserConfig.class).getExtractAllAlternativesFromMSG();
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

            try {
                metadata.set(Office.MAPI_MESSAGE_CLASS, getMessageClass(msg.getMessageClass()));
            } catch (ChunkNotFoundException e){}

            // If the message contains strings that aren't stored
            //  as Unicode, try to sort out an encoding for them
            if (msg.has7BitEncodingStrings()) {
                guess7BitEncoding(msg);
            }

            // Start with the metadata
            String subject = msg.getSubject();
            Map<String, String[]> headers = normalizeHeaders(msg.getHeaders());
            String from = msg.getDisplayFrom();

            handleFromTo(headers, metadata);

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

            for (Map.Entry<String, String[]> e : headers.entrySet()) {
                String headerKey = e.getKey();
                for (String headerValue : e.getValue()) {
                    metadata.add(Metadata.MESSAGE_RAW_HEADER_PREFIX + headerKey, headerValue);
                }
            }

            // Date - try two ways to find it
            // First try via the proper chunk
            if (msg.getMessageDate() != null) {
                metadata.set(TikaCoreProperties.CREATED, msg.getMessageDate().getTime());
                metadata.set(TikaCoreProperties.MODIFIED, msg.getMessageDate().getTime());
            } else {
                    if (headers != null && headers.size() > 0) {
                        for (Map.Entry<String, String[]> header : headers.entrySet()) {
                            String headerKey = header.getKey();
                            if (headerKey.toLowerCase(Locale.ROOT).startsWith("date:")) {
                                String date = headerKey.substring(headerKey.indexOf(':') + 1).trim();

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
            handleBodyChunks(htmlChunk, rtfChunk, textChunk, xhtml);

            // Process the attachments
            for (AttachmentChunks attachment : msg.getAttachmentFiles()) {
                xhtml.startElement("div", "class", "attachment-entry");

                String filename = null;
                if (attachment.getAttachLongFileName() != null) {
                    filename = attachment.getAttachLongFileName().getValue();
                } else if (attachment.getAttachFileName() != null) {
                    filename = attachment.getAttachFileName().getValue();
                }
                if (filename != null && filename.length() > 0) {
                    xhtml.element("h1", filename);
                }

                if (attachment.getAttachData() != null) {
                    handleEmbeddedResource(
                            TikaInputStream.get(attachment.getAttachData().getValue()),
                            filename, null,
                            null, xhtml, true
                    );
                }
                if (attachment.getAttachmentDirectory() != null) {
                    handleEmbeddedOfficeDoc(
                            attachment.getAttachmentDirectory().getDirectory(),
                            xhtml
                    );
                }

                xhtml.endElement("div");
            }
        } catch (ChunkNotFoundException e) {
            throw new TikaException("POI MAPIMessage broken - didn't return null on missing chunk", e);
        } finally {
            //You'd think you'd want to call msg.close().
            //Don't do that.  That closes down the file system.
            //If an msg has multiple msg attachments, some of them
            //can reside in the same file system.  After the first
            //child is read, the fs is closed, and the other children
            //get a java.nio.channels.ClosedChannelException
        }
    }

    private void handleBodyChunks(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk, XHTMLContentHandler xhtml) throws SAXException, IOException, TikaException {

        if (extractAllAlternatives) {
            extractAllAlternatives(htmlChunk, rtfChunk, textChunk, xhtml);
            return;
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
                Parser htmlParser =
                        EmbeddedDocumentUtil.tryToFindExistingLeafParser(HtmlParser.class, parseContext);
                if (htmlParser == null) {
                    htmlParser = new HtmlParser();
                }
                htmlParser.parse(
                        new ByteArrayInputStream(data),
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                        new Metadata(), parseContext
                );
                doneBody = true;
            }
        }
        if (rtfChunk != null && (extractAllAlternatives || !doneBody)) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            MAPIRtfAttribute rtf = new MAPIRtfAttribute(
                    MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(), chunk.getValue()
            );
            Parser rtfParser =
                    EmbeddedDocumentUtil.tryToFindExistingLeafParser(RTFParser.class, parseContext);
            if (rtfParser == null) {
                rtfParser = new RTFParser();
            }
            rtfParser.parse(
                    new ByteArrayInputStream(rtf.getData()),
                    new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                    new Metadata(), parseContext);
            doneBody = true;
        }
        if (textChunk != null && (extractAllAlternatives || !doneBody)) {
            xhtml.element("p", ((StringChunk) textChunk).getValue());
        }
        xhtml.endElement("div");

    }

    private void extractAllAlternatives(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk, XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        if (htmlChunk != null) {
            byte[] data = getValue(htmlChunk);
            if (data != null) {
                handleEmbeddedResource(
                        TikaInputStream.get(data),
                        "html-body", null,
                        MediaType.TEXT_HTML.toString(), xhtml, true
                );
            }
        }
        if (rtfChunk != null) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            MAPIRtfAttribute rtf = new MAPIRtfAttribute(
                    MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(), chunk.getValue()
            );

            byte[] data = rtf.getData();
            if (data != null) {
                handleEmbeddedResource(
                        TikaInputStream.get(data),
                        "rtf-body", null,
                        "application/rtf", xhtml, true
                );
            }
        }
        if (textChunk != null) {
            byte[] data = getValue(textChunk);
            if (data != null) {
                handleEmbeddedResource(
                        TikaInputStream.get(data),
                        "text-body", null,
                        MediaType.TEXT_PLAIN.toString(), xhtml, true
                );
            }
        }

    }

    //can return null!
    private byte[] getValue(Chunk chunk) {
        byte[] data = null;
        if (chunk instanceof ByteChunk) {
            data = ((ByteChunk) chunk).getValue();
        } else if (chunk instanceof StringChunk) {
            data = ((StringChunk) chunk).getRawValue();
        }
        return data;
    }

    private void handleFromTo(Map<String, String[]> headers, Metadata metadata) throws ChunkNotFoundException {
        String from = msg.getDisplayFrom();
        metadata.set(TikaCoreProperties.CREATOR, from);
        metadata.set(Metadata.MESSAGE_FROM, from);
        metadata.set(Metadata.MESSAGE_TO, msg.getDisplayTo());
        metadata.set(Metadata.MESSAGE_CC, msg.getDisplayCC());
        metadata.set(Metadata.MESSAGE_BCC, msg.getDisplayBCC());


        Chunks chunks = msg.getMainChunks();
        StringChunk sentByServerType = chunks.getSentByServerType();
        if (sentByServerType != null) {
            metadata.set(Office.MAPI_SENT_BY_SERVER_TYPE,
                    sentByServerType.getValue());
        }

        Map<MAPIProperty, List<Chunk>> mainChunks = msg.getMainChunks().getAll();

        List<Chunk> senderAddresType = mainChunks.get(MAPIProperty.SENDER_ADDRTYPE);
        String senderAddressTypeString = "";
        if (senderAddresType != null && senderAddresType.size() > 0) {
            senderAddressTypeString = senderAddresType.get(0).toString();
        }

        //sometimes in SMTP .msg files there is an email in the sender name field.

        setFirstChunk(
                mainChunks.get(MAPIProperty.SENDER_NAME), Message.MESSAGE_FROM_NAME, metadata);
        setFirstChunk(
                mainChunks.get(MAPIProperty.SENT_REPRESENTING_NAME),
                Office.MAPI_FROM_REPRESENTING_NAME, metadata);

        setFirstChunk(mainChunks.get(MAPIProperty.SENDER_EMAIL_ADDRESS),
                Message.MESSAGE_FROM_EMAIL, metadata);
        setFirstChunk(mainChunks.get(MAPIProperty.SENT_REPRESENTING_EMAIL_ADDRESS),
                Office.MAPI_FROM_REPRESENTING_EMAIL, metadata);

        for (Recipient recipient : buildRecipients()) {
            switch(recipient.recipientType) {
                case TO:
                    addEvenIfNull(Message.MESSAGE_TO_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_TO_DISPLAY_NAME, recipient.displayName, metadata);
                    addEvenIfNull(Message.MESSAGE_TO_EMAIL, recipient.emailAddress, metadata);
                    break;
                case CC:
                    addEvenIfNull(Message.MESSAGE_CC_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_CC_DISPLAY_NAME, recipient.displayName, metadata);
                    addEvenIfNull(Message.MESSAGE_CC_EMAIL, recipient.emailAddress, metadata);
                    break;
                case BCC:
                    addEvenIfNull(Message.MESSAGE_BCC_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_BCC_DISPLAY_NAME, recipient.displayName, metadata);
                    addEvenIfNull(Message.MESSAGE_BCC_EMAIL, recipient.emailAddress, metadata);
                    break;
                default:
                    //log unknown or undefined?
                    break;
            }
        }
    }

    //need to add empty string to ensure that parallel arrays are parallel
    //even if one value is null.
    public static void addEvenIfNull(Property property, String value, Metadata metadata) {
        if (value == null) {
            value = "";
        }
        metadata.add(property, value);
    }

    private static void setFirstChunk(List<Chunk> chunks, Property property,
                                 Metadata metadata) {
        if (chunks == null || chunks.size() < 1 || chunks.get(0) == null) {
            return;
        }
        metadata.set(property, chunks.get(0).toString());
    }

    private static void addFirstChunk(List<Chunk> chunks, Property property,
                                      Metadata metadata) {
        if (chunks == null || chunks.size() < 1 || chunks.get(0) == null) {
            return;
        }
        metadata.add(property, chunks.get(0).toString());
    }

    //TODO: replace this with getMessageClassEnum when we upgrade POI
    public static String getMessageClass(String messageClass){
        if (messageClass == null || messageClass.trim().length() == 0) {
            return "UNSPECIFIED";
        } else if (messageClass.equalsIgnoreCase("IPM.Note")) {
            return "NOTE";
        } else if (messageClass.equalsIgnoreCase("IPM.Contact")) {
            return "CONTACT";
        } else if (messageClass.equalsIgnoreCase("IPM.Appointment")) {
            return "APPOINTMENT";
        } else if (messageClass.equalsIgnoreCase("IPM.StickyNote")) {
            return "STICKY_NOTE";
        } else if (messageClass.equalsIgnoreCase("IPM.Task")) {
            return "TASK";
        } else if (messageClass.equalsIgnoreCase("IPM.Post")) {
            return "POST";
        } else {
            return "UNKNOWN";
        }
    }

    //As of 3.15, POI currently returns header[] by splitting on /\r?\n/
    //this rebuilds headers that are broken up over several lines
    //this also decodes encoded headers.
    private Map<String, String[]> normalizeHeaders(String[] rows) {
        Map<String, String[]> ret = new LinkedHashMap<>();
        if (rows == null) {
            return ret;
        }
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> headers = new LinkedHashMap();
        Matcher headerKeyMatcher = HEADER_KEY_PAT.matcher("");
        String lastKey = null;
        int consec = 0;
        for (String row : rows) {
            headerKeyMatcher.reset(row);
            if (headerKeyMatcher.find()) {
                if (lastKey != null) {
                    List<String> vals = headers.get(lastKey);
                    vals = (vals == null) ? new ArrayList<String>() : vals;
                    vals.add(decodeHeader(sb.toString()));
                    headers.put(lastKey, vals);
                }
                //reset sb
                sb.setLength(0);
                lastKey = headerKeyMatcher.group(1).trim();
                sb.append(headerKeyMatcher.group(2).trim());
                consec = 0;
            } else {
                if (consec > 0) {
                    sb.append("\n");
                }
                sb.append(row);
            }
            consec++;
        }

        //make sure to add the last value
        if (sb.length() > 0 && lastKey != null) {
            List<String> vals = headers.get(lastKey);
            vals = (vals == null) ? new ArrayList<String>() : vals;
            vals.add(decodeHeader(sb.toString()));
            headers.put(lastKey, vals);
        }

        //convert to array
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            ret.put(e.getKey(), e.getValue().toArray(new String[e.getValue().size()]));
        }
        return ret;

    }

    private String decodeHeader(String header) {
        return DecoderUtil.decodeEncodedWords(header, DecodeMonitor.SILENT);
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
        StringChunk text = mainChunks.getTextBodyChunk();
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


    private List<Recipient> buildRecipients() {
        RecipientChunks[] recipientChunks = msg.getRecipientDetailsChunks();
        if (recipientChunks == null) {
            return Collections.EMPTY_LIST;
        }
        List<Recipient> recipients = new LinkedList<>();

        for (RecipientChunks chunks : recipientChunks) {
            Recipient r = new Recipient();
            r.displayName = (chunks.recipientDisplayNameChunk != null) ? chunks.recipientDisplayNameChunk.toString() : null;
            r.name = (chunks.recipientNameChunk != null) ? chunks.recipientNameChunk.toString() : null;
            r.emailAddress = chunks.getRecipientEmailAddress();
            List<PropertyValue> vals = chunks.getProperties().get(MAPIProperty.RECIPIENT_TYPE);

            RECIPIENT_TYPE recipientType = RECIPIENT_TYPE.UNSPECIFIED;
            if (vals != null && vals.size() > 0) {
                Object val = vals.get(0).getValue();
                if (val instanceof Integer) {
                    recipientType = RECIPIENT_TYPE.getTypeFromVal((int)val);
                }
            }
            r.recipientType = recipientType;

            vals = chunks.getProperties().get(MAPIProperty.ADDRTYPE);
            if (vals != null && vals.size() > 0) {
                String val = vals.get(0).toString();
                if (val != null) {
                    val = val.toLowerCase(Locale.US);
                    //need to find example of this for testing
                    if (val.equals("ex")) {
                        r.addressType = ADDRESS_TYPE.EX;
                    } else if (val.equals("smtp")) {
                        r.addressType = ADDRESS_TYPE.SMTP;
                    }
                }
            }
            recipients.add(r);
        }
        return recipients;
    }

    private static class Recipient {
        String name;
        String displayName;
        RECIPIENT_TYPE recipientType;
        String emailAddress;
        ADDRESS_TYPE addressType;
    }
}
