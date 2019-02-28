/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.csv;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.config.Field;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TextAndCSVParser extends AbstractEncodingDetectorParser {

    private static final String CSV_PREFIX = "csv";
    private static final String CHARSET = "charset";
    private static final String DELIMITER = "delimiter";
    public static final Property DELIMITER_PROPERTY = Property.externalText(
            CSV_PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER+DELIMITER);

    private static final String TD = "td";
    private static final String TR = "tr";
    private static final String TABLE = "table";

    static final MediaType CSV = MediaType.text("csv");
    static final MediaType TSV = MediaType.text("tsv");

    private static final int DEFAULT_MARK_LIMIT = 20000;

    private static final char[] DEFAULT_DELIMITERS = new char[]{',', '\t'};

    private static final Map<Character, String> CHAR_TO_STRING_DELIMITER_MAP = new HashMap<>();
    private static final Map<String, Character> STRING_TO_CHAR_DELIMITER_MAP = new HashMap<>();

    static {
        CHAR_TO_STRING_DELIMITER_MAP.put(',', "comma");
        CHAR_TO_STRING_DELIMITER_MAP.put('\t', "tab");
        CHAR_TO_STRING_DELIMITER_MAP.put('|', "pipe");
        CHAR_TO_STRING_DELIMITER_MAP.put(';', "semicolon");
        CHAR_TO_STRING_DELIMITER_MAP.put(':', "colon");
    }

    static {
        for (Map.Entry<Character, String> e : CHAR_TO_STRING_DELIMITER_MAP.entrySet()) {
            STRING_TO_CHAR_DELIMITER_MAP.put(e.getValue(), e.getKey());
        }
    }
    public TextAndCSVParser() {
        super();
    }

    public TextAndCSVParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }
    private char[] delimiters = DEFAULT_DELIMITERS;

    @Field
    private int markLimit = DEFAULT_MARK_LIMIT;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    CSV, TSV, MediaType.TEXT_PLAIN)));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        CSVParams params = getOverride(metadata);
        Reader reader = null;
        Charset charset = null;
        if (! params.isComplete()) {
            reader = detect(params, stream, metadata, context);
            if (params.getCharset() != null) {
                charset = params.getCharset();
            } else {
                charset = ((AutoDetectReader) reader).getCharset();
            }
        } else {
            reader = new BufferedReader(new InputStreamReader(stream, params.getCharset()));
            charset = params.getCharset();
        }
        //if text or a non-csv/tsv category of text
        //treat this as text and be done
        //TODO -- if it was detected already as a non-csv subtype of text
        if (! params.getMediaType().getBaseType().equals(CSV) &&
            ! params.getMediaType().getBaseType().equals(TSV)) {
            handleText(reader, charset, handler, metadata);
            return;
        }

        updateMetadata(params, metadata);

        CSVFormat csvFormat = CSVFormat.EXCEL.withDelimiter(params.getDelimiter());
        metadata.set(DELIMITER_PROPERTY, CHAR_TO_STRING_DELIMITER_MAP.get(csvFormat.getDelimiter()));

        XHTMLContentHandler xhtmlContentHandler = new XHTMLContentHandler(handler, metadata);
        try (org.apache.commons.csv.CSVParser commonsParser =
                     new org.apache.commons.csv.CSVParser(reader, csvFormat)) {
            xhtmlContentHandler.startDocument();
            xhtmlContentHandler.startElement(TABLE);
            try {
                for (CSVRecord row : commonsParser) {
                    xhtmlContentHandler.startElement(TR);
                    for (String cell : row) {
                        xhtmlContentHandler.startElement(TD);
                        xhtmlContentHandler.characters(cell);
                        xhtmlContentHandler.endElement(TD);
                    }
                    xhtmlContentHandler.endElement(TR);
                }
            } catch (IllegalStateException e) {
                //if there's a parse exception
                //try to get the rest of the content...treat it as text for now
                //There will be some content lost because of buffering.
                //TODO -- figure out how to improve this
                xhtmlContentHandler.endElement(TABLE);
                xhtmlContentHandler.startElement("div", "name", "after exception");
                handleText(reader, xhtmlContentHandler);
                xhtmlContentHandler.endElement("div");
                xhtmlContentHandler.endDocument();
                //TODO -- consider dumping what's left in the reader as text
                throw new TikaException("exception parsing the csv", e);
            }

            xhtmlContentHandler.endElement(TABLE);
            xhtmlContentHandler.endDocument();
        }
    }

    private void handleText(Reader reader, Charset charset,
                            ContentHandler handler, Metadata metadata)
            throws SAXException, IOException, TikaException {
        // Automatically detect the character encoding
            //try to get detected content type; could be a subclass of text/plain
            //such as vcal, etc.
            String incomingMime = metadata.get(Metadata.CONTENT_TYPE);
            MediaType mediaType = MediaType.TEXT_PLAIN;
            if (incomingMime != null) {
                MediaType tmpMediaType = MediaType.parse(incomingMime);
                if (tmpMediaType != null) {
                    mediaType = tmpMediaType;
                }
            }
            MediaType type = new MediaType(mediaType, charset);
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            handleText(reader, xhtml);
            xhtml.endDocument();
    }

    private static void handleText(Reader reader, XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        xhtml.startElement("p");
        char[] buffer = new char[4096];
        int n = reader.read(buffer);
        while (n != -1) {
            xhtml.characters(buffer, 0, n);
            n = reader.read(buffer);
        }
        xhtml.endElement("p");

    }

    private void updateMetadata(CSVParams params, Metadata metadata) {
        MediaType mediaType = (params.getDelimiter() == '\t') ? TSV : CSV;
        Map<String, String> attrs = new HashMap<>();
        attrs.put(CHARSET, params.getCharset().name());
        if (params.getDelimiter() != null) {
            if (CHAR_TO_STRING_DELIMITER_MAP.containsKey(params.getDelimiter())) {
                attrs.put(DELIMITER, CHAR_TO_STRING_DELIMITER_MAP.get(params.getDelimiter()));
            } else {
                attrs.put(DELIMITER, Integer.toString((int)params.getDelimiter().charValue()));
            }
        }
        MediaType type = new MediaType(mediaType, attrs);
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
        // deprecated, see TIKA-431
        metadata.set(Metadata.CONTENT_ENCODING, params.getCharset().name());

    }

    private Reader detect(CSVParams params, InputStream stream,
                        Metadata metadata, ParseContext context) throws IOException, TikaException {
        //if the file was already identified as not .txt, .csv or .tsv
        //don't even try to csv or not
        String mediaString = metadata.get(Metadata.CONTENT_TYPE);
        if (mediaString != null) {
            MediaType mediaType = MediaType.parse(mediaString);
            if (! SUPPORTED_TYPES.contains(mediaType.getBaseType())) {
                params.setMediaType(mediaType);
                return new AutoDetectReader(
                        new CloseShieldInputStream(stream),
                        metadata, getEncodingDetector(context));
            }
        }
        Reader reader = null;
        if (params.getCharset() == null) {
            reader = new AutoDetectReader(
                    new CloseShieldInputStream(stream),
                    metadata, getEncodingDetector(context));
            params.setCharset(((AutoDetectReader)reader).getCharset());
            if (params.isComplete()) {
                return reader;
            }
        } else {
            reader = new BufferedReader(new InputStreamReader(
                    new CloseShieldInputStream(stream), params.getCharset()));
        }

        if (params.getDelimiter() == null &&
                (params.getMediaType() == null ||
                        isCSVOrTSV(params.getMediaType()))) {

            CSVSniffer sniffer = new CSVSniffer(delimiters);
            CSVResult result = sniffer.getBest(reader);
            //we should require a higher confidence if the content-type
            //is text/plain -- e.g. if the file name ends in .txt or
            //the parent parser has an indication that this is txt
            //(as in mail attachment headers)
            params.setMediaType(result.getMediaType());
            params.setDelimiter(result.getDelimiter());
        }
        return reader;
    }

    private CSVParams getOverride(Metadata metadata) {
        String override = metadata.get(TikaCoreProperties.CONTENT_TYPE_OVERRIDE);
        if (override == null) {
            return new CSVParams();
        }
        MediaType mediaType = MediaType.parse(override);
        if (mediaType == null) {
            return new CSVParams();
        }
        String charsetString = mediaType.getParameters().get(CHARSET);
        Charset charset = null;
        if (charsetString != null) {
            try {
                charset = Charset.forName(charsetString);
            } catch (UnsupportedCharsetException e) {

            }
        }
        if (! isCSVOrTSV(mediaType)) {
            return new CSVParams(mediaType, charset);
        }

        String delimiterString = mediaType.getParameters().get(DELIMITER);
        if (delimiterString == null) {
            return new CSVParams(mediaType, charset);
        }
        if (STRING_TO_CHAR_DELIMITER_MAP.containsKey(delimiterString)) {
            return new CSVParams(mediaType, charset, (char) STRING_TO_CHAR_DELIMITER_MAP.get(delimiterString));
        }
        if (delimiterString.length() == 1) {
            return new CSVParams(mediaType, charset, delimiterString.charAt(0));
        }
        //TODO: log bad/unrecognized delimiter string
        return new CSVParams(mediaType, charset);
    }

    static boolean isCSVOrTSV(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getBaseType().equals(TSV) ||
                mediaType.getBaseType().equals(CSV)) {
            return true;
        }
        return false;
    }
    private byte[] readFirstX(InputStream stream, int markLimit) throws IOException {
        byte[] bytes = new byte[markLimit];

        try {
            stream.mark(markLimit);
            int numRead = IOUtils.read(stream, bytes, 0, bytes.length);
            if (numRead < markLimit) {
                byte[] dest = new byte[numRead];
                System.arraycopy(bytes, 0, dest, 0, numRead);
                bytes = dest;
            }
        } finally {
            stream.reset();
        }
        return bytes;
    }

    private CSVFormat guessFormat(byte[] bytes, Charset charset, Metadata metadata) throws IOException {

        String mediaTypeString = metadata.get(Metadata.CONTENT_TYPE);
        char bestDelimiter = (mediaTypeString.endsWith("csv")) ? ',' : '\t';
        CSVReadTestResult bestResult = null;

        for (char c : DEFAULT_DELIMITERS) {

            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), charset)) {

                CSVReadTestResult testResult = attemptCSVRead(c, bytes.length, reader);
                if (bestResult == null || testResult.isBetterThan(bestResult)) {
                    bestResult = testResult;
                    bestDelimiter = c;
                }
            }
        }
        return CSVFormat.EXCEL.withDelimiter(bestDelimiter);
    }

    private CSVReadTestResult attemptCSVRead(char delimiter, int bytesTotal, Reader reader) throws IOException {

        //maps <rowLength, numberOfRows>
        Map<Integer, Integer> colCounts = new HashMap<>();
        long lastCharacterPosition = -1L;
        int rowCount = 0;
        boolean illegalStateException = false;
        try {
            org.apache.commons.csv.CSVParser p = new org.apache.commons.csv.CSVParser(reader, CSVFormat.EXCEL.withDelimiter(delimiter));

            for (CSVRecord row : p) {
                int colCount = row.size();
                lastCharacterPosition = row.getCharacterPosition();
                Integer cnt = colCounts.get(colCount);
                if (cnt == null) {
                    cnt = 1;
                } else {
                    cnt++;
                }
                colCounts.put(colCount, cnt);
                rowCount++;
            }
        } catch (IllegalStateException e) {
            //this could be bad encapsulation -- invalid char between encapsulated token
            //swallow while guessing
            illegalStateException = true;
        }

        int mostCommonColCount = -1;
        int totalCount = 0;
        for (Integer count : colCounts.values()) {
            if (count > mostCommonColCount) {
                mostCommonColCount = count;
            }
            totalCount += count;
        }
        double percentMostCommonRowLength = -1.0f;
        if (totalCount > 0) {
            percentMostCommonRowLength = (double) mostCommonColCount / (double) totalCount;
        }
        return new CSVReadTestResult(bytesTotal, lastCharacterPosition, rowCount, percentMostCommonRowLength, illegalStateException);

    }

    private static class CSVReadTestResult {
        private final int bytesTotal;
        private final long bytesParsed;
        private final int rowCount;
        //the percentage of the rows that have the
        //the most common row length -- maybe use stdev?
        private final double percentMostCommonRowLength;
        private final boolean illegalStateException;

        public CSVReadTestResult(int bytesTotal, long bytesParsed, int rowCount,
                                 double percentMostCommonRowLength, boolean illegalStateException) {
            this.bytesTotal = bytesTotal;
            this.bytesParsed = bytesParsed;
            this.rowCount = rowCount;
            this.percentMostCommonRowLength = percentMostCommonRowLength;
            this.illegalStateException = illegalStateException;
        }

        public boolean isBetterThan(CSVReadTestResult bestResult) {
            if (bestResult == null) {
                return true;
            }
            if (illegalStateException && ! bestResult.illegalStateException) {
                return false;
            } else if (! illegalStateException && bestResult.illegalStateException) {
                return true;
            }
            //if there are >= 3 rows in both, select the one with the better
            //percentMostCommonRowLength
            if (this.rowCount >= 3 && bestResult.rowCount >= 3) {
                if (percentMostCommonRowLength > bestResult.percentMostCommonRowLength) {
                    return true;
                } else {
                    return false;
                }
            }

            //if there's a big difference between the number of bytes parsed,
            //pick the one that allowed more parsed bytes
            if (bytesTotal > 0 && Math.abs((bestResult.bytesParsed - bytesParsed) / bytesTotal) > 0.1f) {
                if (bytesParsed > bestResult.bytesParsed) {
                    return true;
                } else {
                    return false;
                }
            }
            //add other heuristics as necessary

            //if there's no other information,
            //default to not better = default
            return false;
        }
    }


}
