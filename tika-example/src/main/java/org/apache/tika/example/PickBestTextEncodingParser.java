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
package org.apache.tika.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.NonDetectingEncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * Inspired by TIKA-1443 and https://wiki.apache.org/tika/CompositeParserDiscussion
 * this tries several different text encodings, then does the real
 * text parsing based on which is "best".
 * <p>
 * The logic for "best" needs a lot of work!
 * <p>
 * This is not recommended for actual production use... It is mostly to
 * prove that the {@link AbstractMultipleParser} environment is
 * sufficient to support this use-case
 * <p>
 * TODO Implement proper "Junk" detection
 *
 * @deprecated Currently not suitable for real use, more a demo / prototype!
 */
public class PickBestTextEncodingParser extends AbstractMultipleParser {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 730345169223211807L;

    /**
     * Which charsets we should try
     */
    private String[] charsetsToTry;

    public PickBestTextEncodingParser(MediaTypeRegistry registry, String[] charsets) {
        super(registry, MetadataPolicy.DISCARD_ALL, makeParsers(charsets));
        this.charsetsToTry = charsets;
    }

    private static List<Parser> makeParsers(String[] charsets) {
        // One more TXTParser than we have charsets, for the real thing
        List<Parser> parsers = new ArrayList<>(charsets.length + 1);
        for (int i = 0; i < charsets.length + 1; i++) {
            parsers.set(i, new TXTParser());
        }
        return parsers;
    }

    @Override
    protected void parserPrepare(Parser parser, Metadata metadata, ParseContext context) {
        super.parserPrepare(parser, metadata, context);

        // Specify which charset to try
        String charset = context.get(CharsetTester.class).getNextCharset();
        Charset charsetCS = Charset.forName(charset);
        context.set(EncodingDetector.class, new NonDetectingEncodingDetector(charsetCS));
    }

    @Override
    protected boolean parserCompleted(Parser parser, Metadata metadata, ContentHandler handler,
                                      ParseContext context, Exception exception) {
        // Get the current charset
        CharsetTester charsetTester = context.get(CharsetTester.class);
        String charset = charsetTester.getCurrentCharset();

        // Record the text
        if (charsetTester.stillTesting()) {
            charsetTester.charsetText.put(charset, handler.toString());

            // If this was the last real charset, see which one is best
            // TODO Do this in a more generic, less english-only way!
            if (!charsetTester.moreToTest()) {
                int numEnglish = 0;
                String bestcharset = null;
                for (String pcharset : charsetTester.charsetText.keySet()) {
                    String text = charsetTester.charsetText.get(pcharset);
                    int cEnglish = 0;
                    for (char c : text.toCharArray()) {
                        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                                (c >= '0' && c <= '9')) {
                            cEnglish++;
                        }
                    }
                    if (cEnglish > numEnglish) {
                        numEnglish = cEnglish;
                        bestcharset = pcharset;
                    }
                }
                charsetTester.pickedCharset = bestcharset;
            }
        }

        // Always have the next parser tried
        return true;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata originalMetadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Use a BodyContentHandler for each of the charset test,
        //  then their real ContentHandler for the last one
        CharsetContentHandlerFactory handlerFactory = new CharsetContentHandlerFactory();
        handlerFactory.handler = handler;

        // Put something on the ParseContext to get the charset
        context.set(CharsetTester.class, new CharsetTester());

        // Have the parsing done
        super.parse(stream, handlerFactory, originalMetadata, context);
    }

    @Override
    public void parse(InputStream stream, ContentHandlerFactory handlers, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // We only work with one ContentHandler as far as the user is
        //  concerned, any others are purely internal!
        parse(stream, handlers.getNewContentHandler(), metadata, context);
    }

    protected class CharsetContentHandlerFactory implements ContentHandlerFactory {
        // Which one we're on
        private int index = -1;
        // The real one for at the end
        private ContentHandler handler;

        @Override
        public ContentHandler getNewContentHandler() {
            index++;
            if (index < charsetsToTry.length) {
                return new BodyContentHandler();
            }
            return handler;
        }
        @Override
        public ContentHandler getNewContentHandler(OutputStream os, Charset charset) {
            return getNewContentHandler();
        }
    }

    protected class CharsetTester {
        /**
         * Our current charset's index
         */
        private int index = -1;

        /**
         * What charset we felt was best
         */
        private String pickedCharset;
        /**
         * What text we got for each charset, so we can test for the best
         */
        private Map<String, String> charsetText = new HashMap<>();

        protected String getNextCharset() {
            index++;
            return getCurrentCharset();
        }

        protected String getCurrentCharset() {
            if (index < charsetsToTry.length) {
                return charsetsToTry[index];
            }
            return pickedCharset;
        }

        protected boolean stillTesting() {
            return index < charsetsToTry.length;
        }

        protected boolean moreToTest() {
            return index < charsetsToTry.length - 1;
        }
    }
}
