package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

public class ZipFilesChunking extends AbstractChunking
    {
        /// <summary>
        /// Initializes a new instance of the ZipFilesChunking class
        /// </summary>
        /// <param name="fileContent">The content of the file.</param>
        public ZipFilesChunking(byte[] fileContent)
        {
            super(fileContent);
        }

        /// <summary>
        /// This method is used to chunk the file data.
        /// </summary>
        /// <returns>A list of LeafNodeObjectData.</returns>
        @Override public List<LeafNodeObject> Chunking()
        {
            java.util.List<LeafNodeObject> list = new ArrayList<LeafNodeObject>();
            LeafNodeObject.IntermediateNodeObjectBuilder builder = new LeafNodeObject.IntermediateNodeObjectBuilder();

            int index = 0;
            while (ZipHeader.IsFileHeader(this.FileContent, index))
            {
                byte[] dataFileSignatureBytes;
                byte[] header = this.AnalyzeFileHeader(this.FileContent, index, out dataFileSignatureBytes);
                int headerLength = header.Length;
                int compressedSize = (int)this.GetCompressedSize(dataFileSignatureBytes);

                if (headerLength + compressedSize <= 4096)
                {
                    list.Add(builder.Build(AdapterHelper.GetBytes(this.FileContent, index, headerLength + compressedSize), this.GetSingleChunkSignature(header, dataFileSignatureBytes)));
                    index += headerLength += compressedSize;
                }
                else
                {
                    list.Add(builder.Build(header, this.GetSHA1Signature(header)));
                    index += headerLength;

                    byte[] dataFile = AdapterHelper.GetBytes(this.FileContent, index, compressedSize);

                    if (dataFile.Length <= 1048576)
                    {
                        list.Add(builder.Build(dataFile, this.GetDataFileSignature(dataFileSignatureBytes)));
                    }
                    else
                    {
                        list.AddRange(this.GetSubChunkList(dataFile));
                    }

                    index += compressedSize;
                }
            }

            if (0 == index)
            {
                return null;
            }

            byte[] final = AdapterHelper.GetBytes(this.FileContent, index, this.FileContent.Length - index);

            if (final.Length <= 1048576)
            {
                list.Add(builder.Build(final, this.GetSHA1Signature(final)));
            }
            else
            {
                // In current, it has no idea about how to compute the signature for final part larger than 1MB.
                throw new NotImplementedException("If the final chunk is larger than 1MB, the signature method is not implemented.");
            }

            return list;
        }

        /// <summary>
        /// Convert chunk data to LeafNodeObjectData from byte array.
        /// </summary>
        /// <param name="chunkData">A byte array that contains the data.</param>
        /// <returns>A list of LeafNodeObjectData.</returns>
        private List<LeafNodeObject> GetSubChunkList(byte[] chunkData)
        {
            List<LeafNodeObject> subChunkList = new ArrayList<LeafNodeObject>();
            int index = 0;
            while (index < chunkData.Length)
            {
                int length = chunkData.Length - index < 1048576 ? chunkData.Length - index : 1048576;
                byte[] temp = AdapterHelper.GetBytes(chunkData, index, length);
                subChunkList.Add(new LeafNodeObject.IntermediateNodeObjectBuilder().Build(temp, this.GetSubChunkSignature()));
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
        private byte[] AnalyzeFileHeader(byte[] content, int index, out byte[] dataFileSignature)
        {
            int crc32 = BitConverter.ToInt32(content, index + 14);
            int compressedSize = BitConverter.ToInt32(content, index + 18);
            int uncompressedSize = BitConverter.ToInt32(content, index + 22);
            int fileNameLength = BitConverter.ToInt16(content, index + 26);
            int extraFileldLength = BitConverter.ToInt16(content, index + 28);
            int headerLength = 30 + fileNameLength + extraFileldLength;

            BitWriter writer = new BitWriter(20);
            writer.AppendInit32(crc32, 32);
            writer.AppendUInt64((ulong)compressedSize, 64);
            writer.AppendUInt64((ulong)uncompressedSize, 64);
            dataFileSignature = writer.Bytes;

            return AdapterHelper.GetBytes(content, index, headerLength);
        }

        /// <summary>
        /// This method is used to get the compressed size value from the data file signature.
        /// </summary>
        /// <param name="dataFileSignature">Specify the signature of the zip file content.</param>
        /// <returns>Return the compressed size value.</returns>
        private ulong GetCompressedSize(byte[] dataFileSignature)
        {
            using (BitReader reader = new BitReader(dataFileSignature, 0))
            {
                reader.ReadUInt32(32);
                return reader.ReadUInt64(64);
            }
        }

        /// <summary>
        /// Get the signature for single chunk.
        /// </summary>
        /// <param name="header">The data of file header.</param>
        /// <param name="dataFile">The data of data file.</param>
        /// <returns>An instance of SignatureObject.</returns>
        private SignatureObject GetSingleChunkSignature(byte[] header, byte[] dataFile)
        {
            SHA1 sha = new SHA1CryptoServiceProvider();
            byte[] headerSignature = sha.ComputeHash(header);
            sha.Dispose();
            byte[] singleSignature = null;

            if (SharedContext.Current.CellStorageVersionType.MinorVersion >= 2)
            {
                singleSignature = new byte[dataFile.Length];

                for (int i = 0; i < headerSignature.Length; i++)
                {
                    singleSignature[i] = (byte)(headerSignature[i] ^ dataFile[i]);
                }
            }
            else
            {
                List<Byte> tmp = new ArrayList<Byte>();
                tmp.AddRange(headerSignature);
                tmp.AddRange(dataFile);

                singleSignature = tmp.ToArray(); 
            }

            SignatureObject signature = new SignatureObject();
            signature.SignatureData = new BinaryItem(singleSignature);

            return signature;
        }

        /// <summary>
        /// Get signature with SHA1 algorithm.
        /// </summary>
        /// <param name="array">The input data.</param>
        /// <returns>An instance of SignatureObject.</returns>
        private SignatureObject GetSHA1Signature(byte[] array)
        {
            SHA1 sha = new SHA1CryptoServiceProvider();
            byte[] temp = sha.ComputeHash(array);
            sha.Dispose();

            SignatureObject signature = new SignatureObject();
            signature.SignatureData = new BinaryItem(temp);
            return signature;
        }

        /// <summary>
        /// Get the signature for data file.
        /// </summary>
        /// <param name="array">The input data.</param>
        /// <returns>An instance of SignatureObject.</returns>
        private SignatureObject GetDataFileSignature(byte[] array)
        {
            SignatureObject signature = new SignatureObject();
            signature.SignatureData = new BinaryItem(array);

            return signature;
        }

        /// <summary>
        /// Get the signature for sub chunk.
        /// </summary>
        /// <returns>An instance of SignatureObject.</returns>
        private SignatureObject GetSubChunkSignature()
        {
            // In current, it has no idea about how to compute the signature for sub chunk.
            throw new RuntimeException("The Get sub chunk signature method is not implemented.");
        }
    }