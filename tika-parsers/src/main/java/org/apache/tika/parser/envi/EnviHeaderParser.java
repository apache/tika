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
 * 
 */
package org.apache.tika.parser.envi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EnviHeaderParser extends AbstractEncodingDetectorParser {

    private static final long serialVersionUID = -1479368523072408091L;

    private static final Logger LOG = LoggerFactory.getLogger(EnviHeaderParser.class);

    public static final String ENVI_MIME_TYPE = "application/envi.hdr";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("envi.hdr"));

    private List<String> multiLineFieldValueList = new ArrayList<>();

    private transient XHTMLContentHandler xhtml;

    public EnviHeaderParser() {
        super();
    }
    public EnviHeaderParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        // Only outputting the MIME type as metadata
        metadata.set(Metadata.CONTENT_TYPE, ENVI_MIME_TYPE);

        // The following code was taken from the TXTParser
        // Automatically detect the character encoding

        TikaConfig tikaConfig = context.get(TikaConfig.class);
        if (tikaConfig == null) {
            tikaConfig = TikaConfig.getDefaultConfig();
        }
        try (AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata, getEncodingDetector(context))){
                Charset charset = reader.getCharset();
                // deprecated, see TIKA-431
                metadata.set(Metadata.CONTENT_ENCODING, charset.name());
                xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                readLines(reader, metadata);
                xhtml.endDocument();
        } catch (IOException | TikaException e) {
          LOG.error("Error reading input data stream.", e);
        }

    }

    private void readLines(AutoDetectReader reader, Metadata metadata) throws IOException, SAXException {
        // text contents of the xhtml
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("{") && !line.endsWith("}") || line.startsWith(" ")) {
                String completeField = parseMultiLineFieldValue(line);
                if (completeField != null) {
                    writeParagraphAndSetMetadata(completeField, metadata);
                }
            } else {
                writeParagraphAndSetMetadata(line, metadata);
          }
        }
    }

    /*
     * Write a line to the XHTMLContentHandler and populate the key, value into the Metadata
     */
    private void writeParagraphAndSetMetadata(String line, Metadata metadata) throws SAXException {
        if(line.length() < 150) {
            String[] keyValue = line.split("=");
            if(keyValue.length != 1) {
                metadata.set(keyValue[0].trim().replace(" ", "-"), keyValue[1].trim());
            }
        }
        xhtml.startElement("p");
        xhtml.characters(line);
        xhtml.endElement("p");
    }

    /*
     * Enables correct extraction of fiel values which span more
     * than one line. Essentially, multi-line fiel values are
     * typically enclosed within curly braces, so a primitive
     * check it made to ensure the multi-line contents are contained in
     * opening and closing braces.
     */
    private String parseMultiLineFieldValue(String line) {
        multiLineFieldValueList.add(line);
        if (line.endsWith("}")) {
          return String.join("", multiLineFieldValueList);
        } else {
          //do nothing
        }
        return null;
      
    }
}
