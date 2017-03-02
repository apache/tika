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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
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
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.util.CodePageUtil;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingDetectorProxy;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserProxy;
import org.apache.tika.parser.mail.util.MailUtil;
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

    private final static String RECIPIENTS = "recipients";
    private final static Pattern EXCHANGE_O = Pattern.compile("(?i)/o=([^/]+)");
    private final static Pattern EXCHANGE_OU = Pattern.compile("(?i)/ou=([^/]+)");
    private final static Pattern EXCHANGE_CN = Pattern.compile("(?i)/cn=([^/]+)");


    private static Pattern HEADER_KEY_PAT =
            Pattern.compile("\\A([\\x21-\\x39\\x3B-\\x7E]+):(.*?)\\Z");
    //this according to the spec; in practice, it is probably more likely
    //that a "split field" fails to start with a space character than
    //that a real header contains anything but [-_A-Za-z0-9].
    //e.g.
    //header: this header goes onto the next line
    //<mailto:xyz@cnn.com...


    private static final Metadata EMPTY_METADATA = new Metadata();
    private final SimpleDateFormat dateFormat;
    private final EncodingDetector htmlEncodingDetectorProxy;

    private final MAPIMessage msg;
    private final ParseContext parseContext;

    public OutlookExtractor(NPOIFSFileSystem filesystem, ParseContext context) throws TikaException {
        this(filesystem.getRoot(), context);
    }

    public OutlookExtractor(DirectoryNode root, ParseContext context) throws TikaException {
        super(context);
        this.parseContext = context;
        this.htmlEncodingDetectorProxy = new EncodingDetectorProxy("org.apache.tika.parser.html.HtmlEncodingDetector", getClass().getClassLoader());
        this.dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
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

            boolean doneBody = false;
            xhtml.startElement("div", "class", "message-body");
            if (htmlChunk != null) {
                byte[] data = null;
                if (htmlChunk instanceof ByteChunk) {
                    data = ((ByteChunk) htmlChunk).getValue();
                } else if (htmlChunk instanceof StringChunk) {
                    data = ((StringChunk) htmlChunk).getRawValue();
                }
                Parser htmlParser =
                        EmbeddedDocumentUtil.tryToFindExistingLeafParser(
                                "org.apache.tika.parser.html.HtmlParser", parseContext);
                if (htmlParser == null) {
                    htmlParser = new ParserProxy("org.apache.tika.parser.html.HtmlParser", getClass().getClassLoader());
                }

                if (data != null) {
                    htmlParser.parse(
                            new ByteArrayInputStream(data),
                            new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                            new Metadata(), parseContext
                    );
                    doneBody = true;
                }
            }
            if (rtfChunk != null && !doneBody) {
                ByteChunk chunk = (ByteChunk) rtfChunk;
                MAPIRtfAttribute rtf = new MAPIRtfAttribute(
                        MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(), chunk.getValue()
                );
                Parser rtfParser =
                        EmbeddedDocumentUtil.tryToFindExistingLeafParser(
                                RTFParser.class.getCanonicalName(), parseContext);
                if (rtfParser == null) {
                    rtfParser = new RTFParser();
                }
                rtfParser.parse(
                        new ByteArrayInputStream(rtf.getData()),
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                        new Metadata(), parseContext);
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

        //sometimes in SMTP .msg files there is an email in the sender name field
        //make sure to rule those out.
        addChunks(
                mainChunks.get(MAPIProperty.SENDER_NAME), Message.MESSAGE_FROM_NAME,
                    false, metadata);
        addChunks(
                mainChunks.get(MAPIProperty.SENT_REPRESENTING_NAME),
                    Office.MAPI_FROM_REPRESENTING_NAME, false, metadata);

        if (senderAddressTypeString.equalsIgnoreCase("ex")) {
            addExchange(mainChunks.get(MAPIProperty.SENDER_EMAIL_ADDRESS),
                    Office.MAPI_EXCHANGE_FROM_O, Office.MAPI_EXCHANGE_FROM_OU,
                    Office.MAPI_EXCHANGE_FROM_CN, metadata);
            addExchange(mainChunks.get(MAPIProperty.SENT_REPRESENTING_EMAIL_ADDRESS),
                    Office.MAPI_EXCHANGE_FROM_REPRESENTING_O, Office.MAPI_EXCHANGE_FROM_REPRESENTING_OU,
                    Office.MAPI_EXCHANGE_FROM_REPRESENTING_CN, metadata);
        } else {
            addChunks(mainChunks.get(MAPIProperty.SENDER_EMAIL_ADDRESS),
                    Message.MESSAGE_FROM_EMAIL, true, metadata);
            addChunks(mainChunks.get(MAPIProperty.SENT_REPRESENTING_EMAIL_ADDRESS),
                    Office.MAPI_FROM_REPRESENTING_EMAIL, true, metadata);
        }
    }


    private static void addExchange(List<Chunk> chunks,
                                    Property propertyO,
                                    Property propertyOU, Property propertyCN, Metadata metadata ) {
        if (chunks == null || chunks.size() == 0) {
            return;
        }
        addExchange(chunks.get(0).toString(), propertyO, propertyOU, propertyCN, metadata);
    }

    public static void addExchange(String exchange,Property propertyO,
                             Property propertyOU, Property propertyCN, Metadata metadata) {
        if (exchange == null || exchange.length() < 1) {
            return;
        }
        Matcher matcherO = EXCHANGE_O.matcher(exchange);
        if (matcherO.find()) {
            metadata.set(propertyO, matcherO.group(1));
        }
        Matcher matcherOU = EXCHANGE_OU.matcher(exchange);
        if (matcherOU.find()) {
            metadata.set(propertyOU, matcherOU.group(1));
        }

        Matcher matcherCN = EXCHANGE_CN.matcher(exchange);
        while (matcherCN.find()) {
            String cn = matcherCN.group(1);
            if (!cn.equalsIgnoreCase(RECIPIENTS)) {
                metadata.add(propertyCN, cn);
            }
        }
    }

    private static void addChunks(List<Chunk> chunks, Property property, boolean mustContainEmail,
                                 Metadata metadata) {
        if (chunks == null || chunks.size() < 1) {
            return;
        }
        addChunks(chunks.get(0).toString(), property, mustContainEmail, metadata);
    }

    public static void addChunks(String chunk, Property property, boolean mustContainEmail,
                           Metadata metadata) {
        if (chunk == null || chunk.length() == 0) {
            return;
        }
        if (mustContainEmail == MailUtil.containsEmail(chunk)) {
                metadata.set(property, chunk);
        }
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
                    charset = htmlEncodingDetectorProxy.detect(new ByteArrayInputStream(
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
}
