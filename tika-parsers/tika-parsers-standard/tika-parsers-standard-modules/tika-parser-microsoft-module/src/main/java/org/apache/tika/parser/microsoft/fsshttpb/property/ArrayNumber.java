package org.apache.tika.parser.microsoft.fsshttpb.property;

import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.util.BitConverter;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/// <summary>
    /// The class is used to represent the number of the array.
    /// </summary>
    public class ArrayNumber implements IProperty
    {
        /// <summary>
        /// Gets or sets the number of array.
        /// </summary>
        public int Number;
        /// <summary>
        /// This method is used to deserialize the number of array from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the number of array.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            this.Number = BitConverter.toInt32(byteArray, startIndex);
            return 4;
        }
        /// <summary>
        /// This method is used to convert the element of the number of array into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of the number of array.</returns>
        public List<Byte> SerializeToByteList()
        {
            return ByteUtil.toListOfByte(BitConverter.getBytes(this.Number));
        }
    }