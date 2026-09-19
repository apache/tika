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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.MimeConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Uses apache-mime4j to parse emails. Each part is treated with the
 * corresponding parser and displayed within elements.
 * <p/>
 * Also handles Apple Mail's emlx framing: a first line carrying the message's
 * byte count, and an XML plist after the message. Both are dropped when the
 * count checks out; an edited file whose count no longer lands on the plist
 * is parsed whole rather than cut.
 * <p/>
 * A {@link MimeConfig} object can be passed in the parsing context
 * to better control the parsing process.
 *
 * @author jnioche@digitalpebble.com
 */
@TikaComponent(name = "rfc822-parser")
public class RFC822Parser implements Parser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -5504243905998074168L;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        private boolean extractAllAlternatives = false;

        public boolean isExtractAllAlternatives() {
            return extractAllAlternatives;
        }

        public void setExtractAllAlternatives(boolean extractAllAlternatives) {
            this.extractAllAlternatives = extractAllAlternatives;
        }
    }

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.parse("message/rfc822"),
                    MediaType.parse("message/x-emlx"))));

    // digits, then Apple Mail's space padding to 10 columns, then the newline
    private static final int EMLX_COUNT_LINE_MAX = 11;

    private static final byte[] PLIST_START = "<?xml".getBytes(US_ASCII);

    //rely on the detector to be thread-safe
    //built lazily and then reused
    private Detector detector;

    private Config defaultConfig = new Config();

    public RFC822Parser() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public RFC822Parser(Config config) {
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public RFC822Parser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Get the mime4j configuration, or use a default one
        MimeConfig config =
                new MimeConfig.Builder().setMaxLineLen(100000).setMaxHeaderLen(100000).build();

        config = context.get(MimeConfig.class, config);
        Detector localDetector = context.get(Detector.class);
        if (localDetector == null) {
            //lazily load this if necessary
            if (detector == null) {
                detector = EmbeddedDocumentUtil.getDetector(context);
            }
            localDetector = detector;
        }
        MimeStreamParser parser =
                new MimeStreamParser(config, null, new DefaultBodyDescriptorBuilder());
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);

        MailContentHandler mch = new MailContentHandler(xhtml, localDetector, metadata, context,
                config.isStrictParsing(), defaultConfig.isExtractAllAlternatives());
        parser.setContentHandler(mch);
        parser.setContentDecoding(true);
        parser.setNoRecurse();
        xhtml.startDocument();
        checkForZeroByte(tis);//avoid stackoverflow
        try {
            parser.parse(stripEmlxFraming(tis));
        } catch (IOException e) {
            tis.throwIfCauseOf(e);
            throw new TikaException("Failed to parse an email message", e);
        } catch (MimeException e) {
            // Unwrap the exception in case it was not thrown by mime4j
            Throwable cause = e.getCause();
            if (cause instanceof TikaException) {
                throw (TikaException) cause;
            } else if (cause instanceof SAXException) {
                throw (SAXException) cause;
            } else {
                throw new TikaException("Failed to parse an email message", e);
            }
        }
        xhtml.endDocument();
    }

    /**
     * Consumes the emlx byte-count line if the stream starts with one, and bounds the
     * message to that count when the plist really starts there.
     *
     * @return the stream to hand to mime4j
     */
    private static InputStream stripEmlxFraming(TikaInputStream tis) throws IOException {
        byte[] head = new byte[EMLX_COUNT_LINE_MAX];
        int n = tis.peek(head);
        long count = 0;
        int digits = 0;
        boolean padding = false;
        int lineLength = -1;
        for (int i = 0; i < n; i++) {
            int c = head[i];
            if (c >= '0' && c <= '9' && !padding) {
                count = count * 10 + (c - '0');
                digits++;
            } else if (c == ' ' && digits > 0) {
                padding = true;
            } else if (c == '\n' && digits > 0) {
                lineLength = i + 1;
                break;
            } else {
                break;
            }
        }
        if (lineLength < 0) {
            return tis;
        }
        boolean verified = plistStartsAt(tis, lineLength + count);
        IOUtils.skipFully(tis, lineLength);
        return verified ? new BoundedInputStream(count, tis) : tis;
    }

    private static boolean plistStartsAt(TikaInputStream tis, long offset) {
        // must run before any byte is consumed: a passthrough source refuses a seekable
        // view once its position has moved
        try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
            if (offset + PLIST_START.length > channel.size()) {
                return false;
            }
            ByteBuffer buffer = ByteBuffer.allocate(PLIST_START.length);
            channel.position(offset);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // fill
            }
            return !buffer.hasRemaining() && Arrays.equals(buffer.array(), PLIST_START);
        } catch (IOException e) {
            // unverifiable is not a reason to cut; a real read failure recurs in mime4j
            return false;
        }
    }

    private void checkForZeroByte(TikaInputStream tstream) throws IOException, ZeroByteFileException {
        tstream.mark(1);
        try {
            if (tstream.read() < 0) {
                throw new ZeroByteFileException("rfc822 parser found zero bytes");
            }
        } finally {
            tstream.reset();
        }
    }

    public Config getDefaultConfig() {
        return defaultConfig;
    }
}
