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
import org.apache.tika.Tika;
import org.apache.tika.config.Field;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class CSVParser extends AbstractParser {
    private static final String CSV_PREFIX = "csv";
    public static final Property DELIMITER = Property.externalText(
            CSV_PREFIX+ TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER+"delimiter");

    private static final String TD = "td";
    private static final String TR = "tr";
    private static final String TABLE = "table";

    private static final MediaType CSV = MediaType.text("csv");
    private static final MediaType TSV = MediaType.text("tsv");

    private static final int DEFAULT_MARK_LIMIT = 20000;

    //TODO: add | or make this configurable?
    private static final char[] CANDIDATE_DELIMITERS = new char[]{',', '\t'};

    private static final Map<Character, String> DELIMITERS = new HashMap<>();

    static {
        DELIMITERS.put(',', "comma");
        DELIMITERS.put('\t', "tab");
    }



    @Field
    private int markLimit = DEFAULT_MARK_LIMIT;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    CSV, TSV)));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        String override = metadata.get(TikaCoreProperties.CONTENT_TYPE_OVERRIDE);
        Character overrideDelimiter = null;
        Charset overrideCharset = null;
        if (override != null) {
            MediaType mediaType = MediaType.parse(override);
            String charset = mediaType.getParameters().get("charset");
            overrideDelimiter = mediaType.getBaseType().toString().endsWith("tsv") ? '\t' : ',';
            if (charset != null) {
                try {
                    overrideCharset = Charset.forName(charset);
                } catch (UnsupportedCharsetException e) {
                    //swallow
                }
            }
        }
        if (overrideDelimiter == null || overrideCharset == null) {
            if (!stream.markSupported()) {
                stream = new BufferedInputStream(stream);
            }
        }
        //buffer the firstx bytes to detect delimiter
        byte[] firstX = null;
        if (overrideDelimiter == null) {
            firstX = readFirstX(stream, markLimit);
        }
        Charset charset = null;
        Reader reader = null;
        org.apache.commons.csv.CSVParser commonsParser = null;
        try {
            //need to detect if nothing has been sent in via override
            if (overrideCharset == null) {
                reader = new AutoDetectReader(stream);
                charset = ((AutoDetectReader) reader).getCharset();
            } else {
                reader = new BufferedReader(new InputStreamReader(stream, overrideCharset));
                charset = overrideCharset;
            }
            CSVFormat csvFormat = null;
            if (overrideDelimiter == null) {
                csvFormat = guessFormat(firstX, charset, metadata);
            } else {
                csvFormat = CSVFormat.EXCEL.withDelimiter(overrideDelimiter);
            }
            metadata.set(DELIMITER, DELIMITERS.get(csvFormat.getDelimiter()));

            if (overrideCharset == null || overrideDelimiter == null) {
                MediaType mediaType = (csvFormat.getDelimiter() == '\t') ? TSV : CSV;
                MediaType type = new MediaType(mediaType, charset);
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
                // deprecated, see TIKA-431
                metadata.set(Metadata.CONTENT_ENCODING, charset.name());
            }

            XHTMLContentHandler xhtmlContentHandler = new XHTMLContentHandler(handler, metadata);
            commonsParser = new org.apache.commons.csv.CSVParser(reader, csvFormat);
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
                throw new TikaException("exception parsing the csv", e);
            }

            xhtmlContentHandler.endElement(TABLE);
            xhtmlContentHandler.endDocument();
        } finally {
            if (commonsParser != null) {
                try {
                    commonsParser.close();
                } catch (IOException e) {
                    //swallow
                }
            }
            IOUtils.closeQuietly(reader);
        }
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

        for (char c : CANDIDATE_DELIMITERS) {

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
