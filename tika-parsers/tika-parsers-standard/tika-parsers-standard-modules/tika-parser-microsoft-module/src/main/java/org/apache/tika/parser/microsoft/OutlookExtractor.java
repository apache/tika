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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.Chunks;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.MessageSubmissionChunk;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.RecipientChunks;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.util.CodePageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.parser.mailcommons.MailDateParser;
import org.apache.tika.parser.microsoft.msg.ExtendedMetadataExtractor;
import org.apache.tika.parser.microsoft.rtf.RTFParser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;


/**
 * Outlook Message Parser.
 */
public class OutlookExtractor extends AbstractPOIFSExtractor {
    static Logger LOGGER = LoggerFactory.getLogger(OutlookExtractor.class);
    public enum BODY_TYPES_PROCESSED {
        HTML, RTF, TEXT;
    }

    private static final Metadata EMPTY_METADATA = new Metadata();
    private static final MAPIProperty[] LITERAL_TIME_MAPI_PROPERTIES = new MAPIProperty[] {
            MAPIProperty.CLIENT_SUBMIT_TIME,
            MAPIProperty.CREATION_TIME,
            MAPIProperty.DEFERRED_DELIVERY_TIME,
            MAPIProperty.DELIVER_TIME,
            //EXPAND BEGIN and EXPAND END?
            MAPIProperty.EXPIRY_TIME,
            MAPIProperty.LAST_MODIFICATION_TIME,
            MAPIProperty.LATEST_DELIVERY_TIME,
            MAPIProperty.MESSAGE_DELIVERY_TIME,
            MAPIProperty.MESSAGE_DOWNLOAD_TIME,
            MAPIProperty.ORIGINAL_DELIVERY_TIME,
            MAPIProperty.ORIGINAL_SUBMIT_TIME,
            MAPIProperty.PROVIDER_SUBMIT_TIME,
            MAPIProperty.RECEIPT_TIME,
            MAPIProperty.REPLY_TIME,
            MAPIProperty.REPORT_TIME

    };

    private static final Map<MAPIProperty, Property> LITERAL_TIME_PROPERTIES = new HashMap<>();

    private static final Map<String, String> MESSAGE_CLASSES = new LinkedHashMap<>();

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img ([^>]{0,1000})>");
    private static final Pattern SRC_ATTR_PATTERN = Pattern.compile("src=\"cid:([^\"]{0,1000})\"");
    private static final Pattern TEXT_CID_PATTERN = Pattern.compile("\\[cid:([^]]{0,1000})]");

    static {
        for (MAPIProperty property : LITERAL_TIME_MAPI_PROPERTIES) {
            String name = property.mapiProperty.toLowerCase(Locale.ROOT);
            name = name.substring(3);
            name = name.replace('_', '-');
            name = MAPI.PREFIX_MAPI_META + name;
            Property tikaProp = Property.internalDate(name);
            LITERAL_TIME_PROPERTIES.put(property, tikaProp);
        }
        loadMessageClasses();
    }

