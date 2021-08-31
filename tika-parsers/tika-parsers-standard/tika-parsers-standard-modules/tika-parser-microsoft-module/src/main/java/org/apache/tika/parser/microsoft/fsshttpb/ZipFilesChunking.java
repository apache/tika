package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.codec.digest.DigestUtils;

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
    public List<LeafNodeObject> Chunking() {
        java.util.List<LeafNodeObject> list = new ArrayList<>();
        LeafNodeObject.IntermediateNodeObjectBuilder builder = new LeafNodeObject.IntermediateNodeObjectBuilder();

        int index = 0;
        while (ZipHeader.IsFileHeader(this.FileContent, index)) {
            AtomicReference<byte[]> dataFileSignatureBytes = new AtomicReference<>();
            byte[] header = this.AnalyzeFileHeader(this.FileContent, index, dataFileSignatureBytes);
            int headerLength = header.length;
            int compressedSize = (int) this.GetCompressedSize(dataFileSignatureBytes.get());

            if (headerLength + compressedSize <= 4096) {
                list.add(builder.Build(Arrays.copyOfRange(this.FileContent, index, headerLength + compressedSize),
                        this.GetSingleChunkSignature(header, dataFileSignatureBytes.get())));
                index += headerLength += compressedSize;
            } else {
                list.add(builder.Build(header, this.GetSHA1Signature(header)));
                index += headerLength;

                byte[] dataFile = Arrays.copyOfRange(this.FileContent, index, compressedSize);

                if (dataFile.length <= 1048576) {
                    list.add(builder.Build(dataFile, this.GetDataFileSignature(dataFileSignatureBytes.get())));
                } else {
                    list.addAll(this.GetSubChunkList(dataFile));
                }

                index += compressedSize;
            }
        }

        if (0 == index) {
            return null;
        }

        byte[] finalRes = Arrays.copyOfRange(this.FileContent, index, this.FileContent.length - index);

        if (finalRes.length <= 1048576) {
            list.add(builder.Build(finalRes, this.GetSHA1Signature(finalRes)));
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
    private List<LeafNodeObject> GetSubChunkList(byte[] chunkData) {
        List<LeafNodeObject> subChunkList = new ArrayList<LeafNodeObject>();
        int index = 0;
        while (index < chunkData.length) {
            int length = chunkData.length - index < 1048576 ? chunkData.length - index : 1048576;
            byte[] temp = Arrays.copyOfRange(chunkData, index, length);
            subChunkList.add(
                    new LeafNodeObject.IntermediateNodeObjectBuilder().Build(temp, this.GetSubChunkSignature()));
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
    private byte[] AnalyzeFileHeader(byte[] content, int index, AtomicReference<byte[]> dataFileSignature) {
        int crc32 = BitConverter.toInt32(content, index + 14);
        int compressedSize = BitConverter.toInt32(content, index + 18);
        int uncompressedSize = BitConverter.toInt32(content, index + 22);
        int fileNameLength = BitConverter.toInt16(content, index + 26);
        int extraFileldLength = BitConverter.toInt16(content, index + 28);
        int headerLength = 30 + fileNameLength + extraFileldLength;

        BitWriter writer = new BitWriter(20);
        writer.AppendInit32(crc32, 32);
        writer.AppendUInt64(compressedSize, 64);
        writer.AppendUInt64(uncompressedSize, 64);
        dataFileSignature.set(writer.getBytes());

        return Arrays.copyOfRange(content, index, headerLength);
    }

    /**
     * This method is used to get the compressed size value from the data file signature.
     *
     * @param dataFileSignature Specify the signature of the zip file content.
     * @return Return the compressed size value.
     */
    private long GetCompressedSize(byte[] dataFileSignature) {
        BitReader reader = new BitReader(dataFileSignature, 0);
        reader.ReadUInt32(32);
        return reader.ReadUInt64(64);
    }

    /**
     * Get the signature for single chunk.
     *
     * @param header   The data of file header.
     * @param dataFile The data of data file.
     * @return An instance of SignatureObject.
     */
    private SignatureObject GetSingleChunkSignature(byte[] header, byte[] dataFile) {
        byte[] headerSignature = DigestUtils.sha1(header);

//            if (SharedContext.Current.CellStorageVersionType.MinorVersion >= 2)
//            {
//                singleSignature = new byte[dataFile.Length];
//
//                for (int i = 0; i < headerSignature.Length; i++)
//                {
//                    singleSignature[i] = (byte)(headerSignature[i] ^ dataFile[i]);
//                }
//            }
//            else
//            {
        List<Byte> singleSignature = new ArrayList<>();
        ByteUtil.appendByteArrayToListOfByte(singleSignature, headerSignature);
        ByteUtil.appendByteArrayToListOfByte(singleSignature, dataFile);

        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(singleSignature);
//              }
        return signature;
    }

    /**
     * Get signature with SHA1 algorithm.
     *
     * @param array The input data.
     * @return An instance of SignatureObject.
     */
    private SignatureObject GetSHA1Signature(byte[] array) {
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
    private SignatureObject GetDataFileSignature(byte[] array) {
        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(array));

        return signature;
    }

    /**
     * Get the signature for sub chunk.
     *
     * @return An instance of SignatureObject.
     */
    private SignatureObject GetSubChunkSignature() {
        // In current, it has no idea about how to compute the signature for sub chunk.
        throw new RuntimeException("The Get sub chunk signature method is not implemented.");
    }
}