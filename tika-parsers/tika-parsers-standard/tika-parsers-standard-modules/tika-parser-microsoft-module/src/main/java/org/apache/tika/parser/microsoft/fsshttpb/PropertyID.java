package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
    /// This class is used to represent a PropertyID.
    /// </summary>
    public class PropertyID
    {
        /// <summary>
        /// Gets or sets the value of id field.
        /// </summary>
        public int Id;
        /// <summary>
        /// Gets or sets the value of type field.
        /// </summary>
        public int Type;
        /// <summary>
        /// Gets or sets the value of boolValue field.
        /// </summary>
        public int BoolValue;

        /// <summary>
        /// Gets or sets the value of PropertyID.
        /// </summary>
        public int Value;

        /// <summary>
        /// This method is used to convert the element of PropertyID object into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of PropertyID</returns>
        public List<Byte> SerializeToByteList()
        {
            BitWriter bitWriter = new BitWriter(4);
            bitWriter.AppendUInit32(this.Id, 26);
            bitWriter.AppendUInit32(this.Type, 5);
            bitWriter.AppendInit32(this.BoolValue, 1);
           
            return bitWriter.getByteList();
        }

        /// <summary>
        /// This method is used to deserialize the PropertyID object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the PropertyID object.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            BitReader bitReader = new BitReader(byteArray, startIndex);
            this.Id = bitReader.ReadUInt32(26);
            this.Type = bitReader.ReadUInt32(5);
            this.BoolValue = bitReader.ReadInt32(1);
            this.Value = BitConverter.toInt32(byteArray, startIndex);
            return 4;
        }
    }