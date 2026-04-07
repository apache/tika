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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;

/**
 * Parses OLE objdata from an RTF stream inline, byte by byte.
 *
 * <p>The OLE objdata structure is:
 * <pre>
 *   [4 bytes version][4 bytes formatId]
 *   [4 bytes classNameLen][classNameLen bytes className]
 *   [4 bytes topicNameLen][topicNameLen bytes topicName]
 *   [4 bytes itemNameLen][itemNameLen bytes itemName]
 *   [4 bytes dataSz][dataSz bytes payload]
 * </pre>
 * The small header fields are parsed byte-by-byte via a state machine.
 * Once the header is complete and {@code dataSz} is known, the payload
 * bytes stream directly to a temp file -- never buffered in memory.</p>
 *
 * <p>On {@link #onComplete(Metadata, AtomicInteger)}, the payload is
 * interpreted based on {@code className} (Package, PBrush, POIFS, etc.)
 * and the extracted content is returned as a {@link TikaInputStream} whose
 * close will clean up all temp files via {@link TemporaryResources}.</p>
 */
public class RTFObjDataStreamParser implements Closeable {

    private static final String WIN_ASCII = "WINDOWS-1252";

    private final long maxBytes;
    private final TemporaryResources tmp = new TemporaryResources();

    // State machine
    private Field currentField = Field.VERSION;
    private byte[] fieldBuf = new byte[4];
    private int fieldPos;
    private int fieldTarget = 4;

    // Parsed header values
    private long version;
    private long formatId;
    private String className;
    private String topicName;
    private String itemName;
    private long dataSz;

    // String accumulator for length-prefixed ANSI strings
    private byte[] stringBuf;
    private int stringPos;

    // Payload streaming
    private Path tempFile;
    private OutputStream dataOut;
    private long dataWritten;

    /**
     * @param maxBytes maximum payload bytes to accept (-1 for unlimited)
     */
    public RTFObjDataStreamParser(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * Receive a single decoded byte from the objdata hex stream.
     */
    public void onByte(int b) throws IOException, TikaException {
        switch (currentField) {
            case VERSION:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    version = readLE32(fieldBuf);
                    initUint32Field(Field.FORMAT_ID);
                }
                break;

            case FORMAT_ID:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    formatId = readLE32(fieldBuf);
                    if (formatId != 2L) {
                        // Not an embedded object (1 = link). Skip everything.
                        currentField = Field.SKIP;
                    } else {
                        initUint32Field(Field.CLASS_LEN);
                    }
                }
                break;

            case CLASS_LEN:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    int len = (int) readLE32(fieldBuf);
                    initStringField(Field.CLASS_NAME, len);
                }
                break;

            case CLASS_NAME:
                stringBuf[stringPos++] = (byte) b;
                if (stringPos >= fieldTarget) {
                    className = decodeString(stringBuf, fieldTarget);
                    initUint32Field(Field.TOPIC_LEN);
                }
                break;

