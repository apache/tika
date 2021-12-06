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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.chunking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.codec.digest.DigestUtils;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.LeafNodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.SignatureObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ZipHeader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

/**
 * This class is used to process zip file chunking
 */
public class ZipFilesChunking extends AbstractChunking {
    /**
     * Initializes a new instance of the ZipFilesChunking class
     *
     * @param fileContent The content of the file.
     */
    public ZipFilesChunking(byte[] fileContent) {
        super(fileContent);
    }

    /**
     * This method is used to chunk the file data.
     *
     * @return A list of LeafNodeObjectData.
     */
    @Override
    public List<LeafNodeObject> chunking() {
        java.util.List<LeafNodeObject> list = new ArrayList<>();
        LeafNodeObject.IntermediateNodeObjectBuilder builder =
                new LeafNodeObject.IntermediateNodeObjectBuilder();

        int index = 0;
        while (ZipHeader.isFileHeader(this.FileContent, index)) {
            AtomicReference<byte[]> dataFileSignatureBytes = new AtomicReference<>();
            byte[] header = this.analyzeFileHeader(this.FileContent, index, dataFileSignatureBytes);
            int headerLength = header.length;
            int compressedSize = (int) this.getCompressedSize(dataFileSignatureBytes.get());

            if (headerLength + compressedSize <= 4096) {
                list.add(builder.Build(
                        Arrays.copyOfRange(this.FileContent, index, headerLength + compressedSize),
                        this.getSingleChunkSignature(header, dataFileSignatureBytes.get())));
                index += headerLength += compressedSize;
            } else {
                list.add(builder.Build(header, this.getSHA1Signature(header)));
                index += headerLength;

                byte[] dataFile = Arrays.copyOfRange(this.FileContent, index, compressedSize);

                if (dataFile.length <= 1048576) {
                    list.add(builder.Build(dataFile,
                            this.getDataFileSignature(dataFileSignatureBytes.get())));
                } else {
                    list.addAll(this.getSubChunkList(dataFile));
                }

                index += compressedSize;
            }
        }

        if (0 == index) {
            return null;
        }

        byte[] finalRes =
                Arrays.copyOfRange(this.FileContent, index, this.FileContent.length - index);

        if (finalRes.length <= 1048576) {
            list.add(builder.Build(finalRes, this.getSHA1Signature(finalRes)));
        } else {
            // In current, it has no idea about how to compute the signature for final part larger than 1MB.
            throw new RuntimeException(
                    "If the final chunk is larger than 1MB, the signature method is not implemented.");
        }

        return list;
    }

    /**
     * Convert chunk data to LeafNodeObjectData from byte array.
     *
     * @param chunkData A byte array that contains the data.
     * @return A list of LeafNodeObjectData.
     */
    private List<LeafNodeObject> getSubChunkList(byte[] chunkData) {
        List<LeafNodeObject> subChunkList = new ArrayList<LeafNodeObject>();
        int index = 0;
        while (index < chunkData.length) {
            int length = chunkData.length - index < 1048576 ? chunkData.length - index : 1048576;
            byte[] temp = Arrays.copyOfRange(chunkData, index, length);
            subChunkList.add(new LeafNodeObject.IntermediateNodeObjectBuilder().Build(temp,
                    this.getSubChunkSignature()));
            index += length;
        }

        return subChunkList;
    }

    /**
     * This method is used to analyze the zip file header.
     *
     * @param content           Specify the zip content.
     * @param index             Specify the start position.
     * @param dataFileSignature Specify the output value for the data file signature.
     * @return Return the data file content.
     */
    private byte[] analyzeFileHeader(byte[] content, int index,
                                     AtomicReference<byte[]> dataFileSignature) {
        int crc32 = BitConverter.toInt32(content, index + 14);
        int compressedSize = BitConverter.toInt32(content, index + 18);
        int uncompressedSize = BitConverter.toInt32(content, index + 22);
        int fileNameLength = BitConverter.toInt16(content, index + 26);
        int extraFileldLength = BitConverter.toInt16(content, index + 28);
        int headerLength = 30 + fileNameLength + extraFileldLength;

        BitWriter writer = new BitWriter(20);
        writer.appendInit32(crc32, 32);
        writer.appendUInt64(compressedSize, 64);
        writer.appendUInt64(uncompressedSize, 64);
        dataFileSignature.set(writer.getBytes());

        return Arrays.copyOfRange(content, index, headerLength);
    }

    /**
     * This method is used to get the compressed size value from the data file signature.
     *
     * @param dataFileSignature Specify the signature of the zip file content.
     * @return Return the compressed size value.
     */
    private long getCompressedSize(byte[] dataFileSignature) {
        BitReader reader = new BitReader(dataFileSignature, 0);
        reader.readUInt32(32);
        return reader.readUInt64(64);
    }

    /**
     * Get the signature for single chunk.
     *
     * @param header   The data of file header.
     * @param dataFile The data of data file.
     * @return An instance of SignatureObject.
     */
    private SignatureObject getSingleChunkSignature(byte[] header, byte[] dataFile) {
        byte[] headerSignature = DigestUtils.sha1(header);

        List<Byte> singleSignature = new ArrayList<>();
        ByteUtil.appendByteArrayToListOfByte(singleSignature, headerSignature);
        ByteUtil.appendByteArrayToListOfByte(singleSignature, dataFile);

        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(singleSignature);
        return signature;
    }

    /**
     * Get signature with SHA1 algorithm.
     *
     * @param array The input data.
     * @return An instance of SignatureObject.
     */
    private SignatureObject getSHA1Signature(byte[] array) {
        byte[] temp = DigestUtils.sha1(array);

        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(temp));
        return signature;
    }

    /**
     * Get the signature for data file.
     *
     * @param array The input data.
     * @return An instance of SignatureObject.
     */
    private SignatureObject getDataFileSignature(byte[] array) {
        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(array));

        return signature;
    }

    /**
     * Get the signature for sub chunk.
     *
     * @return An instance of SignatureObject.
     */
    private SignatureObject getSubChunkSignature() {
        // In current, it has no idea about how to compute the signature for sub chunk.
        throw new RuntimeException("The Get sub chunk signature method is not implemented.");
    }
}
