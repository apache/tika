package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
    /// This class is used to represent the CompactID structrue.
    /// </summary>
    public class CompactID
    {
        /// <summary>
        /// Gets or sets an unsigned integer that specifies the value of the ExtendedGUID.n field.
        /// </summary>
        public long N;
        /// <summary>
        /// Gets or sets an unsigned integer that specifies the index in the global identification table. 
        /// </summary>
        public long GuidIndex;
        /// <summary>
        /// This method is used to convert the element of CompactID object into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of CompactID</returns>
        public List<Byte> SerializeToByteList()
        {
            BitWriter bitWriter = new BitWriter(4);
            bitWriter.AppendUInit32((int)this.N, 8);
            bitWriter.AppendUInit32((int)this.GuidIndex, 24);

            return bitWriter.getByteList();
        }
        /// <summary>
        /// This method is used to deserialize the CompactID object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the CompactID object.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            BitReader bitReader = new BitReader(byteArray, startIndex);
            this.N = bitReader.ReadUInt32(8);
            this.GuidIndex = bitReader.ReadUInt32(24);
            return 4;
        }
    }