            case TOPIC_LEN:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    int len = (int) readLE32(fieldBuf);
                    initStringField(Field.TOPIC_NAME, len);
                }
                break;

            case TOPIC_NAME:
                stringBuf[stringPos++] = (byte) b;
                if (stringPos >= fieldTarget) {
                    topicName = decodeString(stringBuf, fieldTarget);
                    initUint32Field(Field.ITEM_LEN);
                }
                break;

            case ITEM_LEN:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    int len = (int) readLE32(fieldBuf);
                    initStringField(Field.ITEM_NAME, len);
                }
                break;

            case ITEM_NAME:
                stringBuf[stringPos++] = (byte) b;
                if (stringPos >= fieldTarget) {
                    itemName = decodeString(stringBuf, fieldTarget);
                    initUint32Field(Field.DATA_SIZE);
                }
                break;

            case DATA_SIZE:
                fieldBuf[fieldPos++] = (byte) b;
                if (fieldPos >= fieldTarget) {
                    dataSz = readLE32(fieldBuf);
                    if (dataSz <= 0) {
                        currentField = Field.DONE;
                    } else {
                        currentField = Field.DATA;
                        tempFile = tmp.createTempFile(".bin");
                        dataOut = new BufferedOutputStream(Files.newOutputStream(tempFile));
                    }
                }
                break;

            case DATA:
                if (maxBytes > 0 && dataWritten >= maxBytes) {
                    throw new TikaMemoryLimitException(dataWritten + 1, maxBytes);
                }
                dataOut.write(b);
                dataWritten++;
                if (dataWritten >= dataSz) {
                    dataOut.close();
                    dataOut = null;
                    currentField = Field.DONE;
                }
                break;

            case DONE:
            case SKIP:
                break;
        }
    }

    /**
     * Called when the objdata group closes. Populates metadata and returns
     * a TikaInputStream with the extracted embedded content, or null if
     * the object couldn't be parsed.
     *
     * <p>The caller owns the returned TikaInputStream -- closing it will
     * clean up all temp files via TemporaryResources.</p>
     */
    public TikaInputStream onComplete(Metadata metadata, AtomicInteger unknownFilenameCount)
            throws IOException, TikaException {
        if (currentField == Field.SKIP || tempFile == null) {
            return null;
        }

        metadata.add(RTFMetadata.EMB_APP_VERSION, Long.toString(version));
        if (className != null && !className.isEmpty()) {
            metadata.add(RTFMetadata.EMB_CLASS, className);
        }
        if (topicName != null && !topicName.isEmpty()) {
            metadata.add(RTFMetadata.EMB_TOPIC, topicName);
        }
        if (itemName != null && !itemName.isEmpty()) {
            metadata.add(RTFMetadata.EMB_ITEM, itemName);
        }

        String cn = className != null ? className.toLowerCase(Locale.ROOT) : "";

        if ("package".equals(cn)) {
            return handlePackage(metadata);
        } else if ("pbrush".equals(cn)) {
            return TikaInputStream.get(tempFile, metadata, tmp);
        } else {
            return handleGenericOrPOIFS(metadata, unknownFilenameCount);
        }
    }

    @Override
    public void close() throws IOException {
        if (dataOut != null) {
            dataOut.close();
            dataOut = null;
        }
        tmp.close();
    }

    // --- Package handling ---

    private TikaInputStream handlePackage(Metadata metadata) throws IOException, TikaException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(tempFile))) {
            readUShortLE(is); // type

            String displayName = readNullTerminatedString(is);
            readNullTerminatedString(is); // iconFilePath
            readUShortBE(is); // iconIndex
            int type2 = readUShortLE(is);

            if (type2 != 3) {
                return null;
            }

            readUIntLE(is); // filePathLen
            String ansiFilePath = readNullTerminatedString(is);
            long bytesLen = readUIntLE(is);

            // Write the embedded file content to a new temp file
            Path contentFile = tmp.createTempFile(".bin");
            try (OutputStream contentOut = new BufferedOutputStream(
                    Files.newOutputStream(contentFile))) {
                copyBounded(is, contentOut, bytesLen);
            }

            // Try to read unicode file path (optional)
            StringBuilder unicodePath = new StringBuilder();
            try {
                long unicodeLen = readUIntLE(is);
                for (int i = 0; i < unicodeLen; i++) {
                    int lo = is.read();
                    int hi = is.read();
                    if (lo == -1 || hi == -1) {
                        unicodePath.setLength(0);
                        break;
                    }
                    unicodePath.append((char) (lo + 256 * hi));
                }
            } catch (IOException e) {
                unicodePath.setLength(0);
            }

            String fileNameToUse;
            String pathToUse;
            if (unicodePath.length() > 0) {
                fileNameToUse = unicodePath.toString();
                pathToUse = unicodePath.toString();
            } else {
                fileNameToUse = displayName != null ? displayName : "";
                pathToUse = ansiFilePath != null ? ansiFilePath : "";
            }
            metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, fileNameToUse);
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                    FilenameUtils.getName(fileNameToUse));
            metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, pathToUse);

            // Return TikaInputStream backed by contentFile; closing it cleans up
            // both contentFile and the original tempFile via TemporaryResources
            return TikaInputStream.get(contentFile, metadata, tmp);
        }
    }

    // --- Generic / POIFS handling ---

    private TikaInputStream handleGenericOrPOIFS(Metadata metadata,
                                                  AtomicInteger unknownFilenameCount)
            throws IOException, TikaException {
        try (InputStream probe = new BufferedInputStream(Files.newInputStream(tempFile))) {
            boolean isOLE2 = FileMagic.valueOf(probe) == FileMagic.OLE2;
            if (!isOLE2) {
                return TikaInputStream.get(tempFile, metadata, tmp);
            }
        }

        // It's POIFS -- parse it
        try (InputStream poifsIn = new BufferedInputStream(Files.newInputStream(tempFile));
             POIFSFileSystem fs = new POIFSFileSystem(poifsIn)) {
            DirectoryNode root = fs.getRoot();
            if (root == null) {
                return null;
            }

            byte[] content = null;

            if (root.hasEntry("Package")) {
                Entry pkg = root.getEntry("Package");
                try (BoundedInputStream bis = new BoundedInputStream(
                        maxBytes > 0 ? maxBytes : Long.MAX_VALUE,
                        new DocumentInputStream((DocumentEntry) pkg))) {
                    content = IOUtils.toByteArray(bis);
                    if (bis.hasHitBound()) {
                        throw new TikaMemoryLimitException(maxBytes + 1, maxBytes);
                    }
                }
            } else {
                POIFSDocumentType type = POIFSDocumentType.detectType(root);
                if (type == POIFSDocumentType.OLE10_NATIVE) {
                    try {
                        Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(root);
                        content = ole.getDataBuffer();
                    } catch (Ole10NativeException ex) {
                        // Not valid OLE10Native
                    }
                } else if (type == POIFSDocumentType.COMP_OBJ) {
                    DocumentEntry contentsEntry;
                    try {
                        contentsEntry = (DocumentEntry) root.getEntry("CONTENTS");
                    } catch (FileNotFoundException e) {
                        contentsEntry = (DocumentEntry) root.getEntry("Contents");
                    }
                    try (DocumentInputStream inp = new DocumentInputStream(contentsEntry)) {
                        content = new byte[contentsEntry.getSize()];
                        inp.readFully(content);
                    }
                } else {
                    // Unknown POIFS type -- return the whole thing
                    metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
                    return TikaInputStream.get(tempFile, metadata, tmp);
                }
            }

            if (content != null) {
                Path contentFile = tmp.createTempFile(".bin");
                Files.write(contentFile, content);
                return TikaInputStream.get(contentFile, metadata, tmp);
            }
        }
        return null;
    }

    // --- Helper methods ---

    private void initUint32Field(Field next) {
        currentField = next;
        fieldPos = 0;
        fieldTarget = 4;
    }

    private static final int MAX_HEADER_STRING_LENGTH = 4096;

    private void initStringField(Field next, int len) {
        currentField = next;
        if (len > MAX_HEADER_STRING_LENGTH) {
            // Corrupt or crafted header — bail out
            currentField = Field.SKIP;
            return;
        }
        if (len <= 0) {
            switch (next) {
                case CLASS_NAME:
                    className = "";
                    initUint32Field(Field.TOPIC_LEN);
                    break;
                case TOPIC_NAME:
                    topicName = "";
                    initUint32Field(Field.ITEM_LEN);
                    break;
                case ITEM_NAME:
                    itemName = "";
                    initUint32Field(Field.DATA_SIZE);
                    break;
                default:
                    break;
            }
            return;
        }
        stringBuf = new byte[len];
        stringPos = 0;
        fieldTarget = len;
    }

    private static long readLE32(byte[] buf) {
        return (buf[0] & 0xFFL)
                | ((buf[1] & 0xFFL) << 8)
                | ((buf[2] & 0xFFL) << 16)
                | ((buf[3] & 0xFFL) << 24);
    }

    private static String decodeString(byte[] buf, int len) {
        try {
            return new String(buf, 0, len, WIN_ASCII).trim();
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(buf, 0, len, java.nio.charset.StandardCharsets.US_ASCII).trim();
        }
    }

    private static int readUShortLE(InputStream is) throws IOException {
        try {
            return EndianUtils.readUShortLE(is);
        } catch (EndianUtils.BufferUnderrunException e) {
            throw new IOException(e);
        }
    }

    private static int readUShortBE(InputStream is) throws IOException {
        try {
            return EndianUtils.readUShortBE(is);
        } catch (EndianUtils.BufferUnderrunException e) {
            throw new IOException(e);
        }
    }

    private static long readUIntLE(InputStream is) throws IOException {
        try {
            return EndianUtils.readUIntLE(is);
        } catch (EndianUtils.BufferUnderrunException e) {
            throw new IOException(e);
        }
    }

    private static String readNullTerminatedString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = is.read();
        while (c > 0) {
            sb.append((char) c);
            c = is.read();
        }
        if (c == -1) {
            throw new IOException("hit end of stream before null terminator");
        }
        return sb.toString();
    }

    private static long copyBounded(InputStream in, OutputStream out, long maxLen)
            throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        while (total < maxLen) {
            int toRead = (int) Math.min(buf.length, maxLen - total);
            int read = in.read(buf, 0, toRead);
            if (read == -1) {
                break;
            }
            out.write(buf, 0, read);
            total += read;
        }
        return total;
    }

    private enum Field {
        VERSION, FORMAT_ID,
        CLASS_LEN, CLASS_NAME,
        TOPIC_LEN, TOPIC_NAME,
        ITEM_LEN, ITEM_NAME,
        DATA_SIZE, DATA,
        DONE, SKIP
    }
}
