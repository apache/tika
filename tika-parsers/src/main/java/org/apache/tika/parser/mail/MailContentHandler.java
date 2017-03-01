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
package org.apache.tika.parser.mail;

import static org.apache.tika.utils.DateUtils.MIDDAY;
import static org.apache.tika.utils.DateUtils.UTC;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.dom.field.MailboxListField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Bridge between mime4j's content handler and the generic Sax content handler
 * used by Tika. See
 * http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/ContentHandler.html
 */
class MailContentHandler implements ContentHandler {

    //TIKA-1970 Mac Mail's format
    private static final Pattern GENERAL_TIME_ZONE_NO_MINUTES_PATTERN =
            Pattern.compile("(?:UTC|GMT)([+-])(\\d?\\d)\\Z");

    //find a time ending in am/pm without a space: 10:30am and
    //use this pattern to insert space: 10:30 am
    private static final Pattern AM_PM = Pattern.compile("(?i)(\\d)([ap]m)\\b");

    private static final DateFormat[] ALTERNATE_DATE_FORMATS = new DateFormat[] {
            //note that the string is "cleaned" before processing:
            //1) condense multiple whitespace to single space
            //2) trim()
            //3) strip out commas
            //4) insert space before am/pm

            //May 16 2016 1:32am
            createDateFormat("MMM dd yy hh:mm a", null),

            //this is a standard pattern handled by mime4j;
            //but mime4j fails with leading whitespace
            createDateFormat("EEE d MMM yy HH:mm:ss Z", UTC),

            createDateFormat("EEE d MMM yy HH:mm:ss z", UTC),

            createDateFormat("EEE d MMM yy HH:mm:ss", null),// no timezone

            createDateFormat("EEEEE MMM d yy hh:mm a", null),// Sunday, May 15 2016 1:32 PM

            //16 May 2016 at 09:30:32  GMT+1 (Mac Mail TIKA-1970)
            createDateFormat("d MMM yy 'at' HH:mm:ss z", UTC),   // UTC/Zulu

            createDateFormat("yy-MM-dd HH:mm:ss", null),

            createDateFormat("MM/dd/yy hh:mm a", null, false),

            //now dates without times
            createDateFormat("MMM d yy", MIDDAY, false),
            createDateFormat("EEE d MMM yy", MIDDAY, false),
            createDateFormat("d MMM yy", MIDDAY, false),
            createDateFormat("yy/MM/dd", MIDDAY, false),
            createDateFormat("MM/dd/yy", MIDDAY, false)
    };

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        return createDateFormat(format, timezone, true);
    }

    private static DateFormat createDateFormat(String format, TimeZone timezone, boolean isLenient) {
        SimpleDateFormat sdf =
                new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        sdf.setLenient(isLenient);
        return sdf;
    }

    private boolean strictParsing = false;

    private XHTMLContentHandler handler;
    private Metadata metadata;
    private EmbeddedDocumentExtractor extractor;

    private boolean inPart = false;

    MailContentHandler(XHTMLContentHandler xhtml, Metadata metadata, ParseContext context, boolean strictParsing) {
        this.handler = xhtml;
        this.metadata = metadata;
        this.strictParsing = strictParsing;

        // Fetch / Build an EmbeddedDocumentExtractor with which
        //  to handle/process the parts/attachments

        // Was an EmbeddedDocumentExtractor explicitly supplied?
        this.extractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
    }

    public void body(BodyDescriptor body, InputStream is) throws MimeException,
            IOException {
        // use a different metadata object
        // in order to specify the mime type of the
        // sub part without damaging the main metadata

        Metadata submd = new Metadata();
        submd.set(Metadata.CONTENT_TYPE, body.getMimeType());
        submd.set(Metadata.CONTENT_ENCODING, body.getCharset());

        try {
            if (extractor.shouldParseEmbedded(submd)) {
                // Wrap the InputStream before passing on, as the James provided
                //  one misses many features we might want eg mark/reset
                TikaInputStream tis = TikaInputStream.get(is);
                extractor.parseEmbedded(tis, handler, submd, false);
            }
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endBodyPart() throws MimeException {
        try {
            handler.endElement("p");
            handler.endElement("div");
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endHeader() throws MimeException {
    }

    public void startMessage() throws MimeException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endMessage() throws MimeException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void endMultipart() throws MimeException {
        inPart = false;
    }

    public void epilogue(InputStream is) throws MimeException, IOException {
    }

    /**
     * Header for the whole message or its parts
     *
     * @see <a href="http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/">
     *     http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/</a>
     * Field.html
     */
    public void field(Field field) throws MimeException {
        // inPart indicates whether these metadata correspond to the
        // whole message or its parts
        if (inPart) {
            return;
        }

        try {
            String fieldname = field.getName();

            ParsedField parsedField = LenientFieldParser.getParser().parse(
                    field, DecodeMonitor.SILENT);
            if (fieldname.equalsIgnoreCase("From")) {
                MailboxListField fromField = (MailboxListField) parsedField;
                MailboxList mailboxList = fromField.getMailboxList();
                if (fromField.isValidField() && mailboxList != null) {
                    for (Address address : mailboxList) {
                        String from = getDisplayString(address);
                        MailUtil.setPersonAndEmail(from, Message.MESSAGE_FROM_NAME,
                                Message.MESSAGE_FROM_EMAIL, metadata);
                        metadata.add(Metadata.MESSAGE_FROM, from);
                        metadata.add(TikaCoreProperties.CREATOR, from);
                    }
                } else {
                    String from = stripOutFieldPrefix(field, "From:");
                    MailUtil.setPersonAndEmail(from, Message.MESSAGE_FROM_NAME,
                            Message.MESSAGE_FROM_EMAIL, metadata);

                    if (from.startsWith("<")) {
                        from = from.substring(1);
                    }
                    if (from.endsWith(">")) {
                        from = from.substring(0, from.length() - 1);
                    }
                    metadata.add(Metadata.MESSAGE_FROM, from);
                    metadata.add(TikaCoreProperties.CREATOR, from);
                }
            } else if (fieldname.equalsIgnoreCase("Subject")) {
                metadata.set(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE,
                        ((UnstructuredField) parsedField).getValue());
            } else if (fieldname.equalsIgnoreCase("To")) {
                processAddressList(parsedField, "To:", Metadata.MESSAGE_TO);
            } else if (fieldname.equalsIgnoreCase("CC")) {
                processAddressList(parsedField, "Cc:", Metadata.MESSAGE_CC);
            } else if (fieldname.equalsIgnoreCase("BCC")) {
                processAddressList(parsedField, "Bcc:", Metadata.MESSAGE_BCC);
            } else if (fieldname.equalsIgnoreCase("Date")) {
                DateTimeField dateField = (DateTimeField) parsedField;
                Date date = dateField.getDate();
                if (date == null) {
                    date = tryOtherDateFormats(field.getBody());
                }
                metadata.set(TikaCoreProperties.CREATED, date);
            } else {
                metadata.add(Metadata.MESSAGE_RAW_HEADER_PREFIX+parsedField.getName(),
                        field.getBody());
            }
        } catch (RuntimeException me) {
            if (strictParsing) {
                throw me;
            }
        }
    }

    private static synchronized Date tryOtherDateFormats(String text) {
        if (text == null) {
            return null;
        }
        text = text.replaceAll("\\s+", " ").trim();
        //strip out commas
        text = text.replaceAll(",", "");

        Matcher matcher = GENERAL_TIME_ZONE_NO_MINUTES_PATTERN.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceFirst("GMT$1$2:00");
        }

        matcher = AM_PM.matcher(text);
        if (matcher.find()) {
            text = matcher.replaceFirst("$1 $2");
        }

        for (DateFormat format : ALTERNATE_DATE_FORMATS) {
            try {
                return format.parse(text);
            } catch (ParseException e) {
            }
        }
        return null;
    }

    private void processAddressList(ParsedField field, String addressListType,
                                    String metadataField) throws MimeException {
        AddressListField toField = (AddressListField) field;
        if (toField.isValidField()) {
            AddressList addressList = toField.getAddressList();
            for (Address address : addressList) {
                metadata.add(metadataField, getDisplayString(address));
            }
        } else {
            String to = stripOutFieldPrefix(field,
                    addressListType);
            for (String eachTo : to.split(",")) {
                metadata.add(metadataField, eachTo.trim());
            }
        }
    }

    private String getDisplayString(Address address) {
        if (address instanceof Mailbox) {
            Mailbox mailbox = (Mailbox) address;
            String name = mailbox.getName();
            if (name != null && name.length() > 0) {
                name = DecoderUtil.decodeEncodedWords(name, DecodeMonitor.SILENT);
                return name + " <" + mailbox.getAddress() + ">";
            } else {
                return mailbox.getAddress();
            }
        } else {
            return address.toString();
        }
    }

    public void preamble(InputStream is) throws MimeException, IOException {
    }

    public void raw(InputStream is) throws MimeException, IOException {
    }

    public void startBodyPart() throws MimeException {
        try {
            handler.startElement("div", "class", "email-entry");
            handler.startElement("p");
        } catch (SAXException e) {
            throw new MimeException(e);
        }
    }

    public void startHeader() throws MimeException {
        // TODO Auto-generated method stub

    }

    public void startMultipart(BodyDescriptor descr) throws MimeException {
        inPart = true;
    }

    private String stripOutFieldPrefix(Field field, String fieldname) {
        String temp = field.getRaw().toString();
        int loc = fieldname.length();
        while (temp.charAt(loc) == ' ') {
            loc++;
        }
        return temp.substring(loc);
    }

}
