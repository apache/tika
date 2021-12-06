package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.Arrays;
import java.util.List;

/// <summary>
    /// This class is used to represent the property contains 8 bytes of data in the PropertySet.rgData stream field.
    /// </summary>
    public class EightBytesOfData implements IProperty
    {
        /// <summary>
        /// Gets or sets the data of property.
        /// </summary>
        public byte[] Data;

        /// <summary>
        /// This method is used to deserialize the EightBytesOfData from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the EightBytesOfData.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            this.Data = Arrays.copyOfRange(byteArray, startIndex, startIndex + 8);
            return 8;
        }
        /// <summary>
        /// This method is used to convert the element of EightBytesOfData into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of EightBytesOfData.</returns>
        public List<Byte> SerializeToByteList()
        {
            return ByteUtil.toListOfByte(this.Data);
        }
    }