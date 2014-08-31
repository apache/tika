/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.tika.parser.rtf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.util.IOUtils;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;

/**
 * Many thanks to Simon Mourier for:
 * http://stackoverflow.com/questions/14779647/extract-embedded-image-object-in-rtf 
 * and for granting permission to use his code in Tika.
 * 
 */
class RTFObjDataParser {

    private final static int[] INT_LE_POWS = new int[]{
        1, 256, 65536, 16777216
    };

    private final static String WIN_ASCII = "WINDOWS-1252";

    /**
     * Parses the embedded object/pict string
     * 
     * @param bytes actual bytes (already converted from the 
     *  hex pair string stored in the embedded object data into actual bytes or read
     *  as raw binary bytes)
     * @return a SimpleRTFEmbObj or null
     * @throws IOException if there are any surprise surprises during parsing
     */
    
    /**
     * 
     * @param bytes
     * @param metadata incoming metadata
     * @param unknownFilenameCount 
     * @return byte[] for contents of obj data
     * @throws IOException
     */
    protected byte[] parse(byte[] bytes, Metadata metadata, AtomicInteger unknownFilenameCount) 
            throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        long version = readUInt(is);
        metadata.add(RTFMetadata.EMB_APP_VERSION, Long.toString(version));

        long formatId = readUInt(is);
        //2 is an embedded object. 1 is a link.
        if (formatId != 2L) {
            return null;
        }
        String className = readLengthPrefixedAnsiString(is).trim(); 
        String topicName = readLengthPrefixedAnsiString(is).trim();
        String itemName = readLengthPrefixedAnsiString(is).trim(); 

        if (className != null && className.length() > 0) {
            metadata.add(RTFMetadata.EMB_CLASS, className);
        }
        if (topicName != null && topicName.length() > 0) {
            metadata.add(RTFMetadata.EMB_TOPIC, topicName);
        }
        if (itemName != null && itemName.length() > 0) {
            metadata.add(RTFMetadata.EMB_ITEM, itemName);
        }

        long dataSz = readUInt(is);

        //readBytes tests for reading too many bytes
        byte[] embObjBytes = readBytes(is, dataSz);

