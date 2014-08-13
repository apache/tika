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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Mbox (mailbox) parser. This version extracts each mail from Mbox and uses the
 * DelegatingParser to process each mail.
 */
public class MboxParser extends AbstractParser {

  /** Serial version UID */
  private static final long serialVersionUID = -1762689436731160661L;

  private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("mbox"));

  public static final String MBOX_MIME_TYPE = "application/mbox";
  public static final String MBOX_RECORD_DIVIDER = "From ";
  public static final int MAIL_MAX_SIZE = 50000000;

  private static final Pattern EMAIL_HEADER_PATTERN = Pattern.compile("([^ ]+):[ \t]*(.*)");
  private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("<(.*@.*)>");

  private static final String EMAIL_HEADER_METADATA_PREFIX = "MboxParser-";
  private static final String EMAIL_FROMLINE_METADATA = EMAIL_HEADER_METADATA_PREFIX + "from";

  private boolean tracking = false;
  private final Map<Integer, Metadata> trackingMetadata = new HashMap<Integer, Metadata>();

  public Set<MediaType> getSupportedTypes(ParseContext context) {
    return SUPPORTED_TYPES;
  }

  public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
      throws IOException, TikaException, SAXException {

    EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
        new ParsingEmbeddedDocumentExtractor(context));

    String charsetName = "windows-1252";

    metadata.set(Metadata.CONTENT_TYPE, MBOX_MIME_TYPE);
    metadata.set(Metadata.CONTENT_ENCODING, charsetName);

    XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
    xhtml.startDocument();

    InputStreamReader isr = new InputStreamReader(stream, charsetName);
    BufferedReader reader = new BufferedReader(isr);
    try {
      String curLine = reader.readLine();
      int mailItem = 0;
      do {
        if (curLine.startsWith(MBOX_RECORD_DIVIDER)) {
          Metadata mailMetadata = new Metadata();
          Queue<String> multiline = new LinkedList<String>();
          mailMetadata.add(EMAIL_FROMLINE_METADATA, curLine.substring(MBOX_RECORD_DIVIDER.length()));
          mailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822");
          curLine = reader.readLine();

          ByteArrayOutputStream message = new ByteArrayOutputStream(100000);
          do {
            if (curLine.startsWith(" ") || curLine.startsWith("\t")) {
              String latestLine = multiline.poll();
              latestLine += " " + curLine.trim();
              multiline.add(latestLine);
            } else {
              multiline.add(curLine);
            }

            message.write(curLine.getBytes(charsetName));
            message.write(0x0A);
            curLine = reader.readLine();
          } while (curLine != null && !curLine.startsWith(MBOX_RECORD_DIVIDER) && message.size() < MAIL_MAX_SIZE);

          for (String item : multiline) {
            saveHeaderInMetadata(mailMetadata, item);
          }

          ByteArrayInputStream messageStream = new ByteArrayInputStream(message.toByteArray());
          message = null;

          if (extractor.shouldParseEmbedded(mailMetadata)) {
            extractor.parseEmbedded(messageStream, xhtml, mailMetadata, true);
          }

          if (tracking) {
            getTrackingMetadata().put(mailItem++, mailMetadata);
          }
        } else {
          curLine = reader.readLine();
        }

      } while (curLine != null && !Thread.currentThread().isInterrupted());

    } finally {
      reader.close();
    }

    xhtml.endDocument();
  }

  public static Date parseDate(String headerContent) throws ParseException {
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
    return dateFormat.parse(headerContent);
  }

  public boolean isTracking() {
    return tracking;
  }

  public void setTracking(boolean tracking) {
    this.tracking = tracking;
  }

  public Map<Integer, Metadata> getTrackingMetadata() {
    return trackingMetadata;
  }

  private void saveHeaderInMetadata(Metadata metadata, String curLine) {
    Matcher headerMatcher = EMAIL_HEADER_PATTERN.matcher(curLine);
    if (!headerMatcher.matches()) {
      return; // ignore malformed header lines
    }

    String headerTag = headerMatcher.group(1).toLowerCase(Locale.ROOT);
    String headerContent = headerMatcher.group(2);

    if (headerTag.equalsIgnoreCase("From")) {
      metadata.set(TikaCoreProperties.CREATOR, headerContent);
    } else if (headerTag.equalsIgnoreCase("To") || headerTag.equalsIgnoreCase("Cc")
        || headerTag.equalsIgnoreCase("Bcc")) {
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
    } else if (headerTag.equalsIgnoreCase("Subject")) {
      metadata.add(Metadata.SUBJECT, headerContent);
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
}
