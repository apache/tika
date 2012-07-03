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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Mbox (mailbox) parser. This version returns the headers for the first email
 * via metadata, which means headers from subsequent emails will be lost.
 */
public class MboxParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -1762689436731160661L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.application("mbox"));

    public static final String MBOX_MIME_TYPE = "application/mbox";
    public static final String MBOX_RECORD_DIVIDER = "From ";
    private static final Pattern EMAIL_HEADER_PATTERN = Pattern.compile("([^ ]+):[ \t]*(.*)");
    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("<(.*@.*)>");

    private static final String EMAIL_HEADER_METADATA_PREFIX = "MboxParser-";
    private static final String EMAIL_FROMLINE_METADATA = EMAIL_HEADER_METADATA_PREFIX + "from";

    private enum ParseStates {
        START, IN_HEADER, IN_CONTENT
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {

        InputStreamReader isr;
        try {
            // Headers are going to be 7-bit ascii
            isr = new InputStreamReader(stream, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new TikaException("US-ASCII is not supported!", e);
        }

        BufferedReader reader = new BufferedReader(isr);

        metadata.set(Metadata.CONTENT_TYPE, MBOX_MIME_TYPE);
        metadata.set(Metadata.CONTENT_ENCODING, "us-ascii");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ParseStates parseState = ParseStates.START;
        String multiLine = null;
        boolean inQuote = false;
        int numEmails = 0;

        // We're going to scan, line-by-line, for a line that starts with
        // "From "
        for (String curLine = reader.readLine(); curLine != null; curLine = reader.readLine()) {
            boolean newMessage = curLine.startsWith(MBOX_RECORD_DIVIDER);
            if (newMessage) {
                numEmails += 1;
            }

            switch (parseState) {
            case START:
                if (newMessage) {
                    parseState = ParseStates.IN_HEADER;
                    newMessage = false;
                    // Fall through to IN_HEADER
                } else {
                    break;
                }

            case IN_HEADER:
                if (newMessage) {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    multiLine = curLine;
                } else if (curLine.length() == 0) {
                    // Blank line is signal that we're transitioning to the content.
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    parseState = ParseStates.IN_CONTENT;

                    // Mimic what PackageParser does between entries.
                    xhtml.startElement("div", "class", "email-entry");
                    xhtml.startElement("p");
                    inQuote = false;
                } else if (curLine.startsWith(" ") || curLine.startsWith("\t")) {
                    multiLine += " " + curLine.trim();
                } else {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    multiLine = curLine;
                }

                break;

                // TODO - use real email parsing support so we can correctly handle
                // things like multipart messages and quoted-printable encoding.
                // We'd also want this for charset handling, where content isn't 7-bit
                // ascii.
            case IN_CONTENT:
                if (newMessage) {
                    endMessage(xhtml, inQuote);
                    parseState = ParseStates.IN_HEADER;
                    multiLine = curLine;
                } else {
                    boolean quoted = curLine.startsWith(">");
                    if (inQuote) {
                        if (!quoted) {
                            xhtml.endElement("q");
                            inQuote = false;
                        }
                    } else if (quoted) {
                        xhtml.startElement("q");
                        inQuote = true;
                    }

                    xhtml.characters(curLine);

                    // For plain text email, each line is a real break position.
                    xhtml.element("br", "");
                }
            }
        }

        if (parseState == ParseStates.IN_HEADER) {
            saveHeaderInMetadata(numEmails, metadata, multiLine);
        } else if (parseState == ParseStates.IN_CONTENT) {
            endMessage(xhtml, inQuote);
        }

        xhtml.endDocument();
    }

    private void endMessage(XHTMLContentHandler xhtml, boolean inQuote) throws SAXException {
        if (inQuote) {
            xhtml.endElement("q");
        }

        xhtml.endElement("p");
        xhtml.endElement("div");
    }

    private void saveHeaderInMetadata(int numEmails, Metadata metadata, String curLine) {
        if ((curLine == null) || (numEmails > 1)) {
            return;
        } else if (curLine.startsWith(MBOX_RECORD_DIVIDER)) {
            metadata.add(EMAIL_FROMLINE_METADATA, curLine.substring(MBOX_RECORD_DIVIDER.length()));
            return;
        }

        Matcher headerMatcher = EMAIL_HEADER_PATTERN.matcher(curLine);
        if (!headerMatcher.matches()) {
            return; // ignore malformed header lines
        }

        String headerTag = headerMatcher.group(1).toLowerCase();
        String headerContent = headerMatcher.group(2);

        if (headerTag.equalsIgnoreCase("From")) {
            metadata.set(TikaCoreProperties.CREATOR, headerContent);
        } else if (headerTag.equalsIgnoreCase("To") ||
        	headerTag.equalsIgnoreCase("Cc") ||
        	headerTag.equalsIgnoreCase("Bcc")) {
            Matcher address = EMAIL_ADDRESS_PATTERN.matcher(headerContent);
            if(address.find()) {
        	metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, address.group(1));
            } else if(headerContent.indexOf('@') > -1) {
        	metadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, headerContent);
            }
            
            String property = Metadata.MESSAGE_TO;
            if (headerTag.equalsIgnoreCase("Cc")) {
        	property = Metadata.MESSAGE_CC;
            } else if (headerTag.equalsIgnoreCase("Bcc")) {
        	property = Metadata.MESSAGE_BCC;
            }
            metadata.add(property, headerContent);
        } else if (headerTag.equalsIgnoreCase("Subject")) {
            // TODO Move to title in Tika 2.0
            metadata.add(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE, 
                    headerContent);
        } else if (headerTag.equalsIgnoreCase("Date")) {
            try {
                Date date = parseDate(headerContent);
                metadata.set(TikaCoreProperties.CREATED, date);
            } catch (ParseException e) {
                // ignoring date because format was not understood
            }
        } else if (headerTag.equalsIgnoreCase("Message-Id")) {
            metadata.set(TikaCoreProperties.IDENTIFIER, headerContent);
        } else if (headerTag.equalsIgnoreCase("In-Reply-To")) {
            metadata.set(TikaCoreProperties.RELATION, headerContent);
        } else if (headerTag.equalsIgnoreCase("Content-Type")) {
            // TODO - key off content-type in headers to
            // set mapping to use for content and convert if necessary.

            metadata.add(Metadata.CONTENT_TYPE, headerContent);
            metadata.set(TikaCoreProperties.FORMAT, headerContent);
        } else {
            metadata.add(EMAIL_HEADER_METADATA_PREFIX + headerTag, headerContent);
        }
    }
    
    public static Date parseDate(String headerContent) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        return dateFormat.parse(headerContent);
    }

}
