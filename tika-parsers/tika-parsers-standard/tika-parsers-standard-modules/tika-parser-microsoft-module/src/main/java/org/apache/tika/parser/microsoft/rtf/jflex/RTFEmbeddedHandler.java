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
import java.nio.file.Path;
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

    private static final String EMPTY_STRING = "";

    private final ContentHandler handler;
    private final ParseContext context;
    private final EmbeddedDocumentUtil embeddedDocumentUtil;
    private final long maxBytes;

    private boolean inObject = false;
    private boolean isPictBitmap = false;
    private int hi = -1;
    private int thumbCount = 0;
    private final AtomicInteger unknownFilenameCount = new AtomicInteger();

    // Shape property metadata
    private String sn = EMPTY_STRING;
    private String sv = EMPTY_STRING;
    private final StringBuilder metadataBuffer = new StringBuilder();

    private Metadata metadata;
    private EmbState state = EmbState.NADA;

    // Streaming parsers — one active at a time
    private RTFObjDataStreamParser objParser;
    private RTFPictStreamParser pictParser;

    public RTFEmbeddedHandler(ContentHandler handler, ParseContext context,
                              int memoryLimitInKb) {
        this.handler = handler;
        this.context = context;
        this.embeddedDocumentUtil = new EmbeddedDocumentUtil(context);
        this.maxBytes = memoryLimitInKb > 0 ? (long) memoryLimitInKb * 1024 : -1;
        this.metadata = Metadata.newInstance(context);
    }

    /**
     * Process a token for embedded object/pict handling.
     * Call this AFTER {@link RTFState#processToken(RTFToken)} has run.
     *
     * @param tok the current token
     * @param rtfState the RTF state (already updated for this token)
     * @param closingGroup for GROUP_CLOSE tokens, the group state that just closed.
     *                     Null for other token types.
     */
    public void processToken(RTFToken tok, RTFState rtfState, RTFGroupState closingGroup)
            throws IOException, SAXException, TikaException {
        RTFTokenType type = tok.getType();
        RTFGroupState group = rtfState.getCurrentGroup();

        switch (type) {
            case GROUP_CLOSE:
                if (closingGroup == null) {
                    break;
                }
                if (closingGroup.objdata) {
                    handleCompletedObjData();
                } else if (closingGroup.pictDepth == 1) {
                    handleCompletedPict();
                } else if (closingGroup.sn) {
                    endSN();
                } else if (closingGroup.sv) {
                    endSV();
                } else if (closingGroup.sp) {
                    endSP();
                }
                if (closingGroup.object) {
                    inObject = false;
                }
                break;

            case CONTROL_WORD:
                String name = tok.getName();
                switch (name) {
                    case "object":
                        inObject = true;
                        break;
                    case "objdata":
                        startObjData();
                        break;
                    case "pict":
                        startPict();
                        break;
                    case "sn":
                        startSN();
                        break;
                    case "sv":
                        startSV();
                        break;
                    case "wbitmap":
                        isPictBitmap = true;
                        break;
                }
                break;

            case TEXT:
                if (group.objdata || group.pictDepth == 1) {
                    String text = tok.getName();
                    for (int i = 0; i < text.length(); i++) {
                        writeHexChar(text.charAt(i));
                    }
                } else if (group.sn || group.sv) {
                    String text = tok.getName();
                    for (int i = 0; i < text.length(); i++) {
                        metadataBuffer.append(text.charAt(i));
                    }
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

    // --- Lifecycle for objdata ---

    private void startObjData() throws IOException {
        state = EmbState.OBJDATA;
        metadata = Metadata.newInstance(context);
        objParser = new RTFObjDataStreamParser(maxBytes);
    }

    private void handleCompletedObjData() throws IOException, SAXException, TikaException {
        if (objParser == null) {
            reset();
            return;
        }
        try {
            TikaInputStream tis = objParser.onComplete(metadata, unknownFilenameCount);
            if (tis != null) {
                try {
                    extractObj(tis, metadata);
                } finally {
                    tis.close();
                }
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        } finally {
            objParser.close();
            objParser = null;
            reset();
        }
    }

    // --- Lifecycle for pict ---

    private void startPict() throws IOException {
        state = EmbState.PICT;
        metadata = Metadata.newInstance(context);
        pictParser = new RTFPictStreamParser(maxBytes);
    }

    private void handleCompletedPict() throws IOException, SAXException, TikaException {
        if (pictParser == null) {
            reset();
            return;
        }
        try {
            Path pictFile = pictParser.onComplete();
            if (pictFile != null) {
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

                try (TikaInputStream tis = TikaInputStream.get(pictFile)) {
                    extractObj(tis, metadata);
                }
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        } finally {
            pictParser.close();
            pictParser = null;
            reset();
        }
    }

    // --- Shape property metadata ---

    private void startSN() {
        metadataBuffer.setLength(0);
        metadataBuffer.append(RTFMetadata.RTF_PICT_META_PREFIX);
    }

    private void endSN() {
        sn = metadataBuffer.toString();
    }

    private void startSV() {
        metadataBuffer.setLength(0);
    }

    private void endSV() {
        sv = metadataBuffer.toString();
    }

    private void endSP() {
        metadata.add(sn, sv);
    }

    // --- Hex pair decoding ---

    private void writeHexChar(int b) throws IOException, TikaException {
        if (isHexChar(b)) {
            if (hi == -1) {
                hi = 16 * hexValue(b);
            } else {
                int decoded = hi + hexValue(b);
                hi = -1;
                // Route the decoded byte to the active streaming parser
                if (objParser != null) {
                    objParser.onByte(decoded);
                } else if (pictParser != null) {
                    pictParser.onByte(decoded);
                }
            }
        }
    }

    // --- Common extraction ---

    private void extractObj(TikaInputStream tis, Metadata meta)
            throws SAXException, IOException, TikaException {
        meta.set(Metadata.CONTENT_LENGTH, Long.toString(tis.getLength()));

        if (embeddedDocumentUtil.shouldParseEmbedded(meta)) {
            if (meta.get(TikaCoreProperties.RESOURCE_NAME_KEY) == null) {
                String extension = embeddedDocumentUtil.getExtension(tis, meta);
                if (inObject && state == EmbState.PICT) {
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
        state = EmbState.NADA;
        metadata = Metadata.newInstance(context);
        hi = -1;
        sn = EMPTY_STRING;
        sv = EMPTY_STRING;
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

    private enum EmbState {
        PICT,
        OBJDATA,
        NADA
    }
}
