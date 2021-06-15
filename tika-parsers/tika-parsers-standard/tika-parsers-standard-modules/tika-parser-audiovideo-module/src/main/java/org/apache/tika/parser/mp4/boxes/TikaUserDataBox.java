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
package org.apache.tika.parser.mp4.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drew.lang.SequentialByteArrayReader;
import com.drew.lang.SequentialReader;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.boxes.Box;
import org.xml.sax.SAXException;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaUserDataBox extends Box {

    private static final String LOCATION_CODE = "\u00A9xyz";
    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)");

    @Nullable
    private String coordinateString;

    private boolean isQuickTime = false;
    private final Metadata metadata;
    private final XHTMLContentHandler xhtml;
    public TikaUserDataBox(@NotNull Box box, byte[] payload, Metadata metadata,
                           XHTMLContentHandler xhtml) throws IOException, SAXException {
        super(box);
        this.metadata = metadata;
        this.xhtml = xhtml;
        int length = payload.length;
        SequentialReader reader = new SequentialByteArrayReader(payload);
        while (reader.getPosition() < (long) length) {
            long size = reader.getUInt32();
            if (size <= 4L) {
                break;
            }
            String kindName = reader.getString(4, StandardCharsets.ISO_8859_1);
            if (LOCATION_CODE.equals(kindName)) {
                int xyzLength = reader.getUInt16();
                reader.skip(2L);
                this.coordinateString = reader.getString(xyzLength, "UTF-8");
            } else if ("meta".equals(kindName)) {
                reader.getUInt32();
                reader.getUInt32();
                String hdlr = reader.getString(4, StandardCharsets.ISO_8859_1);
                reader.getUInt32();
                reader.getUInt32();
                String subtype = reader.getString(4, StandardCharsets.ISO_8859_1);
                // If the second and the fifth 32-bit integers encode 'hdlr' and 'mdta' respectively
                // then the MetaBox is formatted according to QuickTime File Format.
                // See https://developer.apple.com/library/content/documentation
                // /QuickTime/QTFF/Metadata/Metadata.html
                if (hdlr.equals("hdlr") && subtype.equals("mdta")) {
                    isQuickTime = true;
                }
                parseUserDataBox(reader, subtype);
            } else {
                if (size < 8L) {
                    return;
                }

                reader.skip(size - 8L);
            }
        }

    }

    private void parseUserDataBox(SequentialReader reader, String handlerType)
            throws IOException, SAXException {
        if (! "mdir".equals(handlerType)) {
            return;
        }
        String mdirType = reader.getString(4, StandardCharsets.ISO_8859_1);

        if ("appl".equals(mdirType)) {
            reader.getString(10);//not sure what these bytes are
            long len = reader.getUInt32();
            if (len >= Integer.MAX_VALUE || len <= 0) {
                //log
                return;
            }
            String subType = reader.getString(4, StandardCharsets.ISO_8859_1);
            if ("ilst".equals(subType)) {
                processIList(reader, len);
            }
        }
    }

    private void processIList(SequentialReader reader, long totalLen)
            throws IOException {

        long totalRead = 0;
        while (totalRead < totalLen) {
            long recordLen = reader.getUInt32();
            String fieldName = reader.getString(4, StandardCharsets.ISO_8859_1);
            long fieldLen = reader.getUInt32();
            String typeName = reader.getString(4, StandardCharsets.ISO_8859_1);//data
            totalRead += 16;
            if ("data".equals(typeName)) {
                reader.skip(8);//not sure what these are
                totalRead += 8;
                int toRead = (int) fieldLen - 16;
                if (toRead <= 0) {
                    //log?
                    return;
                }
                if ("covr".equals(fieldName)) {
                    //covr can be an image file, e.g. png or jpeg
                    //skip this for now
                    reader.skip(toRead);
                } else if ("cpil".equals(fieldName)) {
                    int compilationId = (int)reader.getByte();
                    metadata.set(XMPDM.COMPILATION, compilationId);
                } else if ("trkn".equals(fieldName)) {
                    if (toRead == 8) {
                        long numA = reader.getUInt32();
                        long numB = reader.getUInt32();
                        metadata.set(XMPDM.TRACK_NUMBER, (int)numA);
                    } else {
                        //log
                        reader.skip(toRead);
                    }
                } else if ("disk".equals(fieldName)) {
                    int a = reader.getInt32();
                    short b = reader.getInt16();
                    metadata.set(XMPDM.DISC_NUMBER, a);
                } else {
                    String val = reader.getString(toRead, StandardCharsets.UTF_8);
                    try {
                        addMetadata(fieldName, val);
                    } catch (SAXException e) {
                        //need to punch through IOException catching in MP4Reader
                        throw new RuntimeSAXException(e);
                    }
                }

                totalRead += toRead;
            } else {
                int toSkip = (int) recordLen - 16;
                if (toSkip <= 0) {
                    //log?
                    return;
                }
                reader.skip(toSkip);
                totalRead += toSkip;
            }
        }
    }


    private void addMetadata(String key, String value) throws SAXException {
        switch (key) {
            case "\u00A9nam":
                metadata.set(TikaCoreProperties.TITLE, value);
                xhtml.element("p", value);
                break;
            case "\u00A9too":
                metadata.set(XMP.CREATOR_TOOL, value);
                break;
            case "\u00A9ART" :
                metadata.set(XMPDM.ARTIST, value);
                metadata.set(TikaCoreProperties.CREATOR, value);
                xhtml.element("p", value);
                break;
            case "aART" :
                metadata.set(XMPDM.ALBUM_ARTIST, value);
                xhtml.element("p", value);
                break;
            case "\u00A9wrt":
                metadata.set(XMPDM.COMPOSER, value);
                xhtml.element("p", value);
                break;
            case "\u00A9alb":
                metadata.set(XMPDM.ALBUM, value);
                xhtml.element("p", value);
                break;
            case "\u00A9gen" :
                metadata.set(XMPDM.GENRE, value);
                xhtml.element("p", value);
                break;
            case "\u00A9day" :
                //this can be a year "2008" or a date "2017-04-26T07:00:00Z"
                metadata.set(XMPDM.RELEASE_DATE, value);
                xhtml.element("p", value);
                break;
            case "\u00A9cmt" :
                metadata.set(XMPDM.LOG_COMMENT, value);
                xhtml.element("p", value);
                break;
            case "cprt" :
                metadata.set(XMPDM.COPYRIGHT, value);
                xhtml.element("p", value);
                break;
            case "xid " :
                //not sure this is the right use of this key
                metadata.set(XMP.IDENTIFIER, value);
                break;
                //purd date?
                //xid ? e.g. SonyBMG:isrc:KRA031208874
                //cprt copyright
                //ownr ? and apID
                //flvr ?
                //son = nam, soal = (c)alb soar = aART?
                //(C)ART
        }
    }

    public void addMetadata(Mp4Directory directory) {
        if (this.coordinateString != null) {
            Matcher matcher = COORDINATE_PATTERN.matcher(this.coordinateString);
            if (matcher.find()) {
                double latitude = Double.parseDouble(matcher.group(1));
                double longitude = Double.parseDouble(matcher.group(2));
                directory.setDouble(8193, latitude);
                directory.setDouble(8194, longitude);
            }
        }
    }
}


