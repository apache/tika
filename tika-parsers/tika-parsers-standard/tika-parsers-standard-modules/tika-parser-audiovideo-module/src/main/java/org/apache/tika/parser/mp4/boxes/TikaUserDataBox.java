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

import com.drew.lang.SequentialByteArrayReader;
import com.drew.lang.SequentialReader;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.mp4.Mp4Directory;
import org.xml.sax.SAXException;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.audio.CoverArt;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaUserDataBox {

    private static final String LOCATION_CODE = "\u00A9xyz";
    private static final String META = "meta";
    private static final String ILST = "ilst";
    private static final String MDTA = "mdta";
    private static final String HDLR = "hdlr";
    private static final String MDIR = "mdir";//apple metadata itunes reader

    @Nullable
    private String coordinateString;

    private boolean isQuickTime = false;
    //covr carries no picture type, so the first cover is the thumbnail
    private int coverCount = 0;
    private final Metadata metadata;
    private final XHTMLContentHandler xhtml;
    private final ParseContext parseContext;
    public TikaUserDataBox(@NotNull String box, byte[] payload, Metadata metadata,
                           XHTMLContentHandler xhtml, ParseContext parseContext)
            throws IOException, SAXException {
        this.metadata = metadata;
        this.xhtml = xhtml;
        this.parseContext = parseContext;
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
            } else if (META.equals(kindName)) {
                reader.getUInt32();//not sure what this is
                long lengthToStartOfList = reader.getUInt32() - 4;//this is the length to
                // 'ilst', but the length of the ilist is defined in the 4 bytes before ilist
                if (lengthToStartOfList < 0 || lengthToStartOfList > Integer.MAX_VALUE) {
                    return;
                }
                String hdlr = reader.getString(4, StandardCharsets.ISO_8859_1);
                reader.getUInt32();
                reader.getUInt32();
                String subtype = reader.getString(4, StandardCharsets.ISO_8859_1);
                // If the second and the fifth 32-bit integers encode 'hdlr' and 'mdta' respectively
                // then the MetaBox is formatted according to QuickTime File Format.
                // See https://developer.apple.com/library/content/documentation
                // /QuickTime/QTFF/Metadata/Metadata.html
                if (HDLR.equals(hdlr) && MDTA.equals(subtype)) {
                    isQuickTime = true;
                }
                int read = 16;//bytes read so far
                parseUserDataBox(reader, subtype, read, (int)lengthToStartOfList);
            } else {
                if (size < 8L) {
                    return;
                }

                reader.skip(size - 8L);
            }
        }

    }

    private void parseUserDataBox(SequentialReader reader, String handlerType,
                                  int read, int lengthToStartOfList)
            throws IOException {
        if (!MDIR.equals(handlerType)) {
            return;
        }
        if (lengthToStartOfList < read) {
            return;
        }
        int toSkip = lengthToStartOfList - read;
        reader.skip(toSkip);
        long len = reader.getUInt32();
        String subType = reader.getString(4, StandardCharsets.ISO_8859_1);
        //walk the "free"-style sub-boxes to the ilst, validating each declared length
        //once before it is used: a length below the 8-byte header would make skip(len - 8)
        //negative (an IllegalArgumentException that escapes MP4Reader), and an oversize one
        //would desync the walk. Raise a caught IOException instead, so the udta walk aborts
        //and the problem is recorded as a parse error rather than silently mis-read. It
        //also throws (EOFException) if no ilst is found. See TIKA-4812.
        while (! ILST.equals(subType)) {
            if (len < 8L || len >= Integer.MAX_VALUE) {
                throw new IOException("Malformed box length in udta metadata: " + len);
            }
            reader.skip(len - 8);
            len = reader.getUInt32();
            subType = reader.getString(4, StandardCharsets.ISO_8859_1);
        }
        if (len < 8L || len >= Integer.MAX_VALUE) {
            throw new IOException("Malformed ilst length in udta metadata: " + len);
        }
        processIList(reader, len);
    }



    private void processIList(SequentialReader reader, long totalLen)
            throws IOException {

        long totalRead = 0;
        while (totalRead < totalLen) {
            long recordStart = reader.getPosition();
            long recordLen = reader.getUInt32();
            if (recordLen < 16) {
                //malformed record header; stop rather than loop or skip a negative span
                return;
            }
            String fieldName = reader.getString(4, StandardCharsets.ISO_8859_1);
            long fieldLen = reader.getUInt32();
            String typeName = reader.getString(4, StandardCharsets.ISO_8859_1);//data
            long recordEnd = recordStart + recordLen;
            if ("data".equals(typeName)) {
                //1 byte version and 3 bytes flags; for the "well-known" types the
                //flags hold the value type
                long valueType = reader.getUInt32() & 0xFFFFFF;
                reader.skip(4L);//locale
                int toRead = (int) fieldLen - 16;
                if (toRead > 0) {
                    if ("covr".equals(fieldName)) {
                        //covr holds one image per data atom and may repeat the data atom
                        //for further images; the realign below consumes any leftover
                        if (reader.getPosition() + toRead <= recordEnd) {
                            handleCoverArt(reader, valueType, toRead);
                        }
                        while (reader.getPosition() + 16 <= recordEnd) {
                            long extraLen = reader.getUInt32();
                            String extraType =
                                    reader.getString(4, StandardCharsets.ISO_8859_1);
                            long extraValueType = reader.getUInt32() & 0xFFFFFF;
                            reader.skip(4L);//locale
                            int extraToRead = (int) extraLen - 16;
                            if (!"data".equals(extraType) || extraToRead <= 0
                                    || reader.getPosition() + extraToRead > recordEnd) {
                                break;
                            }
                            handleCoverArt(reader, extraValueType, extraToRead);
                        }
                    } else if ("cpil".equals(fieldName)) {
                        metadata.set(XMPDM.COMPILATION, (int) reader.getByte());
                    } else if ("trkn".equals(fieldName)) {
                        if (toRead >= 8) {
                            long numA = reader.getUInt32();
                            long numB = reader.getUInt32();
                            metadata.set(XMPDM.TRACK_NUMBER, (int) numA);
                            //2 bytes track total, 2 bytes reserved
                            int trackCount = (int) (numB >>> 16);
                            if (trackCount > 0) {
                                metadata.set(Audio.TRACK_COUNT, trackCount);
                            }
                        }
                    } else if ("disk".equals(fieldName)) {
                        if (toRead >= 6) {
                            //2 bytes reserved, 2 bytes disc, 2 bytes total
                            int a = reader.getInt32();
                            short b = reader.getInt16();
                            metadata.set(XMPDM.DISC_NUMBER, a);
                            if (b > 0) {
                                metadata.set(Audio.DISC_COUNT, b);
                            }
                        }
                    } else if (reader.getPosition() + toRead <= recordEnd) {
                        String val = reader.getString(toRead, StandardCharsets.UTF_8);
                        try {
                            addMetadata(fieldName, val);
                        } catch (SAXException e) {
                            //need to punch through IOException catching in MP4Reader
                            throw new RuntimeSAXException(e);
                        }
                    }
                }
            }
            //realign to the end of the record regardless of what the branch consumed, so a
            //trailing sub-atom (e.g. a 'name' atom after 'data') can't desync the walk
            long pos = reader.getPosition();
            if (pos > recordEnd) {
                //a branch read past the record end (malformed lengths); stop
                return;
            }
            reader.skip(recordEnd - pos);
            totalRead += recordLen;
        }
    }


    /**
     * Sends one embedded cover image to the embedded document extractor:
     * the first as the file's thumbnail, any further one as an inline
     * picture. The image only becomes an embedded document, no metadata is
     * recorded on the audio document itself.
     */
    private void handleCoverArt(SequentialReader reader, long valueType, int length)
            throws IOException {
        byte[] picture = reader.getBytes(length);
        Metadata pictureMetadata = Metadata.newInstance(parseContext);
        pictureMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                CoverArt.resourceType(coverCount++, 0).toString());
        //the data atom's well-known value type declares the image format;
        //for any other type leave the content type for auto-detection
        if (valueType == 13) {
            pictureMetadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        } else if (valueType == 14) {
            pictureMetadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        } else if (valueType == 27) {
            pictureMetadata.set(HttpHeaders.CONTENT_TYPE, "image/bmp");
        }
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        if (extractor.shouldParseEmbedded(pictureMetadata, parseContext)) {
            try (TikaInputStream tis = TikaInputStream.get(picture)) {
                extractor.parseEmbedded(tis, xhtml, pictureMetadata, parseContext, true);
            } catch (SAXException e) {
                //need to punch through IOException catching in MP4Reader
                throw new RuntimeSAXException(e);
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
            case "keyw" :
                metadata.set(TikaCoreProperties.SUBJECT, value);
                xhtml.element("p", value);
                break;
            case "\u00A9lyr" :
                xhtml.element("p", value);
                break;
            case "ldes" : //intentional fall through
            case "desc" :
                metadata.set(TikaCoreProperties.DESCRIPTION, value);
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
            ISO6709.Location location = ISO6709.parse(this.coordinateString);
            if (location != null) {
                directory.setDouble(8193, location.latitude);
                directory.setDouble(8194, location.longitude);
                //Mp4Directory has no altitude tag, so set geo:alt directly
                if (location.altitude != null) {
                    metadata.set(TikaCoreProperties.ALTITUDE, location.altitude);
                }
            }
        }
    }
}
