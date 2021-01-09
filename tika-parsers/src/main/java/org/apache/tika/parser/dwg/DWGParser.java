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
package org.apache.tika.parser.dwg;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.StringUtil;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * DWG (CAD Drawing) parser. This is a very basic parser, which just
 * looks for bits of the headers.
 * Note that we use Apache POI for various parts of the processing, as
 * lots of the low level string/int/short concepts are the same.
 */
public class DWGParser extends AbstractParser {

    private static final Logger LOG = LoggerFactory.getLogger(DWGParser.class);

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -7744232583079169119L;

    private static MediaType TYPE = MediaType.image("vnd.dwg");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(TYPE);
    }

    /**
     * The order of the fields in the header
     */
    private static final Property[] HEADER_PROPERTIES_ENTRIES = {
            TikaCoreProperties.TITLE,
            TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_DESCRIPTION,
            TikaCoreProperties.CREATOR,
            TikaCoreProperties.TRANSITION_KEYWORDS_TO_DC_SUBJECT,
            TikaCoreProperties.COMMENTS,
            TikaCoreProperties.MODIFIER,
            null, // Unknown?
            TikaCoreProperties.RELATION, // Hyperlink
    };

    /**
     * For the 2000 file, they're indexed
     */
    private static final Property[] HEADER_2000_PROPERTIES_ENTRIES = {
            null,
            TikaCoreProperties.RELATION, // 0x01
            TikaCoreProperties.TITLE,    // 0x02
            TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_DESCRIPTION,  // 0x03
            TikaCoreProperties.CREATOR,   // 0x04
            null,
            TikaCoreProperties.COMMENTS,// 0x06
            TikaCoreProperties.TRANSITION_KEYWORDS_TO_DC_SUBJECT,    // 0x07
            TikaCoreProperties.MODIFIER, // 0x08
    };

    private static final String HEADER_2000_PROPERTIES_MARKER_STR =
            "DWGPROPS COOKIE";

    private static final byte[] HEADER_2000_PROPERTIES_MARKER =
            new byte[HEADER_2000_PROPERTIES_MARKER_STR.length()];

    static {
        StringUtil.putCompressedUnicode(
                HEADER_2000_PROPERTIES_MARKER_STR,
                HEADER_2000_PROPERTIES_MARKER, 0);
    }

    private DWGConfig dwgConfig = new DWGConfig();

    public DWGParser() {

    }

    /**
     * How far to skip after the last standard property, before
     * we find any custom properties that might be there.
     */
    private static final int CUSTOM_PROPERTIES_SKIP = 20;

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {

        DWGConfig config = context.get(DWGConfig.class, dwgConfig);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        if (StringUtils.isNotBlank(config.getDwgReadExecutable())) {
            File tmpFileOut = File.createTempFile("dwgreadout", ".json");
            File tmpFileOutCleaned = File.createTempFile("dwgreadoutclean", ".json");
            File tmpFileIn = File.createTempFile("dwgreadin", ".dwg");
            try {

                FileUtils.copyInputStreamToFile(stream, tmpFileIn);

                List<String> command = Arrays.asList(config.getDwgReadExecutable(), "-O", "JSON", "-o", tmpFileOut.getCanonicalPath(), tmpFileIn.getCanonicalPath());
                LOG.info("Running dwgread command: {}", command);

                Stopwatch dwgreadSw = Stopwatch.createStarted();
                Process p = new ProcessBuilder(command).start();

                try {
                    int exitCode = p.waitFor();
                    LOG.info("Dwgread command returned exit code: {}, outputFileCreated? {}, took = {}", exitCode, tmpFileOut.exists(), dwgreadSw.elapsed(TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (dwgConfig.isCleanDwgReadOutput()) {
                    // dwgread sometimes creates strings with invalid utf-8 sequences. replace them with empty string.
                    Stopwatch cleanupSw = Stopwatch.createStarted();
                    try (FileInputStream fis = new FileInputStream(tmpFileOut);
                         FileOutputStream fos = new FileOutputStream(tmpFileOutCleaned)) {
                        byte[] bytes = new byte[config.getCleanDwgReadOutputBatchSize()];
                        while (fis.read(bytes) != -1) {
                            byte[] fixedBytes = new String(bytes, StandardCharsets.UTF_8).replaceAll(config.getCleanDwgReadRegexToReplace(), config.getCleanDwgReadReplaceWith()).getBytes(StandardCharsets.UTF_8);
                            fos.write(fixedBytes, 0, fixedBytes.length);
                        }
                    } finally {
                        FileUtils.deleteQuietly(tmpFileOut);
                        tmpFileOut = tmpFileOutCleaned;
                    }
                    LOG.debug("Cleaning non UTF-8 garbage took {} ms", cleanupSw.elapsed(TimeUnit.MILLISECONDS));
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonFactory jsonFactory = mapper.getFactory();

                JsonParser jsonParser = jsonFactory.createParser(tmpFileOut);

                JsonToken nextToken = jsonParser.nextToken();
                if (nextToken != JsonToken.START_OBJECT) {
                    throw new IllegalStateException("The first element of the Json structure was expected to be a START_OBJECT token, but it was: " + nextToken);
                }
                while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
                    if (nextToken == JsonToken.FIELD_NAME) {
                        String nextFieldName = jsonParser.currentName();
                        nextToken = jsonParser.nextToken();
                        if (nextToken.isStructStart()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Struct node: {}", nextFieldName);
                            }
                            if ("OBJECTS".equals(nextFieldName)) {
                                // Start array
                                jsonParser.nextToken();
                                while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                                    parseDwgObject(jsonParser, (nextTextValue) -> {
                                        try {
                                            xhtml.characters(String.format("%s ", nextTextValue));
                                        } catch (SAXException e) {
                                            LOG.error("Could not write next text value {} to xhtml stream", nextTextValue);
                                        }
                                    });
                                }
                            } else if ("SummaryInfo".equals(nextFieldName)) {
                                parseSummaryInfo(jsonParser, metadata);
                            } else if ("FILEHEADER".equals(nextFieldName)) {
                                parseHeader(jsonParser, metadata);
                            } else {
                                jsonParser.skipChildren();
                            }
                        }
                    }
                }
            } finally {
                FileUtils.deleteQuietly(tmpFileOut);
                FileUtils.deleteQuietly(tmpFileIn);
            }
        } else {
            // First up, which version of the format are we handling?
            byte[] header = new byte[128];
            IOUtils.readFully(stream, header);
            String version = new String(header, 0, 6, StandardCharsets.US_ASCII);

            System.out.println("Version: " + version);

            if (version.equals("AC1015")) {
                metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
                if (skipTo2000PropertyInfoSection(stream, header)) {
                    get2000Props(stream, metadata, xhtml);
                }
            } else if (version.equals("AC1018")) {
                metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
                if (skipToPropertyInfoSection(stream, header)) {
                    get2004Props(stream, metadata, xhtml);
                }
            } else if (version.equals("AC1021") || version.equals("AC1024") || version.equals("AC1027")) {
                metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
                if (skipToPropertyInfoSection(stream, header)) {
                    get2007and2010Props(stream, metadata, xhtml);
                }
            } else {
                throw new TikaException(
                        "Unsupported AutoCAD drawing version: " + version);
            }
        }
        xhtml.endDocument();
    }

    private void parseDwgObject(JsonParser jsonParser, Consumer<String> textConsumer) throws IOException {
        JsonToken nextToken;
        while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
            if (nextToken == JsonToken.FIELD_NAME) {
                String nextFieldName = jsonParser.currentName();
                nextToken = jsonParser.nextToken();
                if (nextToken.isStructStart()) {
                    jsonParser.skipChildren();
                } else if (nextToken.isScalarValue()) {
                    if ("text".equals(nextFieldName)) {
                        String textVal = jsonParser.getText();
                        if (StringUtils.isNotBlank(textVal)) {
                            if (LOG.isDebugEnabled()) {
                                LOG.info("Text: {}", textVal);
                            }
                            textConsumer.accept(textVal);
                        }
                    }
                }
            }
        }
    }

    private void parseHeader(JsonParser jsonParser, Metadata metadata) throws IOException {
        JsonToken nextToken;
        while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
            if (nextToken == JsonToken.FIELD_NAME) {
                String nextFieldName = jsonParser.currentName();
                nextToken = jsonParser.nextToken();
                if (nextToken.isStructStart()) {
                    jsonParser.skipChildren();
                } else if (nextToken.isScalarValue()) {
                    metadata.set(nextFieldName, jsonParser.getText());
                }
            }
        }
    }

    private void parseSummaryInfo(JsonParser jsonParser, Metadata metadata) throws IOException {
        JsonToken nextToken;
        while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
            if (nextToken == JsonToken.FIELD_NAME) {
                String nextFieldName = jsonParser.currentName();
                nextToken = jsonParser.nextToken();
                if (nextToken.isStructStart()) {
                    if ("TDCREATE".equals(nextFieldName) || "TDUPDATE".equals(nextFieldName)) {
                        // timestamps are represented by an integer array of format with 2 values in the array:
                        // [julianDate, millisecondOfDay]
                        jsonParser.nextToken(); // start array
                        int julianDay = jsonParser.getIntValue();
                        jsonParser.nextToken();
                        int millisecondsIntoDay = jsonParser.getIntValue();
                        Instant instant = JulianDateUtil.toInstant(julianDay, millisecondsIntoDay);
                        jsonParser.nextToken(); // end array
                        if ("TDCREATE".equals(nextFieldName)) {
                            metadata.set(TikaCoreProperties.CREATED, instant.toString());
                        } else {
                            metadata.set(TikaCoreProperties.MODIFIED, instant.toString());
                        }
                    } else {
                        jsonParser.skipChildren();
                    }

                } else if (nextToken.isScalarValue()) {
                    String textVal = jsonParser.getText();
                    if (StringUtils.isNotBlank(textVal)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Summary Info - {} = {}", nextFieldName, textVal);
                        }
                        if ("TITLE".equals(nextFieldName)) {
                            metadata.set(TikaCoreProperties.TITLE, textVal);
                        } else if ("LASTSAVEDBY".equals(nextFieldName)) {
                            metadata.set(TikaCoreProperties.MODIFIER, textVal);
                        } else if (!StringUtils.startsWithIgnoreCase(nextFieldName, "unknown")) {
                            metadata.set(nextFieldName, textVal);
                        }
                    }
                }
            }
        }
    }

    /**
     * Stored as US-ASCII
     */
    private void get2004Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
        // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2004String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }

        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
            String propName = read2004String(stream);
            String propValue = read2004String(stream);
            if (propName.length() > 0 && propValue.length() > 0) {
                metadata.add(propName, propValue);
            }
        }
    }

    private String read2004String(InputStream stream) throws IOException, TikaException {
        int stringLen = EndianUtils.readUShortLE(stream);

        byte[] stringData = new byte[stringLen];
        IOUtils.readFully(stream, stringData);

        // Often but not always null terminated
        if (stringData[stringLen - 1] == 0) {
            stringLen--;
        }
        String value = StringUtil.getFromCompressedUnicode(stringData, 0, stringLen);
        return value;
    }

    /**
     * Stored as UCS2, so 16 bit "unicode"
     */
    private void get2007and2010Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
        // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2007and2010String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }

        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
            String propName = read2007and2010String(stream);
            String propValue = read2007and2010String(stream);
            if (propName.length() > 0 && propValue.length() > 0) {
                metadata.add(propName, propValue);
            }
        }
    }

    private String read2007and2010String(InputStream stream) throws IOException, TikaException {
        int stringLen = EndianUtils.readUShortLE(stream);

        byte[] stringData = new byte[stringLen * 2];
        IOUtils.readFully(stream, stringData);
        String value = StringUtil.getFromUnicodeLE(stringData);

        // Some strings are null terminated
        if (value.charAt(value.length() - 1) == 0) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    private void get2000Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
        int propCount = 0;
        while (propCount < 30) {
            int propIdx = EndianUtils.readUShortLE(stream);
            int length = EndianUtils.readUShortLE(stream);
            int valueType = stream.read();

            if (propIdx == 0x28) {
                // This one seems not to follow the pattern
                length = 0x19;
            } else if (propIdx == 90) {
                // We think this means the end of properties
                break;
            }

            byte[] value = new byte[length];
            IOUtils.readFully(stream, value);
            if (valueType == 0x1e) {
                // Normal string, good
                String val = StringUtil.getFromCompressedUnicode(value, 0, length);

                // Is it one we can look up by index?
                if (propIdx < HEADER_2000_PROPERTIES_ENTRIES.length) {
                    metadata.add(HEADER_2000_PROPERTIES_ENTRIES[propIdx], val);
                    xhtml.element("p", val);
                } else if (propIdx == 0x012c) {
                    int splitAt = val.indexOf('=');
                    if (splitAt > -1) {
                        String propName = val.substring(0, splitAt);
                        String propVal = val.substring(splitAt + 1);
                        metadata.add(propName, propVal);
                    }
                }
            } else {
                // No idea...
            }

            propCount++;
        }
    }

    private void handleHeader(
            int headerNumber, String value, Metadata metadata,
            XHTMLContentHandler xhtml) throws SAXException {
        if (value == null || value.length() == 0) {
            return;
        }

        Property headerProp = HEADER_PROPERTIES_ENTRIES[headerNumber];
        if (headerProp != null) {
            metadata.set(headerProp, value);
        }

        xhtml.element("p", value);
    }

    /**
     * Grab the offset, then skip there
     */
    private boolean skipToPropertyInfoSection(InputStream stream, byte[] header)
            throws IOException, TikaException {
        // The offset is stored in the header from 0x20 onwards
        long offsetToSection = EndianUtils.getLongLE(header, 0x20);

        // Sanity check the offset. Some files seem to use a different format,
        //  and the offset isn't available at 0x20. Until we can work out how
        //  to find the offset in those files, skip them if detected
        if (offsetToSection > 0xa00000l) {
            // Header should never be more than 10mb into the file, something is wrong
            offsetToSection = 0;
        }

        // Work out how far to skip, and sanity check
        long toSkip = offsetToSection - header.length;
        if (offsetToSection == 0) {
            return false;
        }
        while (toSkip > 0) {
            byte[] skip = new byte[Math.min((int) toSkip, 0x4000)];
            IOUtils.readFully(stream, skip);
            toSkip -= skip.length;
        }
        return true;
    }

    /**
     * We think it can be anywhere...
     */
    private boolean skipTo2000PropertyInfoSection(InputStream stream, byte[] header)
            throws IOException {
        int val = 0;
        while (val != -1) {
            val = stream.read();
            if (val == HEADER_2000_PROPERTIES_MARKER[0]) {
                boolean going = true;
                for (int i = 1; i < HEADER_2000_PROPERTIES_MARKER.length && going; i++) {
                    val = stream.read();
                    if (val != HEADER_2000_PROPERTIES_MARKER[i]) going = false;
                }
                if (going) {
                    // Bingo, found it
                    return true;
                }
            }
        }
        return false;
    }

    private int skipToCustomProperties(InputStream stream)
            throws IOException, TikaException {
        // There should be 4 zero bytes or CUSTOM_PROPERTIES_ALT_PADDING_VALUES next
        byte[] padding = new byte[4];
        IOUtils.readFully(stream, padding);
        if (padding[0] <= 5 && padding[1] == 0 &&
                padding[2] == 0 && padding[3] == 0) {
            // Looks hopeful, skip on
            padding = new byte[CUSTOM_PROPERTIES_SKIP];
            IOUtils.readFully(stream, padding);

            // We should now have the count
            int count = EndianUtils.readUShortLE(stream);

            // Sanity check it
            if (count > 0 && count < 0x7f) {
                // Looks plausible
                return count;
            } else {
                // No properties / count is too high to trust
                return 0;
            }
        } else {
            // No padding. That probably means no custom props
            return 0;
        }
    }

    public DWGConfig getDwgConfig() {
        return dwgConfig;
    }
}
