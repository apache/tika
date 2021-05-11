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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class EnviHeaderParser extends AbstractEncodingDetectorParser {

    public static final String ENVI_MIME_TYPE = "application/envi.hdr";
    private static final long serialVersionUID = -1479368523072408091L;
    private static final Logger LOG = LoggerFactory.getLogger(EnviHeaderParser.class);
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("envi.hdr"));

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
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        // Only outputting the MIME type as metadata
        metadata.set(Metadata.CONTENT_TYPE, ENVI_MIME_TYPE);

        // The following code was taken from the TXTParser
        // Automatically detect the character encoding

        TikaConfig tikaConfig = context.get(TikaConfig.class);
        if (tikaConfig == null) {
            tikaConfig = TikaConfig.getDefaultConfig();
        }
        try (AutoDetectReader reader = new AutoDetectReader(new CloseShieldInputStream(stream),
                metadata, getEncodingDetector(context))) {
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

    private void readLines(AutoDetectReader reader, Metadata metadata)
            throws IOException, SAXException {
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
        if (line.length() < 300) {
            String[] keyValue = line.split("=", 2);
            if (keyValue.length != 1) {
                if (keyValue[0].trim().equals("map info")) {
                    String[] mapInfoValues = parseMapInfoContents(keyValue[1]);
                    if (mapInfoValues[0].equals("UTM")) {
                        metadata.set("envi." + keyValue[0].trim().replace(" ", "."),
                                keyValue[1].trim());
                        String[] latLonStringArray =
                                convertMapInfoValuesToLatLngAndSetMetadata(mapInfoValues, metadata);
                        String xhtmlLatLongLine = "lat/lon = { " + latLonStringArray[0] + ", " +
                                latLonStringArray[1] + " }";
                        xhtml.startElement("p");
                        xhtml.characters(xhtmlLatLongLine);
                        xhtml.endElement("p");
                    } else {
                        metadata.set("envi." + keyValue[0].trim().replace(" ", "."),
                                keyValue[1].trim());
                    }
                } else {
                    metadata.set("envi." + keyValue[0].trim().replace(" ", "."),
                            keyValue[1].trim());
                }
            }
        }
        xhtml.startElement("p");
        xhtml.characters(line);
        xhtml.endElement("p");
    }

    private String[] parseMapInfoContents(String mapInfoValue) {
        StringBuilder mapInfoValueStringBuilder = new StringBuilder();
        for (int i = 0; i < mapInfoValue.length(); ++i) {
            if (mapInfoValue.charAt(i) != '{' && mapInfoValue.charAt(i) != '}' &&
                    mapInfoValue.charAt(i) != ' ') {
                mapInfoValueStringBuilder.append(mapInfoValue.charAt(i));
            }
        }
        String[] mapInfoValues = mapInfoValueStringBuilder.toString().split(",");
        return mapInfoValues;
    }

    // Conversion logic taken from https://stackoverflow.com/questions/343865/how-to-convert-from-utm-to-latlng-in-python-or-javascript/344083#344083
    private String[] convertMapInfoValuesToLatLngAndSetMetadata(String[] mapInfoValues,
                                                                Metadata metadata) {
        // Based on the map info data, pixelEasting is at index 3 and pixelNorthing is at index 4
        double pixelEasting = Double.parseDouble(mapInfoValues[3].trim());
        double pixelNorthing = Double.parseDouble(mapInfoValues[4].trim());
        int zone = 0;
        if (!mapInfoValues[7].trim().isEmpty()) {
            zone = Integer.parseInt(mapInfoValues[7].trim());
        }

        double a = 6378137.0;
        double e = 0.0818191910;
        double e1sq = 0.006739497;
        double k0 = 0.9996;

        double arc = pixelNorthing / k0;
        double mu = arc / (a * (1.0 - Math.pow(e, 2.0) / 4.0 - 3.0 * Math.pow(e, 4.0) / 64.0 -
                5.0 * Math.pow(e, 6.0) / 256.0));

        double ei = (1.0 - Math.pow((1.0 - e * e), (1.0 / 2.0))) /
                (1.0 + Math.pow((1.0 - e * e), (1.0 / 2.0)));

        double ca = 3.0 * ei / 2.0 - 27.0 * Math.pow(ei, 3.0) / 32.0;

        double cb = 21.0 * Math.pow(ei, 2.0) / 16.0 - 55.0 * Math.pow(ei, 4.0) / 32.0;
        double cc = 151.0 * Math.pow(ei, 3.0) / 96.0;
        double cd = 1097.0 * Math.pow(ei, 4.0) / 512.0;
        double phi1 =
                mu + ca * Math.sin(2.0 * mu) + cb * Math.sin(4.0 * mu) + cc * Math.sin(6.0 * mu) +
                        cd * Math.sin(8.0 * mu);

        double n0 = a / Math.pow((1.0 - Math.pow((e * Math.sin(phi1)), 2.0)), (1.0 / 2.0));

        double r0 = a * (1.0 - e * e) /
                Math.pow((1.0 - Math.pow((e * Math.sin(phi1)), 2.0)), (3.0 / 2.0));
        double fact1 = n0 * Math.tan(phi1) / r0;

        double _a1 = 500000.0 - pixelEasting;
        double dd0 = _a1 / (n0 * k0);
        double fact2 = dd0 * dd0 / 2.0;
        double t0 = Math.pow(Math.tan(phi1), 2.0);
        double Q0 = e1sq * Math.pow(Math.cos(phi1), 2.0);
        double fact3 =
                (5.0 + 3.0 * t0 + 10.0 * Q0 - 4.0 * Q0 * Q0 - 9.0 * e1sq) * Math.pow(dd0, 4.0) /
                        24.0;
        double fact4 =
                (61.0 + 90.0 * t0 + 298.0 * Q0 + 45.0 * t0 * t0 - 252.0 * e1sq - 3.0 * Q0 * Q0) *
                        Math.pow(dd0, 6.0) / 720.0;
        double lof1 = _a1 / (n0 * k0);
        double lof2 = (1.0 + 2.0 * t0 + Q0) * Math.pow(dd0, 3.0) / 6.0;
        double lof3 = (5.0 - 2.0 * Q0 + 28.0 * t0 - 3.0 * Math.pow(Q0, 2.0) + 8.0 * e1sq +
                24.0 * Math.pow(t0, 2.0)) * Math.pow(dd0, 5.0) / 120.0;
        double _a2 = (lof1 - lof2 + lof3) / Math.cos(phi1);
        double _a3 = _a2 * 180.0 / Math.PI;
        double zoneCM = (zone > 0) ? 6 * zone - 183.0 : 3.0;
        double latitude = 180.0 * (phi1 - fact1 * (fact2 + fact3 + fact4)) / Math.PI;
        double longitude = zoneCM - _a3;
        metadata.set("envi.lat/lon", latitude + ", " + longitude);

        return new String[]{Double.toString(latitude), Double.toString(longitude)};
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
