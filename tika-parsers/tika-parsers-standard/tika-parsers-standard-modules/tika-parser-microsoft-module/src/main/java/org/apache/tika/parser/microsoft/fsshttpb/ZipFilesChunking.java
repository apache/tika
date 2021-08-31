package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.codec.digest.DigestUtils;

/// <summary>
/// This class is used to process zip file chunking.
/// </summary>
public class ZipFilesChunking extends AbstractChunking {
    /// <summary>
    /// Initializes a new instance of the ZipFilesChunking class
    /// </summary>
    /// <param name="fileContent">The content of the file.</param>
    public ZipFilesChunking(byte[] fileContent) {
        super(fileContent);
    }

    /// <summary>
    /// This method is used to chunk the file data.
    /// </summary>
    /// <returns>A list of LeafNodeObjectData.</returns>
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

    /// <summary>
    /// Convert chunk data to LeafNodeObjectData from byte array.
    /// </summary>
    /// <param name="chunkData">A byte array that contains the data.</param>
    /// <returns>A list of LeafNodeObjectData.</returns>
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

    /// <summary>
    /// This method is used to analyze the zip file header.
    /// </summary>
    /// <param name="content">Specify the zip content.</param>
    /// <param name="index">Specify the start position.</param>
    /// <param name="dataFileSignature">Specify the output value for the data file signature.</param>
    /// <returns>Return the data file content.</returns>
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

    /// <summary>
    /// This method is used to get the compressed size value from the data file signature.
    /// </summary>
    /// <param name="dataFileSignature">Specify the signature of the zip file content.</param>
    /// <returns>Return the compressed size value.</returns>
    private long GetCompressedSize(byte[] dataFileSignature) {
        BitReader reader = new BitReader(dataFileSignature, 0);
        reader.ReadUInt32(32);
        return reader.ReadUInt64(64);
    }

    /// <summary>
    /// Get the signature for single chunk.
    /// </summary>
    /// <param name="header">The data of file header.</param>
    /// <param name="dataFile">The data of data file.</param>
    /// <returns>An instance of SignatureObject.</returns>
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

        return signature;
    }

    /// <summary>
    /// Get signature with SHA1 algorithm.
    /// </summary>
    /// <param name="array">The input data.</param>
    /// <returns>An instance of SignatureObject.</returns>
    private SignatureObject GetSHA1Signature(byte[] array) {
        byte[] temp = DigestUtils.sha1(array);

        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(temp));
        return signature;
    }

    /// <summary>
    /// Get the signature for data file.
    /// </summary>
    /// <param name="array">The input data.</param>
    /// <returns>An instance of SignatureObject.</returns>
    private SignatureObject GetDataFileSignature(byte[] array) {
        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(array));

        return signature;
    }

    /// <summary>
    /// Get the signature for sub chunk.
    /// </summary>
    /// <returns>An instance of SignatureObject.</returns>
    private SignatureObject GetSubChunkSignature() {
        // In current, it has no idea about how to compute the signature for sub chunk.
        throw new RuntimeException("The Get sub chunk signature method is not implemented.");
    }
}