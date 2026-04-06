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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;

/**
 * Handles embedded objects and pictures within the JFlex-based RTF token stream.
 *
 * <p>Uses streaming parsers ({@link RTFObjDataStreamParser} and
 * {@link RTFPictStreamParser}) so that large embedded objects are written
 * to temp files rather than buffered entirely in memory.</p>
 */
public class RTFEmbeddedHandler {

    private final ContentHandler handler;
    private final ParseContext context;
    private final EmbeddedDocumentUtil embeddedDocumentUtil;
    private final long maxBytes;

    private boolean inObject;
    private boolean isPictBitmap;
    private int hi = -1;
    private int thumbCount;
    private final AtomicInteger unknownFilenameCount = new AtomicInteger();

    private String sn = "";
    private String sv = "";
    private final StringBuilder metadataBuffer = new StringBuilder();

    private Metadata metadata;

    // Streaming parsers -- one active at a time
    private RTFObjDataStreamParser objParser;
    private RTFPictStreamParser pictParser;

    public RTFEmbeddedHandler(ContentHandler handler, ParseContext context,
                              int maxBytesInKb) {
        this.handler = handler;
        this.context = context;
        this.embeddedDocumentUtil = new EmbeddedDocumentUtil(context);
        this.maxBytes = maxBytesInKb > 0 ? (long) maxBytesInKb * 1024 : -1;
        this.metadata = Metadata.newInstance(context);
    }

    /**
     * Process a token for embedded object/pict handling.
     * Call this AFTER {@link RTFState#processToken(RTFToken)} has run.
     */
    public void processToken(RTFToken tok, RTFState rtfState, RTFGroupState closingGroup)
            throws IOException, SAXException, TikaException {
        RTFGroupState group = rtfState.getCurrentGroup();

        switch (tok.getType()) {
            case GROUP_CLOSE:
                if (closingGroup.objdata) {
                    handleCompletedObjData();
                } else if (closingGroup.pictDepth == 1) {
                    handleCompletedPict();
                } else if (closingGroup.sn) {
                    sn = metadataBuffer.toString();
                } else if (closingGroup.sv) {
                    sv = metadataBuffer.toString();
                } else if (closingGroup.sp) {
                    metadata.add(sn, sv);
                }
                if (closingGroup.object) {
                    inObject = false;
                }
                break;

            case CONTROL_WORD:
                switch (tok.getName()) {
                    case "object":
                        inObject = true;
                        break;
                    case "objdata":
                        metadata = Metadata.newInstance(context);
                        objParser = new RTFObjDataStreamParser(maxBytes);
                        break;
                    case "pict":
                        metadata = Metadata.newInstance(context);
                        pictParser = new RTFPictStreamParser(maxBytes);
                        break;
                    case "sn":
                        metadataBuffer.setLength(0);
                        metadataBuffer.append(RTFMetadata.RTF_PICT_META_PREFIX);
                        break;
                    case "sv":
                        metadataBuffer.setLength(0);
                        break;
                    case "wbitmap":
                        isPictBitmap = true;
                        break;
                }
                break;

            case TEXT:
                if (group.objdata || group.pictDepth == 1) {
                    writeHexChar(tok.getChar());
                } else if (group.sn || group.sv) {
                    metadataBuffer.append(tok.getChar());
                }
                break;

            case HEX_ESCAPE:
                if (group.sn || group.sv) {
                    metadataBuffer.append((char) tok.getHexValue());
                }
                break;

            default:
                break;
        }
    }

    private void handleCompletedObjData() throws IOException, SAXException, TikaException {
        try (TikaInputStream tis = objParser.onComplete(metadata, unknownFilenameCount)) {
            if (tis != null) {
                extractObj(tis, metadata);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        } finally {
            objParser.close();
            objParser = null;
            reset();
        }
    }

    private void handleCompletedPict() throws IOException, SAXException, TikaException {
        try {
            String filePath =
                    metadata.get(RTFMetadata.RTF_PICT_META_PREFIX + "wzDescription");
            if (filePath != null && !filePath.isEmpty()) {
                metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, filePath);
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                        FilenameUtils.getName(filePath));
                metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, filePath);
            }
            metadata.set(RTFMetadata.THUMBNAIL, Boolean.toString(inObject));
            if (isPictBitmap) {
                metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                        "image/x-rtf-raw-bitmap");
            }
            try (TikaInputStream tis = pictParser.onComplete(metadata)) {
                if (tis != null) {
                    extractObj(tis, metadata);
                }
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        } finally {
            pictParser = null;
            reset();
        }
    }

    private void writeHexChar(int b) throws IOException, TikaException {
        if (isHexChar(b)) {
            if (hi == -1) {
                hi = 16 * hexValue(b);
            } else {
                int decoded = hi + hexValue(b);
                hi = -1;
                if (objParser != null) {
                    objParser.onByte(decoded);
                } else if (pictParser != null) {
                    pictParser.onByte(decoded);
                }
            }
        }
    }

    private void extractObj(TikaInputStream tis, Metadata meta)
            throws SAXException, IOException, TikaException {
        meta.set(Metadata.CONTENT_LENGTH, Long.toString(tis.getLength()));

        if (embeddedDocumentUtil.shouldParseEmbedded(meta)) {
            if (meta.get(TikaCoreProperties.RESOURCE_NAME_KEY) == null) {
                String extension = embeddedDocumentUtil.getExtension(tis, meta);
                if (inObject && pictParser != null) {
                    meta.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                            EmbeddedDocumentUtil.EmbeddedResourcePrefix.THUMBNAIL.getPrefix()
                                    + "-" + thumbCount++ + extension);
                    meta.set(RTFMetadata.THUMBNAIL, "true");
                } else {
                    meta.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                            EmbeddedDocumentUtil.EmbeddedResourcePrefix.EMBEDDED.getPrefix()
                                    + "-" + unknownFilenameCount.getAndIncrement()
                                    + extension);
                }
                meta.set(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED, true);
            }
            try {
                embeddedDocumentUtil.parseEmbedded(
                        tis, new EmbeddedContentHandler(handler), meta, true);
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, meta);
            }
        }
    }

    private void reset() {
        metadata = Metadata.newInstance(context);
        hi = -1;
        sn = "";
        sv = "";
        metadataBuffer.setLength(0);
        isPictBitmap = false;
    }

    private static boolean isHexChar(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private static int hexValue(int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'z') {
            return 10 + (ch - 'a');
        } else {
            return 10 + (ch - 'A');
        }
    }
}
