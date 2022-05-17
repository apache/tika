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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;





/**
 * DWGReadParser (CAD Drawing) parser. This extends the original DWGParser if in the parser configuration DwgRead is
 * set. DWG reader can be found here: https://github.com/LibreDWG/libredwg DWGRead outputs json which we then loop
 * through extracting the text elements The required configuration is dwgReadExecutable. The other settings which can be
 * overwritten are: boolean : cleanDwgReadOutput - whether to clean the json output int : cleanDwgReadOutputBatchSize -
 * clean output batch size to process long : dwgReadTimeout -timeout in milliseconds before killing the dwgread process
 * String : cleanDwgReadRegexToReplace - characters to replace in the json String : cleanDwgReadReplaceWith -
 * replacement characters dwgReadExecutable
 */

public class DWGReadParser extends AbstractDWGParser {
    private static final Logger LOG = LoggerFactory.getLogger(DWGReadParser.class);
    /**
     * 
     */
    private static final long serialVersionUID = 7983127145030096837L;
    private static MediaType TYPE = MediaType.image("vnd.dwg");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(TYPE);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        configure(context);
        DWGParserConfig dwgc = context.get(DWGParserConfig.class);
        final XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        // create unique files so we avoid overwriting out files if multithreaded
        UUID uuid = UUID.randomUUID();
        File tmpFileOut = File.createTempFile(uuid + "dwgreadout", ".json");
        File tmpFileOutCleaned = File.createTempFile(uuid + "dwgreadoutclean", ".json");
        File tmpFileIn = File.createTempFile(uuid + "dwgreadin", ".dwg");
        try {
            

            FileUtils.copyInputStreamToFile(stream, tmpFileIn);

            List<String> command = Arrays.asList(dwgc.getDwgReadExecutable(), "-O", "JSON", "-o",
                    tmpFileOut.getCanonicalPath(), tmpFileIn.getCanonicalPath());
            ProcessBuilder pb = new ProcessBuilder().command(command);
            LOG.info("About to call DWGRead: " + command.toString());
            FileProcessResult fpr = ProcessUtils.execute(pb, dwgc.getDwgReadTimeout(), 10000, 10000);
            LOG.info("DWGRead Exit code is: " + fpr.getExitValue());
            if (fpr.getExitValue() == 0) {
                if (dwgc.isCleanDwgReadOutput()) {
                    // dwgread sometimes creates strings with invalid utf-8 sequences or invalid json
                    //  replace them with empty string.
                    LOG.debug("Cleaning Json Output - Replace: " + dwgc.getCleanDwgReadRegexToReplace() + " with: "
                            + dwgc.getCleanDwgReadReplaceWith());
                    try (FileInputStream fis = new FileInputStream(tmpFileOut);
                            FileOutputStream fos = new FileOutputStream(tmpFileOutCleaned)) {
                        byte[] bytes = new byte[dwgc.getCleanDwgReadOutputBatchSize()];
                        while (fis.read(bytes) != -1) {
                            byte[] fixedBytes = new String(bytes, StandardCharsets.UTF_8)
                      // We need to replace nan with 0 otherwise the json is incorrect
                                    .replaceAll("nan,| nan","0")
                                    .replaceAll(dwgc.getCleanDwgReadRegexToReplace(), dwgc.getCleanDwgReadReplaceWith())
                                    .getBytes(StandardCharsets.UTF_8);
                            fos.write(fixedBytes, 0, fixedBytes.length);
                        }
                    } finally {
                        FileUtils.deleteQuietly(tmpFileOut);
                        tmpFileOut = tmpFileOutCleaned;
                    }

                } else {
                    LOG.debug(
                            "Json wasn't cleaned, "
                            + "if json parsing fails consider reviewing dwgread json output to check it's valid");
                }
            } else {
                throw new TikaException(
                        "DWGRead Failed - Exit Code is:" + fpr.getExitValue() + " Exe error is: " + fpr.getStderr());
            }

            // we can't guarantee the json output is correct so we try to ignore as many
            // errors as we can
            JsonFactory jfactory = JsonFactory.builder()
                    .enable(JsonReadFeature.ALLOW_MISSING_VALUES, JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
                            JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                    .build();
            JsonParser jParser;
            try {
                jParser = jfactory.createParser(tmpFileOut);
            } catch (JsonParseException e1) {
                throw new TikaException("Failed to parse Json: " + ExceptionUtils.getStackTrace(e1));
            } catch (IOException e1) {
                throw new TikaException("Failed to read json file: " + ExceptionUtils.getStackTrace(e1));
            }
            // read json token in a stream using jackson, iterate over each token. We only
            // support OBJECTS, FILEHEADER and SummaryInfo
            // these are the only ones we have in either sample files or have been tested
            // with
            JsonToken nextToken = jParser.nextToken();
            while ((nextToken = jParser.nextToken()) != JsonToken.END_OBJECT) {
                if (nextToken == JsonToken.FIELD_NAME) {
                    String nextFieldName = jParser.currentName();
                    nextToken = jParser.nextToken();
                    if (nextToken.isStructStart()) {

                        if ("OBJECTS".equals(nextFieldName)) {
                            // Start array
                            jParser.nextToken();
                            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                                parseDwgObject(jParser, (nextTextValue) -> {

                                    try {
                                        xhtml.characters(cleanupDwgString(nextTextValue));
                                        xhtml.newline();
                                    } catch (SAXException e) {
                                        LOG.error("Could not write next text value {} to xhtml stream", nextTextValue);
                                    }
                                });
                            }
                        } else if ("FILEHEADER".equals(nextFieldName)) {
                            parseHeader(jParser, metadata);
                        } else if ("SummaryInfo".equals(nextFieldName)) {
                            parseSummaryInfo(jParser, metadata);
                        } else {
                            jParser.skipChildren();
                        }
                    }
                }
            }
            jParser.close();
        } finally {
            // make sure we delete all temp files
            FileUtils.deleteQuietly(tmpFileOut);
            FileUtils.deleteQuietly(tmpFileIn);
            FileUtils.deleteQuietly(tmpFileOutCleaned);
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

                            textConsumer.accept(textVal);
                        }
                    } else if ("text_value".equals(nextFieldName)) {
                        String textVal = jsonParser.getText();
                        if (StringUtils.isNotBlank(textVal)) {

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
                        // timestamps are represented by an integer array of format with 2 values in the
                        // array:
                        // [julianDate, millisecondOfDay]
                        jsonParser.nextToken(); // start array
                        int julianDay = jsonParser.getIntValue();
                        jsonParser.nextToken();
                        int millisecondsIntoDay = jsonParser.getValueAsInt();
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

    private String cleanupDwgString(String dwgString) {
        // Cleaning the formatting of the text has been found from the following
        // website's:
        // https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640
        // https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html
        // We always to do a backwards look to make sure the string to replace hasn't
        // been escaped
        String cleanString;
        // replace A0-2 (Alignment)
        cleanString = dwgString.replaceAll("(?<!\\\\)\\\\A[0-2];", "");
        // replace \\p (New paragraph/ new line) and with new line
        cleanString = cleanString.replaceAll("(?<!\\\\\\\\)\\\\P", "\\n");
        // remove pi (numbered paragraphs)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pi.*;", "");
        // remove pxi (bullets)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxi.*;", "");
        // remove pxt (tab stops)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxt.*;", "");
        // remove lines with \H (text height)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\H[0-9]*.*;", "");
        // remove lines with \F Font Selection
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\F.*;", "");
        // Replace \L \l (underlines)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\L)(.*)(\\\\l)", "$2");
        // Replace \O \o (over strikes)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\O)(.*)(\\\\o)", "$2");
        // Replace \K \k (Strike through)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\K)(.*)(\\\\k)", "$2");
        // Replace \N (new Column)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\N)", "\t");
        // Replace \Q (text angle)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\Q[\\d];", "");
        // Replace \W (Text Width)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\W[.*];", "");
        // Replace \S (Stacking)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\S[.*]:", "");
        // Replace \C (Stacking)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\C[1-7];)", "");
        // Replace \T (Tracking)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\T[0-9];)", "");
        // Now we have cleaned the formatting we can now remove the escaped \
        cleanString = cleanString.replaceAll("(\\\\)", "\\\\");
        // Replace {} (text formatted by the above)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\}|(?<!\\\\)\\{", "");

        //
        return cleanString;

    }

}