        if (className.toLowerCase(Locale.ROOT).equals("package")){
            return handlePackage(embObjBytes, metadata);
        } else if (className.toLowerCase(Locale.ROOT).equals("pbrush")) {
            //simple bitmap bytes
            return embObjBytes;
        } else {
            ByteArrayInputStream embIs = new ByteArrayInputStream(embObjBytes);
            if (NPOIFSFileSystem.hasPOIFSHeader(embIs)){
                try{
                    return handleEmbeddedPOIFS(embIs, metadata, unknownFilenameCount);
                } catch (IOException e) {
                    //swallow
                }   
            }
        }
        return embObjBytes;
    }


    //will throw IOException if not actually POIFS
    //can return null byte[]
    private byte[] handleEmbeddedPOIFS(InputStream is, Metadata metadata, 
            AtomicInteger unknownFilenameCount) 
            throws IOException {

        NPOIFSFileSystem fs = null;
        byte[] ret = null;
        try {

            fs = new NPOIFSFileSystem(is);

            DirectoryNode root = fs.getRoot();

            if (root == null) {
                return ret;
            }

            if (root.hasEntry("Package")){
                Entry ooxml = root.getEntry("Package");
                TikaInputStream stream = TikaInputStream.get(new DocumentInputStream((DocumentEntry) ooxml));

                ByteArrayOutputStream out = new ByteArrayOutputStream();

                IOUtils.copy(stream, out);
                ret = out.toByteArray();
            } else {
                //try poifs
                POIFSDocumentType type = POIFSDocumentType.detectType(root);
                if (type == POIFSDocumentType.OLE10_NATIVE) {
                    try {
                        // Try to un-wrap the OLE10Native record:
                        Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(root);
                        ret = ole.getDataBuffer();
                    } catch (Ole10NativeException ex) {
                        // Not a valid OLE10Native record, skip it
                    }
                } else if (type == POIFSDocumentType.COMP_OBJ) {

                    DocumentEntry contentsEntry;
                    try {
                        contentsEntry = (DocumentEntry)root.getEntry("CONTENTS");
                    } catch (FileNotFoundException ioe) {
                        contentsEntry = (DocumentEntry)root.getEntry("Contents");
                    }

                    DocumentInputStream inp = null;
                    try {
                        inp = new DocumentInputStream(contentsEntry);
                        ret = new byte[contentsEntry.getSize()];
                        inp.readFully(ret);
                    } finally {
                        if (inp != null) {
                            inp.close();
                        }
                    }
                } else {

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    is.reset();
                    IOUtils.copy(is, out);
                    ret = out.toByteArray();
                    metadata.set(Metadata.RESOURCE_NAME_KEY, "file_"+unknownFilenameCount.getAndIncrement() + "."+type.getExtension());
                    metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
                }
            }
        } finally {
            if (fs != null) {
                fs.close();
            }
        }
        return ret;
    }



    /**
     * can return null if there is a linked object 
     * instead of an embedded file
     */
    private byte[] handlePackage(byte[] pkgBytes, Metadata metadata) throws IOException { 
        //now parse the package header
        ByteArrayInputStream is = new ByteArrayInputStream(pkgBytes);
        readUShort(is);

        String displayName = readAnsiString(is);

        //should we add this to the metadata?
        readAnsiString(is); //iconFilePath
        readUShort(is); //iconIndex
        int type = readUShort(is); //type

        //1 is link, 3 is embedded object
        //this only handles embedded objects
        if (type != 3) {
            return null;
        }
        //should we really be ignoring this filePathLen?
        readUInt(is); //filePathLen

        String ansiFilePath = readAnsiString(is); //filePath
        long bytesLen = readUInt(is);
        byte[] objBytes = initByteArray(bytesLen);
        is.read(objBytes);
        StringBuilder unicodeFilePath = new StringBuilder();

        try {
            long unicodeLen = readUInt(is);

            for (int i = 0; i < unicodeLen; i++){
                int lo = is.read();
                int hi = is.read();
                int sum = lo+256*hi;
                if (hi == -1 || lo == -1){
                    //stream ran out; empty SB and stop
                    unicodeFilePath.setLength(0);
                    break;
                }
                unicodeFilePath.append((char)sum);
            }
        } catch (IOException e) {
            //swallow; the unicode file path is optional and might not happen
            unicodeFilePath.setLength(0);
        }
        String fileNameToUse = "";
        String pathToUse = "";
        if (unicodeFilePath.length() > 0){
            String p = unicodeFilePath.toString();
            fileNameToUse = p;
            pathToUse = p;
        } else {
            fileNameToUse = displayName == null ? "" : displayName;
            pathToUse = ansiFilePath == null ? "" : ansiFilePath;
        }
        metadata.set(Metadata.RESOURCE_NAME_KEY, FilenameUtils.getName(fileNameToUse));
        metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, pathToUse);

        return objBytes;
    }


    private int readUShort(InputStream is) throws IOException {
        int lo = is.read();
        int hi = is.read()*256;
        if (lo == -1 || hi == -1) {
            throw new IOException("Hit end of stream before reading little endian unsigned short.");
        }
        return hi+lo;
    }

    private long readUInt(InputStream is) throws IOException {
        long sum = 0;
        for (int i = 0; i < 4; i++){
            int v = is.read();
            if (v == -1) {
                throw new IOException("Hit end of stream before finishing little endian unsigned int.");
            }
            sum += v*(long)INT_LE_POWS[i];
        }
        return sum;
    }

    private String readAnsiString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = is.read();
        while (c > 0) {
            sb.append((char)c);
            c = is.read();
        }
        if (c == -1) {
            throw new IOException("Hit end of stream before end of AnsiString");
        }
        return sb.toString();
    }

    private String readLengthPrefixedAnsiString(InputStream is) throws IOException {
        long len = readUInt(is);
        byte[] bytes = readBytes(is, len);
        try {
            return new String(bytes, WIN_ASCII);
        } catch (UnsupportedEncodingException e) {
            //shouldn't ever happen
            throw new IOException("Unsupported encoding");
        }
    }


    private byte[] readBytes(InputStream is, long len) throws IOException {
        //initByteArray tests for "reading of too many bytes"
        byte[] bytes = initByteArray(len);
        int read = is.read(bytes);
        if (read != len) {
            throw new IOException("Hit end of stream before reading all bytes");
        }

        return bytes;
    }
    
    private byte[] initByteArray(long len) throws IOException {
        if (len < 0 || len > RTFParser.getMaxBytesForEmbeddedObject()) {
            throw new IOException("Requested length for reading bytes is out of bounds: " + len);
        }
        return new byte[(int)len];
        
    }
}