    private static void loadMessageClasses() {
        String fName = "/org/apache/tika/parser/microsoft/msg/mapi_message_classes.properties";
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        OutlookExtractor.class.getResourceAsStream(fName), UTF_8))) {
            String line = r.readLine();
            while (line != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    line = r.readLine();
                    continue;
                }
                String[] cols = line.split("\\s+");
                String lcKey = cols[0].toLowerCase(Locale.ROOT);
                String value = cols[1];
                if (MESSAGE_CLASSES.containsKey(lcKey)) {
                    throw new IllegalArgumentException("Can't have duplicate keys: " + lcKey);
                }
                MESSAGE_CLASSES.put(lcKey, value);
                line = r.readLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("can't find mapi_message_classes.properties?!");
        }

    }

    //this according to the spec; in practice, it is probably more likely
    //that a "split field" fails to start with a space character than
    //that a real header contains anything but [-_A-Za-z0-9].
    //e.g.
    //header: this header goes onto the next line
    //<mailto:xyz@cnn.com...
    private static Pattern HEADER_KEY_PAT =
            Pattern.compile("\\A([\\x21-\\x39\\x3B-\\x7E]+):(.*?)\\Z");

    private final MAPIMessage msg;
    private final ParseContext parseContext;
    private final boolean extractAllAlternatives;
    private final boolean extractExtendedMsgProperties;
    HtmlEncodingDetector detector = new HtmlEncodingDetector();


    public OutlookExtractor(DirectoryNode root, Metadata metadata, ParseContext context) throws TikaException {
        super(context, metadata);
        this.parseContext = context;
        this.extractAllAlternatives =
                context.get(OfficeParserConfig.class).isExtractAllAlternativesFromMSG();
        this.extractExtendedMsgProperties = context.get(OfficeParserConfig.class).isExtractExtendedMsgProperties();
        try {
            this.msg = new MAPIMessage(root);
        } catch (IOException e) {
            throw new TikaException("Failed to parse Outlook message", e);
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

    private static void setFirstChunk(List<Chunk> chunks, Property property, Metadata metadata) {
        if (chunks == null || chunks.isEmpty() || chunks.get(0) == null) {
            return;
        }
        metadata.set(property, chunks.get(0).toString());
    }

    public static String getNormalizedMessageClass(String messageClass) {
        if (messageClass == null || messageClass.isBlank()) {
            return "UNSPECIFIED";
        }
        String lc = messageClass.toLowerCase(Locale.ROOT);
        if (MESSAGE_CLASSES.containsKey(lc)) {
            return MESSAGE_CLASSES.get(lc);
        }
        return "UNKNOWN";
    }

    public void parse(XHTMLContentHandler xhtml) throws TikaException, SAXException, IOException {
        try {
            _parse(xhtml);
        } catch (ChunkNotFoundException e) {
            throw new TikaException("POI MAPIMessage broken - didn't return null on missing chunk", e);
        } /*finally {
            //You'd think you'd want to call msg.close().
            //Don't do that.  That closes down the file system.
            //If an msg has multiple msg attachments, some of them
            //can reside in the same file system.  After the first
            //child is read, the fs is closed, and the other children
            //get a java.nio.channels.ClosedChannelException
        }*/
    }

    private void _parse(XHTMLContentHandler xhtml) throws TikaException, SAXException, IOException, ChunkNotFoundException {
        msg.setReturnNullOnMissingChunk(true);

        // If the message contains strings that aren't stored
        //  as Unicode, try to sort out an encoding for them
        if (msg.has7BitEncodingStrings()) {
            guess7BitEncoding(msg);
        }

        // Start with the metadata
        Map<String, String[]> headers = normalizeHeaders(msg.getHeaders());

        handleFromTo(headers, parentMetadata);
        handleMessageInfo(msg, headers, parentMetadata);
        if (extractExtendedMsgProperties) {
            ExtendedMetadataExtractor.extract(msg, parentMetadata);
        }

        try {
            for (String recipientAddress : msg.getRecipientEmailAddressList()) {
                if (recipientAddress != null) {
                    parentMetadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, recipientAddress);
                }
            }
        } catch (ChunkNotFoundException e) {
            //you'd think we wouldn't need this. we do.
        }

        for (Map.Entry<String, String[]> e : headers.entrySet()) {
            String headerKey = e.getKey();
            for (String headerValue : e.getValue()) {
                parentMetadata.add(Metadata.MESSAGE_RAW_HEADER_PREFIX + headerKey, headerValue);
            }
        }

        handleGeneralDates(msg, headers, parentMetadata);
        writeSelectHeadersInBody(parentMetadata, msg, xhtml);

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

        Set<String> contentIdNames = new HashSet<>();
        handleBodyChunks(htmlChunk, rtfChunk, textChunk, xhtml, contentIdNames);
        // Process the attachments
        for (AttachmentChunks attachment : msg.getAttachmentFiles()) {
            Metadata attachMetadata = new Metadata();
            updateAttachmentMetadata(attachment, attachMetadata, contentIdNames);
            String filename = null;
            if (!StringUtils.isBlank(attachMetadata.get(MAPI.ATTACH_LONG_FILE_NAME))) {
                filename = attachMetadata.get(MAPI.ATTACH_LONG_FILE_NAME);
            } else if (!StringUtils.isBlank(attachMetadata.get(MAPI.ATTACH_DISPLAY_NAME))) {
                filename = attachMetadata.get(MAPI.ATTACH_DISPLAY_NAME);
            } else if (!StringUtils.isBlank(attachMetadata.get(MAPI.ATTACH_FILE_NAME))) {
                filename = attachMetadata.get(MAPI.ATTACH_FILE_NAME);
            }
            //this is allowed to be null;
            String mimeType = attachMetadata.get(MAPI.ATTACH_MIME);
            if (attachment.getAttachData() != null) {
                handleEmbeddedResource(TikaInputStream.get(attachment
                        .getAttachData()
                        .getValue()), attachMetadata, filename, null, null, mimeType, xhtml, true);
            }
            if (attachment.getAttachmentDirectory() != null) {
                handleEmbeddedOfficeDoc(attachment
                        .getAttachmentDirectory()
                        .getDirectory(), attachMetadata, filename, xhtml, true);
            }
        }

    }

    private void updateAttachmentMetadata(AttachmentChunks attachment, Metadata metadata,
                                          Set<String> contentIdNames) {
        StringChunk contentIdChunk = attachment.getAttachContentId();
        if (contentIdChunk != null) {
            String contentId = contentIdChunk.getValue();
            if (! StringUtils.isBlank(contentId)) {
                contentId = contentId.trim();
                if (contentIdNames.contains(contentId)) {
                    metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY,
                            TikaCoreProperties.EmbeddedResourceType.INLINE.name());
                }
                metadata.set(MAPI.ATTACH_CONTENT_ID, contentId);
            }
        }
        addStringChunkToMetadata(MAPI.ATTACH_LONG_PATH_NAME, attachment.getAttachLongPathName(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_LONG_FILE_NAME, attachment.getAttachLongFileName(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_FILE_NAME, attachment.getAttachFileName(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_CONTENT_LOCATION, attachment.getAttachContentLocation(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_DISPLAY_NAME, attachment.getAttachDisplayName(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_EXTENSION, attachment.getAttachExtension(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_MIME, attachment.getAttachMimeTag(), metadata);
        addStringChunkToMetadata(MAPI.ATTACH_LANGUAGE, attachment.getAttachLanguage(), metadata);
    }

    private void addStringChunkToMetadata(Property property, StringChunk stringChunk, Metadata metadata) {
        if (stringChunk == null) {
            return;
        }
        String v = stringChunk.getValue();
        if (StringUtils.isBlank(v)) {
            return;
        }
        metadata.set(property, v);
    }

    private void handleMessageInfo(MAPIMessage msg, Map<String, String[]> headers, Metadata metadata) throws ChunkNotFoundException {
        //this is the literal subject including "re: "
        metadata.set(TikaCoreProperties.TITLE, msg.getSubject());
        //this is the original topic for the thread without the "re: "
        String topic = msg.getConversationTopic();
        metadata.set(TikaCoreProperties.SUBJECT, topic);
        metadata.set(TikaCoreProperties.DESCRIPTION, topic);
        metadata.set(MAPI.CONVERSATION_TOPIC, topic);
        Chunks mainChunks = msg.getMainChunks();
        if (mainChunks == null) {
            return;
        }
        if (mainChunks.getMessageId() != null) {
            metadata.set(MAPI.INTERNET_MESSAGE_ID, mainChunks
                    .getMessageId()
                    .getValue());
        }

        String mc = msg.getStringFromChunk(mainChunks.getMessageClass());
        if (mc != null) {
            metadata.set(MAPI.MESSAGE_CLASS_RAW, mc);
        }
        metadata.set(MAPI.MESSAGE_CLASS, getNormalizedMessageClass(mc));
        List<Chunk> conversationIndex = mainChunks
                .getAll()
                .get(MAPIProperty.CONVERSATION_INDEX);
        if (conversationIndex != null && !conversationIndex.isEmpty()) {
            Chunk chunk = conversationIndex.get(0);
            if (chunk instanceof ByteChunk) {
                byte[] bytes = ((ByteChunk) chunk).getValue();
                String hex = Hex.encodeHexString(bytes);
                metadata.set(MAPI.CONVERSATION_INDEX, hex);
            }
        }

        List<Chunk> internetReferences = mainChunks
                .getAll()
                .get(MAPIProperty.INTERNET_REFERENCES);
        if (internetReferences != null) {
            for (Chunk ref : internetReferences) {
                if (ref instanceof StringChunk) {
                    metadata.add(MAPI.INTERNET_REFERENCES, ((StringChunk) ref).getValue());
                }
            }
        }
        List<Chunk> inReplyToIds = mainChunks
                .getAll()
                .get(MAPIProperty.IN_REPLY_TO_ID);
        if (inReplyToIds != null && !inReplyToIds.isEmpty()) {
            metadata.add(MAPI.IN_REPLY_TO_ID, inReplyToIds
                    .get(0)
                    .toString());
        }

        for (Map.Entry<MAPIProperty, Property> e : LITERAL_TIME_PROPERTIES.entrySet()) {
            List<PropertyValue> timeProp = mainChunks
                    .getProperties()
                    .get(e.getKey());
            if (timeProp != null && !timeProp.isEmpty()) {
                Calendar cal = ((PropertyValue.TimePropertyValue) timeProp.get(0)).getValue();
                metadata.set(e.getValue(), cal);
            }
        }

        MessageSubmissionChunk messageSubmissionChunk = mainChunks.getSubmissionChunk();
        if (messageSubmissionChunk != null) {
            String submissionId = messageSubmissionChunk.getSubmissionId();
            metadata.set(MAPI.SUBMISSION_ID, submissionId);
            metadata.set(MAPI.SUBMISSION_ACCEPTED_AT_TIME, messageSubmissionChunk.getAcceptedAtTime());
        }
    }


    private void handleGeneralDates(MAPIMessage msg, Map<String, String[]> headers, Metadata metadata) throws ChunkNotFoundException {
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
                            Date d = MailDateParser.parseDateLenient(date);
                            metadata.set(TikaCoreProperties.CREATED, d);
                            metadata.set(TikaCoreProperties.MODIFIED, d);
                        } catch (SecurityException e) {
                            throw e;
                        } catch (Exception e) {
                            // Store it as-is, and hope for the best...
                            metadata.set(TikaCoreProperties.CREATED, date);
                            metadata.set(TikaCoreProperties.MODIFIED, date);
                        }
                        break;
                    }
                }
            }
        }
        //try to overwrite the modified property if the actual LAST_MODIFICATION_TIME property exists.
        List<PropertyValue> timeProp = msg.getMainChunks().getProperties().get(MAPIProperty.LAST_MODIFICATION_TIME);
        if (timeProp != null && ! timeProp.isEmpty()) {
            Calendar cal = ((PropertyValue.TimePropertyValue)timeProp.get(0)).getValue();
            metadata.set(TikaCoreProperties.MODIFIED, cal);
        }

    }

    private void handleBodyChunks(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                  XHTMLContentHandler xhtml, Set<String> contentIdNames)
            throws SAXException, IOException, TikaException {

        if (extractAllAlternatives) {
            extractAllAlternatives(htmlChunk, rtfChunk, textChunk, xhtml, contentIdNames);
            return;
        }
        _handleBestBodyChunk(htmlChunk, rtfChunk, textChunk, xhtml, contentIdNames);

    }
    private void _handleBestBodyChunk(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                      XHTMLContentHandler xhtml, Set<String> contentIdNames)
            throws SAXException, IOException, TikaException {
        //try html, then rtf, then text
        if (htmlChunk != null) {
            byte[] data = null;
            if (htmlChunk instanceof ByteChunk) {
                data = ((ByteChunk) htmlChunk).getValue();
            } else if (htmlChunk instanceof StringChunk) {
                data = ((StringChunk) htmlChunk).getRawValue();
            }
            if (data != null) {
                Parser htmlParser = EmbeddedDocumentUtil
                        .tryToFindExistingLeafParser(JSoupParser.class, parseContext);
                if (htmlParser == null) {
                    htmlParser = new JSoupParser();
                }
                Metadata htmlMetadata = new Metadata();
                try (TikaInputStream tis = TikaInputStream.get(data)) {
                    htmlParser.parse(tis, new EmbeddedContentHandler(new BodyContentHandler(xhtml)), htmlMetadata, parseContext);
                }
                extractContentIdNamesFromHtml(data, htmlMetadata, contentIdNames);
                parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.HTML.name());
                return;
            }
        }
        if (rtfChunk != null) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            //avoid buffer underflow TIKA-2530
            //TODO -- would be good to find an example triggering file and
            //figure out if this is a bug in POI or a genuine 0 length chunk
            if (chunk.getValue() != null && chunk.getValue().length > 0) {
                MAPIRtfAttribute rtf =
                        new MAPIRtfAttribute(MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(),
                                chunk.getValue());
                RTFParser rtfParser = (RTFParser) EmbeddedDocumentUtil
                        .tryToFindExistingLeafParser(RTFParser.class, parseContext);
                if (rtfParser == null) {
                    rtfParser = new RTFParser();
                }
                Metadata rtfMetadata = new Metadata();
                try (TikaInputStream tis = TikaInputStream.get(rtf.getData())) {
                    rtfParser.parseInline(tis, xhtml, rtfMetadata, parseContext);
                }
                extractContentIdNamesFromRtf(rtf.getData(), rtfMetadata, contentIdNames);
                parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.RTF.name());
                parentMetadata.set(RTFMetadata.CONTAINS_ENCAPSULATED_HTML,
                        rtfMetadata.get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));
                return;
            }
        }
        if (textChunk != null) {
            String s = ((StringChunk) textChunk).getValue();
            xhtml.element("p", s);
            extractContentIdNamesFromText(s, contentIdNames);
            parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.TEXT.name());
        }

    }

    private void extractContentIdNamesFromRtf(byte[] data, Metadata metadata, Set<String> contentIdNames) {
        //for now, hope that there's encapsulated html
        //TODO: check for encapsulated html. If it doesn't exist, handle RTF specifically
        extractContentIdNamesFromHtml(data, metadata, contentIdNames);
    }

    private void extractContentIdNamesFromHtml(byte[] data, Metadata metadata, Set<String> contentIdNames) {
        String html = new String(data, UTF_8);
        Matcher imageMatcher = IMG_TAG_PATTERN.matcher(html);
        Matcher cidSrcMatcher = SRC_ATTR_PATTERN.matcher("");
        while (imageMatcher.find()) {
            String imgElementContents = imageMatcher.group(1);
            cidSrcMatcher.reset(imgElementContents);
            while (cidSrcMatcher.find()) {
                String cid = cidSrcMatcher.group(1);
                cid = cid.trim();
                contentIdNames.add(cid);
            }
        }
    }

    private void extractContentIdNamesFromText(String s, Set<String> contentIdNames) {
        Matcher m = TEXT_CID_PATTERN.matcher(s);
        while (m.find()) {
            contentIdNames.add(m.group(1));
        }
    }

    private void extractAllAlternatives(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                        XHTMLContentHandler xhtml, Set<String> contentIdNames)
            throws TikaException, SAXException, IOException {
        if (htmlChunk != null) {
            byte[] data = getValue(htmlChunk);
            if (data != null) {
                handleEmbeddedResource(TikaInputStream.get(data), "html-body", null,
                        MediaType.TEXT_HTML.toString(), xhtml, true);
                extractContentIdNamesFromHtml(data, new Metadata(), contentIdNames);
                parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.HTML.name());
            }
        }
        if (rtfChunk != null) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            MAPIRtfAttribute rtf =
                    new MAPIRtfAttribute(MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(),
                            chunk.getValue());

            byte[] data = rtf.getData();
            if (data != null) {
                Metadata rtfMetadata = new Metadata();
                handleEmbeddedResource(TikaInputStream.get(data), rtfMetadata,
                        "rtf-body", null, null,
                        "application/rtf", xhtml, true);
                extractContentIdNamesFromRtf(data, rtfMetadata, contentIdNames);
                //copy this info into the parent...what else should we copy?
                parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.RTF.name());
                parentMetadata.set(RTFMetadata.CONTAINS_ENCAPSULATED_HTML,
                        rtfMetadata.get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));

            }
        }
        if (textChunk != null) {
            byte[] data = getValue(textChunk);
            if (data != null) {
                Metadata chunkMetadata = new Metadata();
                chunkMetadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                        MediaType.TEXT_PLAIN.toString());
                handleEmbeddedResource(TikaInputStream.get(data), chunkMetadata, null, "text-body",
                        null, MediaType.TEXT_PLAIN.toString(), xhtml, true);
                if (textChunk instanceof StringChunk) {
                    extractContentIdNamesFromText(((StringChunk) textChunk).getValue(), contentIdNames);
                }
                parentMetadata.add(MAPI.BODY_TYPES_PROCESSED, BODY_TYPES_PROCESSED.TEXT.name());
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

    private void handleFromTo(Map<String, String[]> headers, Metadata metadata)
            throws ChunkNotFoundException {
        String from = msg.getDisplayFrom();
        metadata.set(TikaCoreProperties.CREATOR, from);
        metadata.set(Metadata.MESSAGE_FROM, from);
        metadata.set(Metadata.MESSAGE_TO, msg.getDisplayTo());
        metadata.set(Metadata.MESSAGE_CC, msg.getDisplayCC());
        metadata.set(Metadata.MESSAGE_BCC, msg.getDisplayBCC());


        Chunks chunks = msg.getMainChunks();
        StringChunk sentByServerType = chunks.getSentByServerType();
        if (sentByServerType != null) {
            metadata.set(MAPI.SENT_BY_SERVER_TYPE, sentByServerType.getValue());
        }

        Map<MAPIProperty, List<Chunk>> mainChunks = msg.getMainChunks().getAll();

        List<Chunk> senderAddresType = mainChunks.get(MAPIProperty.SENDER_ADDRTYPE);
        String senderAddressTypeString = "";
        if (senderAddresType != null && senderAddresType.size() > 0) {
            senderAddressTypeString = senderAddresType.get(0).toString();
        }

        //sometimes in SMTP .msg files there is an email in the sender name field.

        setFirstChunk(mainChunks.get(MAPIProperty.SENDER_NAME), Message.MESSAGE_FROM_NAME, metadata);
        setFirstChunk(mainChunks.get(MAPIProperty.SENT_REPRESENTING_NAME), MAPI.FROM_REPRESENTING_NAME, metadata);

        setFirstChunk(mainChunks.get(MAPIProperty.SENDER_EMAIL_ADDRESS), Message.MESSAGE_FROM_EMAIL, metadata);
        setFirstChunk(mainChunks.get(MAPIProperty.SENT_REPRESENTING_EMAIL_ADDRESS), MAPI.FROM_REPRESENTING_EMAIL, metadata);

        for (Recipient recipient : buildRecipients()) {
            switch (recipient.recipientType) {
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
                    vals = (vals == null) ? new ArrayList<>() : vals;
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
            vals = (vals == null) ? new ArrayList<>() : vals;
            vals.add(decodeHeader(sb.toString()));
            headers.put(lastKey, vals);
        }

        //convert to array
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            ret.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        return ret;

    }

    private String decodeHeader(String header) {
        return DecoderUtil.decodeEncodedWords(header, DecodeMonitor.SILENT);
    }

    private void header(XHTMLContentHandler xhtml, String key, String value) throws SAXException {
        if (value != null && value.length() > 0) {
            xhtml.element("dt", key);
            xhtml.element("dd", value);
        }
    }

    /**
     * Tries to identify the correct encoding for 7-bit (non-unicode)
     * strings in the file.
     * <p>Many messages store their strings as unicode, which is
     * nice and easy. Some use one-byte encodings for their
     * strings, but don't always store the encoding anywhere
     * helpful in the file.</p>
     * <p>This method checks for codepage properties, and failing that
     * looks at the headers for the message, and uses these to
     * guess the correct encoding for your file.</p>
     * <p>Bug #49441 has more on why this is needed</p>
     * <p>This is taken verbatim from POI (TIKA-1238)
     * as a temporary workaround to prevent unsupported encoding exceptions</p>
     */
    private void guess7BitEncoding(MAPIMessage msg) {
        Chunks mainChunks = msg.getMainChunks();
        //null check
        if (mainChunks == null) {
            return;
        }

        Map<MAPIProperty, List<PropertyValue>> props = mainChunks.getProperties();
        if (props != null) {
            // First choice is a codepage property
            for (MAPIProperty prop : new MAPIProperty[]{MAPIProperty.MESSAGE_CODEPAGE, MAPIProperty.INTERNET_CPID}) {
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
            if (headers != null && headers.length > 0) {
                // Look for a content type with a charset
                Pattern p = Pattern.compile("Content-Type:.*?charset=[\"']?([^;'\"]+)[\"']?", Pattern.CASE_INSENSITIVE);

                for (String header : headers) {
                    if (header.startsWith("Content-Type")) {
                        Matcher m = p.matcher(header);
                        if (m.matches()) {
                            // Found it! Tell all the string chunks
                            String charset = m.group(1);
                            if (tryToSet7BitEncoding(msg, charset)) {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (ChunkNotFoundException e) {
            //swallow
        }

        // Nothing suitable in the headers, try HTML
        // TODO: do we need to replicate this in Tika? If we wind up
        // parsing the html version of the email, this is duplicative??
        // Or do we need to reset the header strings based on the html
        // meta header if there is no other information?
        try {
            String html = msg.getHtmlBody();
            if (html != null && html.length() > 0) {
                Charset charset = null;
                try {
                    charset = detector.detect(UnsynchronizedByteArrayInputStream.builder().setByteArray(html.getBytes(UTF_8)).get(),
                            EMPTY_METADATA);
                } catch (IOException e) {
                    //swallow
                }
                if (charset != null && tryToSet7BitEncoding(msg, charset.name())) {
                    return;
                }
            }
        } catch (ChunkNotFoundException e) {
            //swallow
        }

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

    private void writeSelectHeadersInBody(Metadata metadata, MAPIMessage msg, XHTMLContentHandler xhtml)
            throws SAXException, ChunkNotFoundException {
        if (! officeParserConfig.isWriteSelectHeadersInBody()) {
            return;
        }
        String subject = metadata.get(TikaCoreProperties.TITLE);
        subject = (subject == null) ? "" : subject;
        xhtml.element("h1", subject);

        // Output the from and to details in text, as you
        //  often want them in text form for searching
        xhtml.startElement("dl");
        String from = metadata.get(Message.MESSAGE_FROM);
        if (from != null) {
            header(xhtml, "From", from);
        }
        header(xhtml, "To", msg.getDisplayTo());
        header(xhtml, "Cc", msg.getDisplayCC());
        header(xhtml, "Bcc", msg.getDisplayBCC());
        try {
            header(xhtml, "Recipients", msg.getRecipientEmailAddress());
        } catch (ChunkNotFoundException e) {
            //swallow
        }
        xhtml.endElement("dl");
    }

    private List<Recipient> buildRecipients() {
        RecipientChunks[] recipientChunks = msg.getRecipientDetailsChunks();
        if (recipientChunks == null) {
            return Collections.EMPTY_LIST;
        }
        List<Recipient> recipients = new LinkedList<>();

        for (RecipientChunks chunks : recipientChunks) {
            Recipient r = new Recipient();
            r.displayName = (chunks.getRecipientDisplayNameChunk() != null) ?
                    chunks.getRecipientDisplayNameChunk().toString() : null;
            r.name = (chunks.getRecipientNameChunk() != null) ?
                    chunks.getRecipientNameChunk().toString() :
                    null;
            r.emailAddress = chunks.getRecipientEmailAddress();
            List<PropertyValue> vals = chunks.getProperties().get(MAPIProperty.RECIPIENT_TYPE);

            RECIPIENT_TYPE recipientType = RECIPIENT_TYPE.UNSPECIFIED;
            if (vals != null && vals.size() > 0) {
                Object val = vals.get(0).getValue();
                if (val instanceof Integer) {
                    recipientType = RECIPIENT_TYPE.getTypeFromVal((int) val);
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

    public enum RECIPIENT_TYPE {
        TO(1), CC(2), BCC(3), UNRECOGNIZED(-1), UNSPECIFIED(-1);

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
    }


    private enum ADDRESS_TYPE {
        EX, SMTP
    }

    private static class Recipient {
        String name;
        String displayName;
        RECIPIENT_TYPE recipientType;
        String emailAddress;
        ADDRESS_TYPE addressType;
    }
}
