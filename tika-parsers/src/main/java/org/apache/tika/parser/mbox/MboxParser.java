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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mail.MailUtil;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Mbox (mailbox) parser. This version extracts each mail from Mbox and uses the
 * {@link org.apache.tika.parser.DelegatingParser} to process each mail.
 */
public class MboxParser extends AbstractParser {

    public static final String MBOX_MIME_TYPE = "application/mbox";
    public static final String RECORD_DIVIDER = "From ";
    public static final int MAIL_MAX_SIZE = 50_000_000;
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -1762689436731160661L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("mbox"));
    private static final Pattern EMAIL_HEADER_PATTERN = Pattern.compile("([^ ]+):[ \t]*(.*)");
    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("<(.*@.*)>");

    private static final String EMAIL_HEADER_METADATA_PREFIX = "MboxParser-";
    static final String EMAIL_FROMLINE_METADATA = EMAIL_HEADER_METADATA_PREFIX + "from";

    public static Date parseDate(String headerContent) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        return dateFormat.parse(headerContent);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Helper class to manage state while parsing mbox files
     */
    private static class ParserState {
        boolean isFirstRecord = true;
        boolean isBody = false;
        
        ByteArrayOutputStream currentMessage = null;
        StringBuilder currentHeader = null;
        Metadata currentMailMetadata = null;
        
        BufferedReader reader;
        // Null if we have not looked ahead yet, the next line otherwise
        String nextLine;
        
        private ParserState(BufferedReader reader) {
            this.reader = reader;
        }
        
        private String lookAhead() throws IOException {
            if (this.nextLine == null) {
                this.nextLine = reader.readLine();
            }
            
            return this.nextLine;
        }
        
        String readNextLine() throws IOException {
            if (this.nextLine == null) {
                return reader.readLine();
            }
            
            String temp = this.nextLine;
            this.nextLine = null;
            return temp;
        }

        void reset(String curLine) {
            isBody = false;
            currentMessage = new ByteArrayOutputStream(100_000);
            currentHeader = new StringBuilder();
            currentMailMetadata = new Metadata();
            currentMailMetadata.add(EMAIL_FROMLINE_METADATA, curLine.substring(RECORD_DIVIDER.length()));
            currentMailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822");
        }
    }
    
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {

        EmbeddedDocumentExtractor extractor = context.get(
                EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        String charsetName = "windows-1252";

        metadata.set(Metadata.CONTENT_TYPE, MBOX_MIME_TYPE);
        metadata.set(Metadata.CONTENT_ENCODING, charsetName);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        InputStreamReader isr = new InputStreamReader(stream, charsetName);
        try (BufferedReader reader = new BufferedReader(isr)) {
            ParserState state = new ParserState(reader);
            while (true) {
                String curLine = state.readNextLine();
                
                if (curLine == null) {
                    completeRecord(extractor, xhtml, state);
                    break;
                }
                
                if (isRecordDivider(curLine, state)) {
                    if (state.isFirstRecord) {
                        state.isFirstRecord = false;
                    } else {
                        completeRecord(extractor, xhtml, state);
                    }
                    
                    state.reset(curLine);
                    
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                } else if (curLine.isEmpty()) {
                    state.isBody = true;
                } else if (!state.isBody) {
                    // Append for multi-line messages
                    if (curLine.startsWith(" ") || curLine.startsWith("\t")) {
                        state.currentHeader.append(" ").append(curLine.trim());
                    } else {
                        // If it is non-empty and not prepended with whitespace, then it is the start
                        // of a new header, so save the last header
                        saveHeaderInMetadata(state.currentMailMetadata, state.currentHeader.toString());
                        state.currentHeader = new StringBuilder().append(curLine);
                    }
                }
                
                state.currentMessage.write(curLine.getBytes(charsetName));
                state.currentMessage.write(0x0A);
            }
        }
        
        xhtml.endDocument();
    }
    
    /**
     * Save the last recorded header into the current record's {@link Metadata}
     * and pass the completed record to the {@link EmbeddedDocumentExtractor}
     */
    private void completeRecord(EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml, ParserState state) throws SAXException, IOException {
        // Save the last header recorded
        saveHeaderInMetadata(state.currentMailMetadata, state.currentHeader.toString());
        
        // Extract the message if needed
        if (extractor.shouldParseEmbedded(state.currentMailMetadata)) {
            ByteArrayInputStream messageStream = new ByteArrayInputStream(state.currentMessage.toByteArray());
            extractor.parseEmbedded(messageStream, xhtml, state.currentMailMetadata, true);
        }
    }
    
    private boolean isRecordDivider(String line, ParserState state) throws IOException, TikaException {
        if (!line.startsWith(RECORD_DIVIDER)) {
            return false;
        }
        
        // If "From " is part of the message body, then RFC 4155 indicates that
        // different mbox formats may escape the message with e.g. '>' characters, etc
        // which would be parsed properly.  Here, we add handling if a "From" in a message body
        // is NOT escaped at all.
        String nextLine = state.lookAhead();
        if (nextLine == null) {
            throw new TikaException("Expected mbox to end with newline characters");
        }
        
        return nextLine.isEmpty() || EMAIL_HEADER_PATTERN.matcher(nextLine).matches();
    }

    private void saveHeaderInMetadata(Metadata metadata, String rawHeader) {
        Matcher headerMatcher = EMAIL_HEADER_PATTERN.matcher(rawHeader);
        if (!headerMatcher.matches()) {
            return; // ignore malformed header lines
        }

        String headerTag = headerMatcher.group(1).toLowerCase(Locale.ROOT);
        String headerContent = headerMatcher.group(2);

        switch (headerTag) {
        case "from":
            metadata.set(TikaCoreProperties.CREATOR, headerContent);
            MailUtil.setPersonAndEmail(
                    headerContent,
                    Message.MESSAGE_FROM_NAME,
                    Message.MESSAGE_FROM_EMAIL,
                    metadata);
            break;
        case "to":
        case "cc":
        case "bcc":
            Matcher address = EMAIL_ADDRESS_PATTERN.matcher(headerContent);
            if (address.find()) {
                metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, address.group(1));
            } else if (headerContent.indexOf('@') > -1) {
                metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, headerContent);
            }

            String property = Metadata.MESSAGE_TO;
            if (headerTag.equalsIgnoreCase("Cc")) {
                property = Metadata.MESSAGE_CC;
            } else if (headerTag.equalsIgnoreCase("Bcc")) {
                property = Metadata.MESSAGE_BCC;
            }
            metadata.add(property, headerContent);
            break;
        case "subject":
            metadata.add(Metadata.SUBJECT, headerContent);
            break;
        case "date":
            try {
                Date date = parseDate(headerContent);
                metadata.set(TikaCoreProperties.CREATED, date);
            } catch (ParseException e) {
                // ignoring date because format was not understood
            }
            break;
        case "message-id":
            metadata.set(TikaCoreProperties.IDENTIFIER, headerContent);
            break;
        case "in-reply-to":
            metadata.set(TikaCoreProperties.RELATION, headerContent);
            break;
        case "content-type":
            // TODO - key off content-type in headers to
            // set mapping to use for content and convert if necessary.

            metadata.add(Metadata.CONTENT_TYPE, headerContent);
            metadata.set(TikaCoreProperties.FORMAT, headerContent);
            break;
        default:
            metadata.add(EMAIL_HEADER_METADATA_PREFIX + headerTag, headerContent);
        }
    }
}
