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
package org.apache.tika.parser.apple;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSSet;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.dd.plist.UID;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Apple's plist and bplist. This is a wrapper around
 * com.googlecode.plist:dd-plist
 * <p>
 * As of 1.25, Tika does not have detection for the text based plist,
 * so those files will not be directed to this parser
 *
 * @since 1.25
 */
public class PListParser extends AbstractParser {

    private static final String ARR = "array";
    private static final String DATA = "data";
    private static final String DATE = "date";
    private static final String DICT = "dict";
    private static final String KEY = "key";
    private static final String NUMBER = "number";
    private static final String PLIST = "plist";
    private static final String SET = "set";
    private static final String STRING = "string";
    private static final String UID = "uid";


    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(BPListDetector.BITUNES, BPListDetector.BMEMGRAPH, BPListDetector.BPLIST,
                    BPListDetector.BWEBARCHIVE, BPListDetector.PLIST)));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        NSObject rootObj = null;
        //if this already went through the PListDetector,
        //there should be an NSObject in the open container
        if (stream instanceof TikaInputStream) {
            rootObj = (NSObject) ((TikaInputStream) stream).getOpenContainer();
        }

        if (rootObj == null) {
            try {
                if (stream instanceof TikaInputStream && ((TikaInputStream) stream).hasFile()) {
                    rootObj = PropertyListParser.parse(((TikaInputStream) stream).getFile());
                } else {
                    rootObj = PropertyListParser.parse(stream);
                }
            } catch (PropertyListFormatException | ParseException |
                    ParserConfigurationException e) {
                throw new TikaException("problem parsing root", e);
            }
        }
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (BPListDetector.PLIST.toString().equals(contentType)) {
            if (rootObj instanceof NSDictionary) {
                MediaType subtype =
                        BPListDetector.detectXMLOnKeys(((NSDictionary) rootObj).keySet());
                metadata.set(Metadata.CONTENT_TYPE, subtype.toString());
            }
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        State state = new State(xhtml, metadata, embeddedDocumentExtractor, df);
        xhtml.startDocument();
        xhtml.startElement(PLIST);
        parseObject(rootObj, state);
        xhtml.endElement(PLIST);
        xhtml.endDocument();
    }

    private void parseObject(NSObject obj, State state) throws SAXException, IOException {

        if (obj instanceof NSDictionary) {
            parseDict((NSDictionary) obj, state);
        } else if (obj instanceof NSArray) {
            NSArray nsArray = (NSArray) obj;
            state.xhtml.startElement(ARR);
            for (NSObject child : nsArray.getArray()) {
                parseObject(child, state);
            }
            state.xhtml.endElement(ARR);
        } else if (obj instanceof NSString) {
            state.xhtml.startElement(STRING);
            state.xhtml.characters(((NSString) obj).getContent());
            state.xhtml.endElement(STRING);
        } else if (obj instanceof NSNumber) {
            state.xhtml.startElement(NUMBER);
            state.xhtml.characters(((NSNumber) obj).toString());
            state.xhtml.endElement(NUMBER);
        } else if (obj instanceof NSData) {
            state.xhtml.startElement(DATA);
            handleData((NSData) obj, state);
            state.xhtml.endElement(DATA);
        } else if (obj instanceof NSDate) {
            state.xhtml.startElement(DATE);
            String dateString = state.dateFormat.format(((NSDate) obj).getDate());
            state.xhtml.characters(dateString);
            state.xhtml.endElement(DATE);
        } else if (obj instanceof NSSet) {
            state.xhtml.startElement(SET);
            parseSet((NSSet) obj, state);
            state.xhtml.endElement(SET);
        } else if (obj instanceof UID) {
            //do we want to do anything with obj.getBytes()
            state.xhtml.element(UID, ((UID) obj).getName());
        } else {
            throw new UnsupportedOperationException(
                    "don't yet support this type of object: " + obj.getClass() +
                            " Please open an issue on our tracker");
        }
    }

    private void parseSet(NSSet obj, State state) throws SAXException, IOException {
        state.xhtml.startElement(SET);
        for (NSObject child : obj.allObjects()) {
            parseObject(child, state);
        }
        state.xhtml.endElement(SET);
    }

    private void parseDict(NSDictionary obj, State state) throws SAXException, IOException {
        state.xhtml.startElement(DICT);
        for (Map.Entry<String, NSObject> mapEntry : obj.getHashMap().entrySet()) {
            String key = mapEntry.getKey();
            NSObject value = mapEntry.getValue();
            state.xhtml.element(KEY, key);
            parseObject(value, state);
        }
        state.xhtml.endElement(DICT);
    }

    private void handleData(NSData value, State state) throws IOException, SAXException {
        state.xhtml.characters(value.getBase64EncodedData());
        Metadata embeddedMetadata = new Metadata();
        if (!state.embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            return;
        }

        try (TikaInputStream tis = TikaInputStream.get(value.bytes())) {
            state.embeddedDocumentExtractor
                    .parseEmbedded(tis, state.xhtml, embeddedMetadata, true);
        }
    }

    private static class State {
        final XHTMLContentHandler xhtml;
        final Metadata metadata;
        final EmbeddedDocumentExtractor embeddedDocumentExtractor;
        final DateFormat dateFormat;

        public State(XHTMLContentHandler xhtml, Metadata metadata,
                     EmbeddedDocumentExtractor embeddedDocumentExtractor, DateFormat df) {
            this.xhtml = xhtml;
            this.metadata = metadata;
            this.embeddedDocumentExtractor = embeddedDocumentExtractor;
            this.dateFormat = df;
        }
    }
}
